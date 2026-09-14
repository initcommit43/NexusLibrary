package dev.nexus.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One verification link that has been handed out and not yet followed.
 *
 * <p>The same row as a reset link, in its own table: this one grants less — it confirms an
 * address already on the account rather than setting a password — but it is still the thing
 * standing between a stranger and a usable account, so it is kept the same way. The behaviour
 * is {@link OneTimeLink}'s.
 */
@Entity
@Table(name = "email_verification_token")
public class EmailVerificationToken extends OneTimeLink {

    protected EmailVerificationToken() {
        // JPA
    }

    public EmailVerificationToken(String tokenHash, Long userId, Instant expiresAt) {
        super(tokenHash, userId, expiresAt);
    }
}
