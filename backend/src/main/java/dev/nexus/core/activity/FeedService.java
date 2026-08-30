package dev.nexus.core.activity;

import dev.nexus.core.domain.Activity;
import dev.nexus.core.domain.ActivityRepository;
import dev.nexus.core.domain.ActivityType;
import dev.nexus.core.domain.ProviderActivity;
import dev.nexus.core.domain.ProviderActivityRepository;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a reader did, from both places it is known.
 *
 * <p>This app records what was done in it. A provider's own stream records what was done
 * before any of it existed — the episodes watched on AniList over years, which arrive with an
 * import. A reader has one history, so the feed reads the two together rather than keeping the
 * imported half where only the map can see it.
 */
@Service
public class FeedService {

    /** What the id of a feed row is prefixed with, since the two halves number themselves. */
    private static final String OWN = "own:";

    private static final String IMPORTED = "imported:";

    /** One row of the feed, from whichever side it came. */
    public record FeedEvent(
            String id,
            ActivityType type,
            TrackableItem item,
            Map<String, Object> payload,
            Instant at) {}

    private final ActivityRepository activities;
    private final ProviderActivityRepository imported;
    private final TrackableItemRepository items;

    public FeedService(
            ActivityRepository activities,
            ProviderActivityRepository imported,
            TrackableItemRepository items) {
        this.activities = activities;
        this.imported = imported;
        this.items = items;
    }

    /**
     * The newest events of a reader's, both halves merged.
     *
     * <p>Each side is asked for a whole page of its own before they are merged and cut back:
     * a reader who imported a decade of AniList this morning would otherwise see that and
     * nothing they have done since.
     */
    @Transactional(readOnly = true)
    public List<FeedEvent> feedFor(Long userId, int limit) {
        List<FeedEvent> merged = new ArrayList<>(own(userId, limit));
        merged.addAll(imported(userId, limit));

        return merged.stream()
                .sorted(Comparator.comparing(FeedEvent::at).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Forgets one event, whichever half of the feed it came from.
     *
     * <p>An imported event is forgotten for good rather than hidden: it is also a square on
     * the reader's map, and a feed and a map disagreeing about the same day is worse than
     * either of them being one event short.
     */
    @Transactional
    public void forget(Long userId, String feedId) {
        long deleted = feedId.startsWith(IMPORTED)
                ? imported.deleteByIdAndUserId(idIn(feedId, IMPORTED), userId)
                : activities.deleteByIdAndUserId(idIn(feedId, OWN), userId);

        if (deleted == 0) {
            throw new ActivityNotFoundException();
        }
    }

    private List<FeedEvent> own(Long userId, int limit) {
        return activities.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(limit)).stream()
                .map(activity -> new FeedEvent(
                        OWN + activity.getId(),
                        activity.getType(),
                        activity.getItem(),
                        activity.getPayload(),
                        activity.getCreatedAt()))
                .toList();
    }

    private List<FeedEvent> imported(Long userId, int limit) {
        List<ProviderActivity> events =
                imported.findByUserIdOrderByHappenedOnDescIdDesc(userId, Limit.of(limit));

        Map<Long, TrackableItem> titles = items
                .findAllById(events.stream().map(ProviderActivity::getItemId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(TrackableItem::getId, Function.identity()));

        return events.stream()
                .map(event -> new FeedEvent(
                        IMPORTED + event.getId(),
                        ActivityType.EXTERNAL,
                        titles.get(event.getItemId()),
                        payloadOf(event),
                        // A day, not a moment: what the provider gave was the day it happened,
                        // and the feed places it at the start of that day rather than inventing
                        // an hour for it.
                        event.getHappenedOn().atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .filter(event -> event.item() != null)
                .toList();
    }

    /** What the provider said the event was, in its own words: "watched episode", "5". */
    private Map<String, Object> payloadOf(ProviderActivity event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("provider", event.getProvider().name());
        payload.put("status", event.getStatus());
        payload.put("progress", event.getProgress());
        return payload;
    }

    private Long idIn(String feedId, String prefix) {
        try {
            return Long.valueOf(feedId.substring(prefix.length()));
        } catch (NumberFormatException e) {
            throw new ActivityNotFoundException();
        }
    }
}
