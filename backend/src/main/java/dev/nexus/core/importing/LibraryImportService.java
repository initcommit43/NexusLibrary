package dev.nexus.core.importing;

import dev.nexus.core.activity.ActivityRecorder;
import dev.nexus.core.adapter.CanonicalRef;
import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.FetchProgress;
import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.adapter.LibraryImportAdapter;
import dev.nexus.core.adapter.ItemResolver;
import dev.nexus.core.cache.ItemCacheService;
import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.ExternalIds;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.jobs.SyncJob;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.domain.UserEntryRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a user's external library into tracked entries, for any provider.
 *
 * <p>Everything provider-specific is delegated: the adapter fetches, the resolver maps ids
 * onto a canonical catalogue. What stays here is the part worth writing once — resolve,
 * cache on miss, upsert, and report what did not match. Adding MAL or Simkl later means
 * contributing two beans and changing nothing in this class.
 */
@Service
public class LibraryImportService {

    private static final Logger log = LoggerFactory.getLogger(LibraryImportService.class);

    private final List<LibraryImportAdapter> adapters;
    private final List<ItemResolver> resolvers;
    private final ItemCacheService itemCache;
    private final UserEntryRepository entries;
    private final TrackableItemRepository items;
    private final ActivityRecorder activity;

    public LibraryImportService(
            List<LibraryImportAdapter> adapters,
            List<ItemResolver> resolvers,
            ItemCacheService itemCache,
            UserEntryRepository entries,
            TrackableItemRepository items,
            ActivityRecorder activity) {
        // Kept as lists and matched on demand: there will only ever be a handful of
        // providers, and indexing them up front would call into every adapter at startup.
        this.adapters = List.copyOf(adapters);
        this.resolvers = List.copyOf(resolvers);
        this.itemCache = itemCache;
        this.entries = entries;
        this.items = items;
        this.activity = activity;
    }

    @Transactional
    public ImportReport importLibrary(ExternalAccount account) {
        return importLibrary(account, null);
    }

    /**
     * @param job optional, reporting progress as the library is walked. It moves through
     *     three phases rather than one, because writing the entries — the only part a
     *     single counter used to cover — is the shortest of them by a wide margin.
     */
    @Transactional
    public ImportReport importLibrary(ExternalAccount account, SyncJob job) {
        Provider provider = account.getProvider();
        LibraryImportAdapter adapter =
                require(adapters, LibraryImportAdapter::provider, provider, "import adapter");

        // Nothing to count yet: how long a list is is the first thing the pull tells us.
        if (job != null) {
            job.beginPhase(SyncJob.Phase.FETCHING, 0);
        }

        List<ImportedEntry> library = adapter.pullLibrary(account);
        log.debug("Pulled {} items from {}", library.size(), provider);

        ImportReport report = importEntries(account.getUserId(), provider, library, job);
        account.markSynced();
        return report;
    }

