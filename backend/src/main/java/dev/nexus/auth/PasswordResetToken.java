package dev.nexus.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One password reset link that has been handed out and not yet spent.
 *
 * <p>Everything about how a link behaves lives in {@link OneTimeLink}; this says only which
 * table the resets are kept in.
 */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken extends OneTimeLink {

    protected PasswordResetToken() {
        // JPA
    }

    public PasswordResetToken(String tokenHash, Long userId, Instant expiresAt) {
        super(tokenHash, userId, expiresAt);
    }
}
