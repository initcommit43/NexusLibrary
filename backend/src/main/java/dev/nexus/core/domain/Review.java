package dev.nexus.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A user's written review of one tracked item. The rating stays on {@link UserEntry}: you
 * can rate something without writing about it, and the dashboard needs the score without
 * loading the prose.
 */
@Entity
@Table(name = "review")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ownership is inherited from the entry rather than duplicated as a user_id here,
    // which keeps a review from ever disagreeing with the entry about who owns it.
    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_entry_id", nullable = false, unique = true)
    private UserEntry entry;

    @Column(nullable = false)
    private String body;

    @Column(name = "contains_spoilers", nullable = false)
    private boolean containsSpoilers;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    protected Review() {
        // JPA
    }

    public Review(UserEntry entry, String body, boolean containsSpoilers) {
        this.entry = entry;
        this.body = body;
        this.containsSpoilers = containsSpoilers;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UserEntry getEntry() {
        return entry;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public boolean isContainsSpoilers() {
        return containsSpoilers;
    }

    public void setContainsSpoilers(boolean containsSpoilers) {
        this.containsSpoilers = containsSpoilers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
