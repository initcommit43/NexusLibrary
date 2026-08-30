package dev.nexus.core.tracking;

import dev.nexus.core.activity.ActivityRecorder;
import dev.nexus.core.activity.ActivityRecorder.EntrySnapshot;
import dev.nexus.core.cache.ItemCacheService;
import dev.nexus.core.cache.ItemRefreshService;
import dev.nexus.core.domain.ActivityRepository;
import dev.nexus.core.domain.ProviderActivityRepository;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.core.tracking.dto.TrackRequest;
import dev.nexus.core.tracking.dto.UpdateEntryRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns every read and write of a user's entries.
 *
 * <p>Authorization is structural rather than checked: the repository is only ever asked for
 * rows already scoped to the caller's own {@code userId}, which comes from the authenticated
 * principal and never from the request. A caller cannot address another user's row at all.
 */
@Service
public class TrackingService {

    private final UserEntryRepository entries;
    private final ItemCacheService itemCache;
    private final ActivityRecorder activity;
    private final ItemRefreshService refresh;
    private final ProviderActivityRepository imported;
    private final ActivityRepository activities;

    public TrackingService(
            UserEntryRepository entries,
            ItemCacheService itemCache,
            ActivityRecorder activity,
            ItemRefreshService refresh,
            ProviderActivityRepository imported,
            ActivityRepository activities) {
        this.entries = entries;
        this.itemCache = itemCache;
        this.activity = activity;
        this.refresh = refresh;
        this.imported = imported;
        this.activities = activities;
    }

    /**
     * When each of a reader's titles was last actually at, from both records of it.
     *
     * <p>Not the entry's own timestamp, which says when the row was last written: an import
     * writes hundreds of rows in one second, so ordering a freshly imported library by it
     * orders it by nothing. What counts is what happened to the title — an episode logged on
     * AniList, a status moved here — and neither of those is written by a bulk run.
     *
     * <p>Read beside the library rather than joined onto it: two grouped queries for the whole
     * shelf, against an outer join paid for by every read of it.
     */
    @Transactional(readOnly = true)
    public Map<Long, Instant> lastActivityByItem(Long userId) {
        Map<Long, Instant> latest = new HashMap<>();

        // A provider gives the day rather than the moment, so it is read as the start of that
        // day: enough to order titles against each other, which is all this is for.
        imported.latestPerItem(userId)
                .forEach(row -> latest.merge(
                        row.getItemId(),
                        row.getDay().atStartOfDay(ZoneId.systemDefault()).toInstant(),
                        this::later));

        activities.lastTouchedPerItem(userId)
                .forEach(row -> latest.merge(row.getItemId(), row.getAt(), this::later));

        return latest;
    }

    private Instant later(Instant one, Instant other) {
        return one.isAfter(other) ? one : other;
    }

    @Transactional(readOnly = true)
    public List<UserEntry> listFor(Long userId) {
        List<UserEntry> owned = entries.findByUserIdOrderByUpdatedAtDesc(userId);
        // The dashboard is the read that keeps the cache honest: it sees a user's whole library
        // at once, so anything past its TTL is noticed here and re-fetched behind the response.
        refresh.refreshIfStale(owned.stream().map(UserEntry::getItem).toList());
        return owned;
    }

    /** This reader's entry for a catalogue item, when they have one. */
    @Transactional(readOnly = true)
    public Optional<UserEntry> findByItem(Long userId, Long itemId) {
        return entries.findByUserIdAndItemId(userId, itemId);
    }

    @Transactional(readOnly = true)
    public UserEntry requireOwned(Long entryId, Long userId) {
        UserEntry entry = entries.findByIdAndUserId(entryId, userId).orElseThrow(EntryNotFoundException::new);
        refresh.refreshIfStale(entry.getItem());
        return entry;
    }

    /**
     * Tracks an item, caching it on first sighting. Tracking something already tracked
     * updates the existing entry, which is what the unique (user, item) constraint expects.
     */
    @Transactional
    public UserEntry track(Long userId, TrackRequest request) {
        TrackableItem item = itemCache.findOrCache(request.source(), request.externalId());

        UserEntry existing = entries.findByUserIdAndItemId(userId, item.getId()).orElse(null);
        boolean isNew = existing == null;
        UserEntry entry = isNew ? new UserEntry(userId, item, request.status()) : existing;
        EntrySnapshot before = isNew ? null : EntrySnapshot.of(entry);
        boolean wasAtTheEnd = !isNew && atTheEnd(entry);

        entry.setStatus(request.status());
        applyIfPresent(request.rating(), entry::setRating);
        applyIfPresent(request.progressCurrent(), entry::setProgressCurrent);
        applyIfPresent(request.progressMax(), entry::setProgressMax);
        applyIfPresent(request.progressUnit(), entry::setProgressUnit);
        applyIfPresent(request.startedAt(), entry::setStartedAt);
        applyIfPresent(request.finishedAt(), entry::setFinishedAt);
        applyIfPresent(request.notes(), entry::setNotes);
        if (request.favorite() != null) {
            entry.setFavorite(request.favorite());
        }
        completeIfFinished(entry, request.status(), wasAtTheEnd);

        UserEntry saved = entries.save(entry);
        if (isNew) {
            activity.added(saved);
        } else {
            activity.changed(saved, before);
        }
        return saved;
    }