    /**
     * The same import, for a library that arrived some other way than by asking the provider
     * for it — an exported CSV, uploaded because the API is paywalled or not worth
     * connecting for one run.
     *
     * <p>Everything downstream of the pull is shared, which is the point: a CSV row carries
     * the same hints the live adapter sets, so it goes through that provider's own resolver
     * and lands in the same unmatched report. Nothing here knows which route the rows took.
     *
     * <p>No account is touched, so nothing is marked as synced: an upload is not a link, and
     * claiming a connection was refreshed by it would be a lie on the settings card.
     */
    @Transactional
    public ImportReport importEntries(Long userId, Provider provider, List<ImportedEntry> library, SyncJob job) {
        ItemResolver resolver = require(resolvers, ItemResolver::provider, provider, "item resolver");

        Map<ExternalItemRef, CanonicalRef> resolved =
                resolver.resolveAll(library.stream().map(ImportedEntry::itemRef).toList());

        // One batch per catalogue: already-cached titles cost nothing, and a title another
        // user cached earlier costs nothing either.
        Map<Source, Map<String, TrackableItem>> cached = cacheResolvedItems(resolved.values(), job);
        recordProviderIds(provider, resolved, cached);

        List<ImportReport.UnmatchedItem> unmatched = new ArrayList<>();
        List<ActivityRecorder.Change> advanced = new ArrayList<>();
        int created = 0;
        int updated = 0;

        if (job != null) {
            job.beginPhase(SyncJob.Phase.IMPORTING, library.size());
        }

        for (ImportedEntry entry : library) {
            if (job != null && job.isCancelled()) {
                log.debug("Import cancelled after {} of {}", created + updated, library.size());
                break;
            }

            CanonicalRef ref = resolved.get(entry.itemRef());
            if (ref == null) {
                unmatched.add(unmatchedItem(entry, "No match in the catalogue"));
                if (job != null) {
                    job.advance(false);
                }
                continue;
            }

            TrackableItem item = cached.getOrDefault(ref.source(), Map.of()).get(ref.externalId());
            if (item == null) {
                unmatched.add(unmatchedItem(entry, "Matched but could not be loaded"));
                if (job != null) {
                    job.advance(false);
                }
                continue;
            }

            Upserted result = upsert(userId, provider, item, entry);
            if (result.created()) {
                created++;
            } else if (result.changed()) {
                updated++;
            }
            if (result.advanced()) {
                advanced.add(ActivityRecorder.Change.of(item.getTitle(), result.from(), result.to()));
            }
            if (job != null) {
                job.advance(result.created());
            }
        }

        /*
         * One event for the run rather than one per title. A first import would otherwise
         * write hundreds of rows at a single timestamp and bury everything its owner did by
         * hand — and a run that changed nothing records nothing at all.
         */
        activity.ran(userId, provider, created, advanced);

        return new ImportReport(created, updated, unmatched);
    }

    /**
     * Remembers which provider id resolved to which canonical item.
     *
     * <p>The resolver has just computed this and it is otherwise thrown away, leaving no
     * route from a tracked game back to the provider — which anything working per-item,
     * like achievements, needs.
     */
    private void recordProviderIds(
            Provider provider,
            Map<ExternalItemRef, CanonicalRef> resolved,
            Map<Source, Map<String, TrackableItem>> cached) {

        List<TrackableItem> updated = new ArrayList<>();
        resolved.forEach((ref, canonical) -> {
            TrackableItem item =
                    cached.getOrDefault(canonical.source(), Map.of()).get(canonical.externalId());
            if (item != null && ExternalIds.record(item, provider, ref.providerItemId())) {
                updated.add(item);
            }
        });

        // Items cached during this run come back detached from their own transaction, so
        // mutating them is not enough on its own.
        if (!updated.isEmpty()) {
            items.saveAll(updated);
        }
    }

    /**
     * Caches every title the library resolved to, counting the fetch onto the job.
     *
     * <p>A provider spanning two catalogues counts each separately, so the total moves when
     * the second starts. That is honest about what is left rather than pretending to know
     * up front how many of the second are already cached.
     */
    private Map<Source, Map<String, TrackableItem>> cacheResolvedItems(Iterable<CanonicalRef> refs, SyncJob job) {
        Map<Source, List<String>> idsBySource = new LinkedHashMap<>();
        refs.forEach(ref -> idsBySource
                .computeIfAbsent(ref.source(), source -> new ArrayList<>())
                .add(ref.externalId()));

        FetchProgress progress = job == null
                ? FetchProgress.IGNORED
                : (fetched, total) -> job.reportPhase(SyncJob.Phase.MATCHING, fetched, total);

        Map<Source, Map<String, TrackableItem>> cached = new LinkedHashMap<>();
        idsBySource.forEach((source, ids) -> cached.put(source, itemCache.findOrCacheAll(source, ids, progress)));
        return cached;
    }

