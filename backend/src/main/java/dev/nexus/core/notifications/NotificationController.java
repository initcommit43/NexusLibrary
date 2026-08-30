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

    private static final int MAX_LIMIT = 200;

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
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Positive @Max(MAX_LIMIT) int limit) {

        List<NotificationResponse> items = notifications.feedFor(user.id(), limit).stream()
                .map(NotificationResponse::from)
                .toList();

        return new Waiting(items, notifications.unreadCount(user.id()));
    }

    /** Says the reader has seen the lot. Answers with what is left, which is nothing new. */
    @PostMapping("/read")
    public Waiting readAll(@AuthenticationPrincipal CurrentUser user) {
        notifications.markAllRead(user.id());
        return waiting(user, DEFAULT_LIMIT);
    }
}
