package dev.nexus.core.importing;

import dev.nexus.core.adapter.CanonicalRef;
import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.adapter.LibraryImportAdapter;
import dev.nexus.core.adapter.ItemResolver;
import dev.nexus.core.cache.ItemCacheService;
import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.ExternalIds;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.jobs.SyncJob;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.domain.UserEntryRepository;
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
 * cache on miss, upsert, and report what did not match. Adding MAL or Trakt later means
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

    public LibraryImportService(
            List<LibraryImportAdapter> adapters,
            List<ItemResolver> resolvers,
            ItemCacheService itemCache,
            UserEntryRepository entries,
            TrackableItemRepository items) {
        // Kept as lists and matched on demand: there will only ever be a handful of
        // providers, and indexing them up front would call into every adapter at startup.
        this.adapters = List.copyOf(adapters);
        this.resolvers = List.copyOf(resolvers);
        this.itemCache = itemCache;
        this.entries = entries;
        this.items = items;
    }

    @Transactional
    public ImportReport importLibrary(ExternalAccount account) {
        return importLibrary(account, null);
    }

    /**
     * @param job optional, reporting progress as the library is walked. Pulling and
     *     resolving happen before the first item is counted, so the count starts once there
     *     is something to count.
     */
    @Transactional
    public ImportReport importLibrary(ExternalAccount account, SyncJob job) {
        Provider provider = account.getProvider();
        LibraryImportAdapter adapter =
                require(adapters, LibraryImportAdapter::provider, provider, "import adapter");
        ItemResolver resolver = require(resolvers, ItemResolver::provider, provider, "item resolver");

        List<ImportedEntry> library = adapter.pullLibrary(account);
        log.debug("Pulled {} items from {}", library.size(), provider);
        if (job != null) {
            job.setTotal(library.size());
        }

        Map<ExternalItemRef, CanonicalRef> resolved =
                resolver.resolveAll(library.stream().map(ImportedEntry::itemRef).toList());

        // One batch per catalogue: already-cached titles cost nothing, and a title another
        // user cached earlier costs nothing either.
        Map<Source, Map<String, TrackableItem>> cached = cacheResolvedItems(resolved.values());
        recordProviderIds(provider, resolved, cached);

        List<ImportReport.UnmatchedItem> unmatched = new ArrayList<>();
        int created = 0;
        int updated = 0;

        for (ImportedEntry entry : library) {
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

            boolean isNew = upsert(account.getUserId(), provider, item, entry);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
            if (job != null) {
                job.advance(isNew);
            }
        }

        account.markSynced();
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

    private Map<Source, Map<String, TrackableItem>> cacheResolvedItems(Iterable<CanonicalRef> refs) {
        Map<Source, List<String>> idsBySource = new LinkedHashMap<>();
        refs.forEach(ref -> idsBySource
                .computeIfAbsent(ref.source(), source -> new ArrayList<>())
                .add(ref.externalId()));

        Map<Source, Map<String, TrackableItem>> cached = new LinkedHashMap<>();
        idsBySource.forEach((source, ids) -> cached.put(source, itemCache.findOrCacheAll(source, ids)));
        return cached;
    }

    /**
     * @return true when a new entry was created, false when an existing one was updated
     */
    private boolean upsert(Long userId, Provider provider, TrackableItem item, ImportedEntry imported) {
        UserEntry existing =
                entries.findByUserIdAndItemId(userId, item.getId()).orElse(null);

        if (existing == null) {
            UserEntry entry = new UserEntry(userId, item, imported.status());
            entry.setImportedFrom(provider);
            applyProgress(entry, imported);
            entries.save(entry);
            return true;
        }

        // An entry added by hand and later found in an import did come from there too.
        if (existing.getImportedFrom() == null) {
            existing.setImportedFrom(provider);
        }

        // An import must not overwrite what the user set by hand. Progress is objective and
        // comes from the provider; status and rating are the user's own judgement, so they
        // are only filled in where the user has expressed nothing.
        applyProgress(existing, imported);
        entries.save(existing);
        return false;
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
