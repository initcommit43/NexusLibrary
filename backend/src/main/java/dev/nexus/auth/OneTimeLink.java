package dev.nexus.auth;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;

/**
 * A link that has been mailed out, is good once, and dies on a deadline.
 *
 * <p>Password resets and address confirmations are the same mechanism pointed at different
 * questions, so they are the same row shape: a digest of the token, who it belongs to, when it
 * expires, and when it was spent. Kept in one place because two flows that look alike and
 * behave differently are how one of them ends up wrong.
 *
 * <p>Only the digest is stored. The token itself is the credential — a reset link sets a
 * password without the old one being known — so a leaked table must not be a set of working
 * links.
 */
@MappedSuperclass
public abstract class OneTimeLink {

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

    protected OneTimeLink() {
        // JPA
    }

    protected OneTimeLink(String tokenHash, Long userId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public Long getUserId() {
        return userId;
    }

    /** Unspent and not yet expired: the only state in which a link does anything. */
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
