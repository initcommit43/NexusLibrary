package dev.nexus.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One verification link that has been handed out and not yet followed.
 *
 * <p>Only the digest is stored, for the same reason its neighbour does it: a leaked table must
 * not be a set of working links. This one grants less than a reset link — it confirms an
 * address already on the account rather than setting a password — but it is still the thing
 * standing between a stranger and a usable account, so it is kept the same way.
 */
@Entity
@Table(name = "email_verification_token")
public class EmailVerificationToken {

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

    protected EmailVerificationToken() {
        // JPA
    }

    public EmailVerificationToken(String tokenHash, Long userId, Instant expiresAt) {
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

    /** Unspent and not yet expired: the only state in which a link opens an account. */
    public boolean isLiveAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void spend(Instant at) {
        this.usedAt = at;
    }
}
