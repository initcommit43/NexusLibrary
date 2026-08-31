package dev.nexus.core.notifications;

import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Notification;
import dev.nexus.core.domain.NotificationRepository;
import dev.nexus.core.domain.NotificationType;
import dev.nexus.core.domain.TrackableItem;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What is waiting for a reader: read, counted, and marked as seen. */
@Service
public class NotificationService {

    /** As many as the panel shows before anyone asks for more of them. */
    private static final int DEFAULT_LIMIT = 50;

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /** Every module's, for a caller that has not said which one it is showing. */
    @Transactional(readOnly = true)
    public List<Notification> feedFor(Long userId, int limit) {
        return feedFor(userId, List.of(), limit);
    }

    /** One module's, given the media types it owns; every module's when that is empty. */
    @Transactional(readOnly = true)
    public List<Notification> feedFor(Long userId, Collection<MediaType> mediaTypes, int limit) {
        Limit cap = Limit.of(limit <= 0 ? DEFAULT_LIMIT : limit);

        return mediaTypes.isEmpty()
                ? notifications.findByUserIdOrderByCreatedAtDesc(userId, cap)
                : notifications.findByUserIdAndItemMediaTypeInOrderByCreatedAtDesc(userId, mediaTypes, cap);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return unreadCount(userId, List.of());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId, Collection<MediaType> mediaTypes) {
        return mediaTypes.isEmpty()
                ? notifications.countByUserIdAndReadAtIsNull(userId)
                : notifications.countByUserIdAndItemMediaTypeInAndReadAtIsNull(userId, mediaTypes);
    }

    /**
     * Marks one as seen.
     *
     * <p>Silent when the id is not this reader's: it is the same answer as one already read,
     * and telling a caller which ids exist is telling them about somebody else's rows.
     */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        notifications.markRead(notificationId, userId, Instant.now());
    }

    /** @return how many were still unread when the reader said they had seen them */
    @Transactional
    public int markAllRead(Long userId) {
        return markAllRead(userId, List.of());
    }

    /** The ones under the heading the reader pressed it beneath, and no others. */
    @Transactional
    public int markAllRead(Long userId, Collection<MediaType> mediaTypes) {
        Instant now = Instant.now();

        return mediaTypes.isEmpty()
                ? notifications.markAllRead(userId, now)
                : notifications.markAllRead(userId, mediaTypes, now);
    }

    /**
     * Raises one, unless this reader has already been told this exact thing.
     *
     * <p>The detectors run again every quarter of an hour and an episode that aired stays
     * aired, so "already told" is the normal case rather than the exception. It is checked
     * here as well as being refused by the table, because reaching the constraint would break
     * the transaction the whole batch is being written in.
     */
    @Transactional
    public void raise(
            Long userId, TrackableItem item, NotificationType type, String subject, Map<String, Object> payload) {

        Set<String> told = Set.copyOf(notifications.subjectsFor(userId, item.getId(), type));
        if (told.contains(subject)) {
            return;
        }
        notifications.save(new Notification(userId, item, type, subject, payload));
    }
}
