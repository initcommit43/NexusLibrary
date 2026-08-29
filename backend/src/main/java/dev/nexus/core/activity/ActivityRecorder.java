package dev.nexus.core.activity;

import dev.nexus.core.domain.Activity;
import dev.nexus.core.domain.ActivityRepository;
import dev.nexus.core.domain.ActivityType;
import dev.nexus.core.domain.Provider;
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

    /** Enough for the hover card to show a handful and say how many more there were. */
    private static final int MAX_TITLES_IN_PAYLOAD = 20;

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

    /**
     * What a run brought in, as one event rather than one per title.
     *
     * <p>A first import of a large library would otherwise write hundreds of rows at a single
     * timestamp and bury everything its owner did by hand — and "you added 771 anime" is not
     * true anyway: they arrived together, in one act.
     *
     * @param changes the titles that moved, newest first; only the first few are kept, since
     *     a payload that grows with a library is a row that grows without limit
     */
    @Transactional
    public void ran(Long userId, Provider provider, int added, List<Change> advanced) {
        // A nightly sync that found nothing is not news, and a feed saying so every night is
        // a feed nobody reads.
        if (added == 0 && advanced.isEmpty()) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", provider.name());
        payload.put("added", added);
        payload.put("advanced", advanced.size());
        payload.put(
                "titles",
                advanced.stream().limit(MAX_TITLES_IN_PAYLOAD).map(Change::asPayload).toList());

        // A run that brought titles in is an import, whether or not it is the first: "three
        // arrived from AniList" is what happened, and a later run saying so is not wrong.
        ActivityType type = added > 0 ? ActivityType.IMPORTED : ActivityType.SYNCED;
        activities.save(new Activity(userId, null, type, payload));
    }

    /** One title a run touched, as the feed's hover card reads it. */
    public record Change(String title, String from, String to) {

        public static Change of(String title, Object from, Object to) {
            return new Change(title, from == null ? null : String.valueOf(from), String.valueOf(to));
        }

        private Map<String, Object> asPayload() {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("title", title);
            entry.put("from", from);
            entry.put("to", to);
            return entry;
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
