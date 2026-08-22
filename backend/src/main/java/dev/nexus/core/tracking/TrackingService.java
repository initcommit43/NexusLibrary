package dev.nexus.core.tracking;

import dev.nexus.core.activity.ActivityRecorder;
import dev.nexus.core.activity.ActivityRecorder.EntrySnapshot;
import dev.nexus.core.cache.ItemCacheService;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.core.tracking.dto.TrackRequest;
import dev.nexus.core.tracking.dto.UpdateEntryRequest;
import java.util.List;
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

    public TrackingService(UserEntryRepository entries, ItemCacheService itemCache, ActivityRecorder activity) {
        this.entries = entries;
        this.itemCache = itemCache;
        this.activity = activity;
    }

    @Transactional(readOnly = true)
    public List<UserEntry> listFor(Long userId) {
        return entries.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public UserEntry requireOwned(Long entryId, Long userId) {
        return entries.findByIdAndUserId(entryId, userId).orElseThrow(EntryNotFoundException::new);
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

        UserEntry saved = entries.save(entry);
        activity.changed(saved, before);
        return saved;
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
