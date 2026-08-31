package dev.nexus.modules.anime;

import dev.nexus.core.domain.Notification;
import dev.nexus.core.domain.NotificationRepository;
import dev.nexus.core.domain.NotificationType;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Turns a page of AniList notifications into rows, and writes the ones that are new. */
@Component
public class AniListNotificationWriter {

    /**
     * What one page came to.
     *
     * @param stored notifications written
     * @param known ones already held, which a re-run is mostly made of
     * @param unmatched ones about a title that is not on the shelf
     */
    public record Written(int stored, int known, int unmatched) {

        public int seen() {
            return stored + known + unmatched;
        }
    }

    private final NotificationRepository notifications;
    private final TrackableItemRepository items;

    public AniListNotificationWriter(NotificationRepository notifications, TrackableItemRepository items) {
        this.notifications = notifications;
        this.items = items;
    }

    /**
     * Writes a page, skipping what is already held and what is about a title off the shelf.
     *
     * <p>Both lookups are one query for the whole page, as the activity walk does: fifty round
     * trips a page is the difference between a sync and an afternoon.
     *
     * <p>A related-media addition names the title that has just appeared rather than the one
     * the reader keeps, so it is off the shelf by definition and skipped here. Catching those
     * means caching the new title first, which is its own piece of work.
     */
    @Transactional
    public Written save(Long userId, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return new Written(0, 0, 0);
        }

        Set<String> mediaIds = rows.stream()
                .map(AniListNotificationWriter::mediaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Map<String, TrackableItem> shelved = items.findBySourceAndExternalIdIn(Source.ANILIST, mediaIds).stream()
                .collect(Collectors.toMap(TrackableItem::getExternalId, item -> item, (first, second) -> first));

        Set<String> told = shelved.isEmpty()
                ? Set.of()
                : notifications
                        .toldAbout(
                                userId,
                                shelved.values().stream().map(TrackableItem::getId).toList())
                        .stream()
                        .map(row -> key(row.getItemId(), row.getType(), row.getSubject()))
                        .collect(Collectors.toSet());

        List<Notification> fresh = new ArrayList<>();
        Set<String> writing = new HashSet<>();
        int known = 0;
        int unmatched = 0;

        for (Map<String, Object> row : rows) {
            TrackableItem item = shelved.get(mediaId(row));
            NotificationType type = typeOf(row);
            String subject = subjectOf(row, type);
            Instant happened = happenedAt(row.get("createdAt"));

            if (item == null || type == null || subject == null || happened == null) {
                unmatched++;
                continue;
            }

            // Held already, or held by an earlier row of this same page: AniList raises one
            // notification per episode, but the page is not ours to trust twice over.
            String key = key(item.getId(), type, subject);
            if (told.contains(key) || !writing.add(key)) {
                known++;
                continue;
            }

            fresh.add(new Notification(userId, item, type, subject, payloadOf(row, type), happened));
        }

        notifications.saveAll(fresh);
        return new Written(fresh.size(), known, unmatched);
    }

    /**
     * What the app calls the same event.
     *
     * <p>An aired episode is keyed by its number rather than by AniList's notification id, so
     * that the sweep here and the notification there are one event and not two: whichever
     * noticed it first is the one that gets written.
     */
    private static String subjectOf(Map<String, Object> row, NotificationType type) {
        if (type == NotificationType.EPISODE_AIRED) {
            return row.get("episode") instanceof Number episode ? "episode:" + episode.intValue() : null;
        }
        return "added";
    }

    private static NotificationType typeOf(Map<String, Object> row) {
        return switch (String.valueOf(row.get("type"))) {
            case "AIRING" -> NotificationType.EPISODE_AIRED;
            case "RELATED_MEDIA_ADDITION" -> NotificationType.TITLE_ADDED;
            default -> null;
        };
    }

    private static Map<String, Object> payloadOf(Map<String, Object> row, NotificationType type) {
        Map<String, Object> payload = new HashMap<>();
        if (type == NotificationType.EPISODE_AIRED && row.get("episode") instanceof Number episode) {
            payload.put("episode", episode.intValue());
        }
        return payload;
    }

    /** AniList stamps its notifications in Unix seconds. */
    private static Instant happenedAt(Object createdAt) {
        return createdAt instanceof Number seconds ? Instant.ofEpochSecond(seconds.longValue()) : null;
    }

    private static String key(Long itemId, NotificationType type, String subject) {
        return itemId + "\u0000" + type + "\u0000" + subject;
    }

    private static String mediaId(Map<String, Object> row) {
        return row.get("media") instanceof Map<?, ?> media && media.get("id") != null
                ? String.valueOf(media.get("id"))
                : null;
    }
}
