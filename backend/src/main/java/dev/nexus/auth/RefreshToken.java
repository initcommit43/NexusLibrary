package dev.nexus.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One refresh token that is still allowed, named by the {@code jti} claim of the token
 * itself. Absence is refusal: a token whose id is not here, or is here and revoked, buys
 * nothing however well it is signed.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID jti;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuthClient client;

    @Column(name = "issued_at", nullable = false, updatable = false, insertable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {
        // JPA
    }

    public RefreshToken(UUID jti, Long userId, AuthClient client, Instant expiresAt) {
        this.jti = jti;
        this.userId = userId;
        this.client = client;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getJti() {
        return jti;
    }

    public Long getUserId() {
        return userId;
    }

    public AuthClient getClient() {
        return client;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    /**
     * The token's own expiry is checked as well as the row's state. The two agree when they
     * were written together, and a row that outlived its sweep must not admit a stale token.
     */
    public boolean isLiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    /** Keeps the first revocation's time: when a session ended is not changed by asking again. */
    public void revoke(Instant at) {
        if (revokedAt == null) {
            revokedAt = at;
        }
    }
}