    @Transactional
    public UserEntry update(Long userId, Long entryId, UpdateEntryRequest request) {
        UserEntry entry = requireOwned(entryId, userId);
        EntrySnapshot before = EntrySnapshot.of(entry);
        boolean wasAtTheEnd = atTheEnd(entry);

        applyIfPresent(request.status(), entry::setStatus);
        applyIfPresent(request.rating(), entry::setRating);
        applyIfPresent(request.progressCurrent(), entry::setProgressCurrent);
        applyIfPresent(request.progressMax(), entry::setProgressMax);
        applyIfPresent(request.progressUnit(), entry::setProgressUnit);
        applyIfPresent(request.startedAt(), entry::setStartedAt);
        applyIfPresent(request.finishedAt(), entry::setFinishedAt);
        applyIfPresent(request.notes(), entry::setNotes);
        if (request.favorite() != null) {
            entry.setFavorite(request.favorite());
        }
        completeIfFinished(entry, request.status(), wasAtTheEnd);

        UserEntry saved = entries.save(entry);
        activity.changed(saved, before);
        return saved;
    }

    /** Whether the progress stands at its end, where there is an end for it to stand at. */
    private boolean atTheEnd(UserEntry entry) {
        Integer current = entry.getProgressCurrent();
        Integer max = entry.getProgressMax();
        return current != null && max != null && max > 0 && current >= max;
    }

    /**
     * The last episode is the end of the thing: reaching it finishes the entry.
     *
     * <p>What every service a reader comes from already does, and what makes the shelf agree
     * with itself — twelve of twelve sitting under "watching" is a list saying two different
     * things about one title.
     *
     * <p>On reaching the end, and only then. A reader who puts a finished thing back to
     * watching has said what it is, and every later change to it — a note, a rating, the same
     * twelve of twelve written again — would otherwise take that back for them. So it is the
     * arrival at the end that completes an entry, not standing at it.
     *
     * <p>A status sent with the progress is the reader saying so in the same breath, and wins
     * outright: dropping something on its last episode is a thing people do.
     *
     * @param askedFor the status the request carried, or null when it said nothing about it
     * @param wasAtTheEnd whether the progress was already at its end before this change
     */
    private void completeIfFinished(UserEntry entry, TrackingStatus askedFor, boolean wasAtTheEnd) {
        if (askedFor != null
                || wasAtTheEnd
                || !atTheEnd(entry)
                || entry.getStatus() == TrackingStatus.COMPLETED) {
            return;
        }

        entry.setStatus(TrackingStatus.COMPLETED);
        // The day it was finished is today, which is also what puts it on the reader's map.
        if (entry.getFinishedAt() == null) {
            entry.setFinishedAt(LocalDate.now());
        }
    }

    /**
     * Writes the reader's own arrangement of their favourites.
     *
     * <p>Every id is looked up scoped to the caller, so an id belonging to someone else is
     * simply not found and the whole arrangement is refused rather than partly applied. Rank
     * is the position in the list, which leaves no gaps to reason about later.
     */
    @Transactional
    public List<UserEntry> reorderFavourites(Long userId, List<Long> entryIds) {
        List<UserEntry> arranged = entryIds.stream()
                .map(id -> entries.findByIdAndUserId(id, userId).orElseThrow(EntryNotFoundException::new))
                .toList();

        for (int rank = 0; rank < arranged.size(); rank++) {
            arranged.get(rank).setFavoriteRank(rank);
        }
        return entries.saveAll(arranged);
    }

    @Transactional
    public void delete(Long userId, Long entryId) {
        if (entries.deleteByIdAndUserId(entryId, userId) == 0) {
            throw new EntryNotFoundException();
        }
    }

    private <T> void applyIfPresent(T value, java.util.function.Consumer<T> setter) {
        Optional.ofNullable(value).ifPresent(setter);
    }
}