    /**
     * What one title's import did, which is what the feed reports about the run.
     *
     * @param changed whether anything was actually written; a title found exactly as it was
     *     left is neither created nor updated
     */
    private record Upserted(boolean created, boolean changed, Integer from, Integer to) {

        boolean advanced() {
            return !created && !java.util.Objects.equals(from, to);
        }
    }

    private Upserted upsert(Long userId, Provider provider, TrackableItem item, ImportedEntry imported) {
        UserEntry existing =
                entries.findByUserIdAndItemId(userId, item.getId()).orElse(null);

        if (existing == null) {
            UserEntry entry = new UserEntry(userId, item, imported.status());
            entry.setImportedFrom(provider);
            applyProgress(entry, imported);
            entries.save(entry);
            return new Upserted(true, true, null, entry.getProgressCurrent());
        }

        // An entry added by hand and later found in an import did come from there too.
        if (existing.getImportedFrom() == null) {
            existing.setImportedFrom(provider);
        }

        Integer before = existing.getProgressCurrent();
        State held = State.of(existing);

        /*
         * Progress and status both come across, because both are what the provider knows and
         * what the reader went there to record: finishing a series on AniList and finding it
         * still listed as watching here is the import having done half its job.
         *
         * <p>A rating is left alone once it exists, and so are dates already set. Those are
         * the reader's own judgement about a title, and an import is a mirror of a shelf
         * rather than an opinion about one.
         */
        applyProgress(existing, imported);
        if (imported.status() != null) {
            existing.setStatus(imported.status());
        }

        // An entry that came back identical is one the run did not update, and saying it did
        // would make every re-import read as if the whole library had moved.
        if (held.equals(State.of(existing))) {
            return new Upserted(false, false, before, before);
        }

        entries.save(existing);
        return new Upserted(false, true, before, existing.getProgressCurrent());
    }

    /**
     * What an import may change about an entry, for telling a run that changed something from
     * one that found everything already as it was.
     */
    private record State(
            TrackingStatus status,
            Integer progressCurrent,
            Integer progressMax,
            ProgressUnit progressUnit,
            Short rating,
            LocalDate startedAt,
            LocalDate finishedAt,
            Provider importedFrom) {

        static State of(UserEntry entry) {
            return new State(
                    entry.getStatus(),
                    entry.getProgressCurrent(),
                    entry.getProgressMax(),
                    entry.getProgressUnit(),
                    entry.getRating(),
                    entry.getStartedAt(),
                    entry.getFinishedAt(),
                    entry.getImportedFrom());
        }
    }

    private void applyProgress(UserEntry entry, ImportedEntry imported) {
        entry.setProgressCurrent(imported.progressCurrent());
        entry.setProgressMax(imported.progressMax());
        entry.setProgressUnit(imported.progressUnit());

        if (entry.getRating() == null && imported.rawRating() != null) {
            entry.setRating(toInternalScale(imported.rawRating(), imported.rawRatingMax()));
        }
        if (entry.getStartedAt() == null) {
            entry.setStartedAt(imported.startedAt());
        }
        if (entry.getFinishedAt() == null) {
            entry.setFinishedAt(imported.finishedAt());
        }
    }

    /** Every source is normalised to 0-100 on the way in; the raw scale is never stored. */
    private Short toInternalScale(int rawRating, Integer rawMax) {
        int max = rawMax == null || rawMax <= 0 ? 100 : rawMax;
        return (short) Math.round(Math.min(rawRating, max) * 100.0 / max);
    }

    private ImportReport.UnmatchedItem unmatchedItem(ImportedEntry entry, String reason) {
        return new ImportReport.UnmatchedItem(
                entry.itemRef().providerItemId(), entry.itemRef().title(), reason);
    }

    private <T> T require(List<T> candidates, Function<T, Provider> key, Provider provider, String what) {
        return candidates.stream()
                .filter(candidate -> key.apply(candidate) == provider)
                .findFirst()
                .orElseThrow(() -> new ImportNotSupportedException("No " + what + " registered for " + provider));
    }
}
