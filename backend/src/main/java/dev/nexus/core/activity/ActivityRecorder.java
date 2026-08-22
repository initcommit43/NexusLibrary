package dev.nexus.core.activity;

import dev.nexus.core.domain.Activity;
import dev.nexus.core.domain.ActivityRepository;
import dev.nexus.core.domain.ActivityType;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.UserEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the history of what a user did.
 *
 * <p>Called from the service layer rather than from controllers, so a manual edit and a
 * library import produce the same history without either path having to remember to.
 */
@Service
public class ActivityRecorder {

    private static final int DEFAULT_FEED_SIZE = 50;

    private final ActivityRepository activities;

    public ActivityRecorder(ActivityRepository activities) {
        this.activities = activities;
    }

    @Transactional(readOnly = true)
    public List<Activity> feedFor(Long userId, int limit) {
        return activities.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(limit <= 0 ? DEFAULT_FEED_SIZE : limit));
    }

    @Transactional
    public void added(UserEntry entry) {
        record(entry, ActivityType.ADDED, Map.of("status", entry.getStatus().name()));
    }

    @Transactional
    public void reviewed(UserEntry entry) {
        record(entry, ActivityType.REVIEWED, Map.of());
    }

    /**
     * Compares an entry before and after an edit and writes one activity per thing that
     * actually moved. Nothing is recorded when a value is re-submitted unchanged, which
     * keeps a re-import from filling the feed with noise.
     */
    @Transactional
    public void changed(UserEntry entry, EntrySnapshot before) {
        if (before.status() != entry.getStatus()) {
            record(entry, ActivityType.STATUS_CHANGE, change(before.status(), entry.getStatus()));
        }
        if (!java.util.Objects.equals(before.rating(), entry.getRating()) && entry.getRating() != null) {
            record(entry, ActivityType.RATED, change(before.rating(), entry.getRating()));
        }
        if (!java.util.Objects.equals(before.progressCurrent(), entry.getProgressCurrent())
                && entry.getProgressCurrent() != null) {
            Map<String, Object> payload = change(before.progressCurrent(), entry.getProgressCurrent());
            if (entry.getProgressUnit() != null) {
                payload.put("unit", entry.getProgressUnit().name());
            }
            record(entry, ActivityType.PROGRESS, payload);
        }
    }

    private void record(UserEntry entry, ActivityType type, Map<String, Object> payload) {
        TrackableItem item = entry.getItem();
        activities.save(new Activity(entry.getUserId(), item, type, payload));
    }

    /** Old and new together, so the feed can read "Playing to Completed" without a join. */
    private Map<String, Object> change(Object from, Object to) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from == null ? null : String.valueOf(from));
        payload.put("to", to == null ? null : String.valueOf(to));
        return payload;
    }

    /** The fields worth comparing after an edit, captured before it happens. */
    public record EntrySnapshot(
            dev.nexus.core.domain.TrackingStatus status, Short rating, Integer progressCurrent) {

        public static EntrySnapshot of(UserEntry entry) {
            return new EntrySnapshot(entry.getStatus(), entry.getRating(), entry.getProgressCurrent());
        }
    }
}
