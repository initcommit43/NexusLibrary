package dev.nexus.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Something that happened to a title a reader keeps, while they were not looking.
 *
 * <p>Distinct from {@link Activity}, which is what the reader did. An episode airs whether or
 * not anyone opens the app, and the point of the row is that it is waiting when they do.
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "trackable_item_id", nullable = false)
    private TrackableItem item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    /**
     * What exactly happened, as a short stable key — "episode:12", "added".
     *
     * <p>It is what makes telling someone twice impossible: the detector runs again every
     * quarter of an hour, and an episode that aired stays aired.
     */
    @Column(nullable = false)
    private String subject;

    /** What the line says: the episode number, the name of the season that appeared. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> payload = new HashMap<>();

    /**
     * When it happened, not when the row was written.
     *
     * <p>Supplied rather than defaulted because an import carries its own: a stream brought in
     * from AniList is years of events arriving in one second, and a feed sorted by the moment
     * each row was inserted would stack all of them under the same minute.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Null until it has been seen, which is the whole of what makes one new. */
    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
        // JPA
    }

    public Notification(
            Long userId,
            TrackableItem item,
            NotificationType type,
            String subject,
            Map<String, Object> payload) {
        this(userId, item, type, subject, payload, Instant.now());
    }

    public Notification(
            Long userId,
            TrackableItem item,
            NotificationType type,
            String subject,
            Map<String, Object> payload,
            Instant createdAt) {
        this.createdAt = createdAt;
        this.userId = userId;
        this.item = item;
        this.type = type;
        this.subject = subject;
        this.payload = payload == null ? new HashMap<>() : new HashMap<>(payload);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public TrackableItem getItem() {
        return item;
    }

    public NotificationType getType() {
        return type;
    }

    public String getSubject() {
        return subject;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void markRead(Instant when) {
        if (readAt == null) {
            readAt = when;
        }
    }
}
