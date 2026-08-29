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

/** One thing a user did to an item. Immutable once written. */
@Entity
@Table(name = "activity")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * The title this happened to, where it happened to one. An import or a sync is about a
     * run rather than a title, and names what it touched in its payload.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "trackable_item_id")
    private TrackableItem item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    /** What changed, as old to new. Shape varies by type and is never queried on. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> payload = new HashMap<>();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Activity() {
        // JPA
    }

    public Activity(Long userId, TrackableItem item, ActivityType type, Map<String, Object> payload) {
        this.userId = userId;
        this.item = item;
        this.type = type;
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

    public ActivityType getType() {
        return type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
