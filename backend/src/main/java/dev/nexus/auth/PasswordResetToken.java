package dev.nexus.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One password reset link that has been handed out and not yet spent.
 *
 * <p>Only the digest of the link is here. The token itself is the credential — it sets a
 * password without the old one being known — so unlike a refresh token, which this table's
 * neighbour names by an id, it must not be readable from the database it is checked against.
 */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected PasswordResetToken() {
        // JPA
    }

    public PasswordResetToken(String tokenHash, Long userId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    /** A link is good once, and only inside its half hour. */
    public boolean isLiveAt(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    /** Keeps the first spending's time: a link does not become fresher by being presented again. */
    public void spend(Instant at) {
        if (usedAt == null) {
            usedAt = at;
        }
    }
}
