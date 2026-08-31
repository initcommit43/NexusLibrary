package dev.nexus.core.notifications;

import dev.nexus.auth.CurrentUser;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Notification;
import dev.nexus.core.domain.NotificationType;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** What was waiting for a reader while they were away. */
@Validated
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private static final int DEFAULT_LIMIT = 50;

    /**
     * As much as one request will hand back.
     *
     * <p>Matches the activity feed's ceiling, and for its reason: the page reading itself is
     * what reaches it, by asking for more of a module whose rows are scattered through
     * everything else.
     */
    private static final int MAX_LIMIT = 1000;

    /**
     * One line of the panel.
     *
     * @param read whether it has been seen; the one thing that makes a row look new
     */
    public record NotificationResponse(
            Long id,
            NotificationType type,
            MediaType mediaType,
            String title,
            String coverUrl,
            Source source,
            String externalId,
            Map<String, Object> payload,
            boolean read,
            Instant createdAt) {

        static NotificationResponse from(Notification notification) {
            TrackableItem item = notification.getItem();

            return new NotificationResponse(
                    notification.getId(),
                    notification.getType(),
                    item.getMediaType(),
                    item.getTitle(),
                    item.getCoverUrl(),
                    item.getSource(),
                    item.getExternalId(),
                    notification.getPayload(),
                    notification.getReadAt() != null,
                    notification.getCreatedAt());
        }
    }

    /** What is waiting, and how much of it is new. */
    public record Waiting(List<NotificationResponse> items, long unread) {}

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public Waiting waiting(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(required = false) List<MediaType> mediaTypes) {

        List<MediaType> scope = mediaTypes == null ? List.of() : mediaTypes;

        List<NotificationResponse> items = notifications.feedFor(user.id(), scope, limit).stream()
                .map(NotificationResponse::from)
                .toList();

        return new Waiting(items, notifications.unreadCount(user.id(), scope));
    }

    /**
     * Says the reader has seen one of them, and answers with what is left.
     *
     * <p>The whole panel comes back rather than the row alone: the count beside the heading
     * changes with it, and a caller that has to work out the new one from the old is a caller
     * that can be wrong about it.
     */
    @PostMapping("/{notificationId}/read")
    public Waiting read(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long notificationId,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(required = false) List<MediaType> mediaTypes) {

        notifications.markRead(user.id(), notificationId);
        return waiting(user, limit, mediaTypes);
    }

    /** Says the reader has seen the lot. Answers with what is left, which is nothing new. */
    @PostMapping("/read")
    public Waiting readAll(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit,
            @RequestParam(required = false) List<MediaType> mediaTypes) {

        notifications.markAllRead(user.id(), mediaTypes == null ? List.of() : mediaTypes);
        return waiting(user, limit, mediaTypes);
    }
}
