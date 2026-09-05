package dev.nexus.auth;

import dev.nexus.config.NexusProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Getting back in without the old password.
 *
 * <p>The link stands in for knowing the password, so it is treated as one: 256 bits of
 * randomness, stored only as a digest, good once, and dead after half an hour. Between them
 * those mean a link cannot be guessed, cannot be read out of the database, cannot be replayed
 * after it is spent, and is not still working when the mailbox holding it is read months later.
 */
@Service
public class PasswordResetService {

    /**
     * 32 bytes. Far past guessing, and short enough that the URL survives a mail client that
     * decides where to wrap a line.
     */
    private static final int TOKEN_BYTES = 32;

    private final AppUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService sessions;
    private final Optional<PasswordResetMailer> mailer;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;
    private final String frontendUrl;

    public PasswordResetService(
            AppUserRepository users,
            PasswordResetTokenRepository tokens,
            PasswordEncoder passwordEncoder,
            RefreshTokenService sessions,
            Optional<PasswordResetMailer> mailer,
            NexusProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.mailer = mailer;
        this.ttl = Duration.ofMinutes(properties.security().passwordResetTtlMinutes());
        this.frontendUrl = properties.security().frontendUrl();
    }

    /**
     * Sends a link, if there is an account to send one to. Says nothing either way.
     *
     * <p>An address that answers differently from one that does not is a way to ask this
     * endpoint who has an account here, so the caller is told the same thing in both cases and
     * the difference is only whether a mail goes out.
     *
     * <p>Whether the deployment can send at all is settled first, before the account is looked
     * up. Refusing only once an account is found would answer 501 for a real address and 204
     * for a stranger's, which is the disclosure this otherwise avoids.
     */
    @Transactional
    public void requestLink(String email) {
        PasswordResetMailer send = mailer.orElseThrow(PasswordResetUnavailableException::new);

        users.findByEmail(EmailAddresses.normalise(email)).ifPresent(user -> {
            Instant now = Instant.now();
            tokens.spendEveryOutstandingLink(user.getId(), now);

            String token = newToken();
            tokens.save(new PasswordResetToken(digestOf(token), user.getId(), now.plus(ttl)));

            send.send(user, frontendUrl + "/reset-password?token=" + token, ttl);
        });
    }

    /**
     * Sets the password the link was asked for, and ends every session the account has.
     *
     * <p>Signing everything out is the point as much as the new password is: someone resetting
     * has usually lost control of the account or of a device holding it, and leaving the old
     * sessions live would hand it back with the new password already set. It happens in this
     * transaction rather than in the controller so there is no moment where the password has
     * changed and the old sessions have not gone.
     *
     * <p>No session is started here in exchange. Whoever holds the link proved they can read
     * the mailbox, not that they know the password that was just set — so they sign in with it,
     * which is also the last check that the reset was the one they meant to make.
     */
    @Transactional
    public void reset(String presentedToken, String newPassword) {
        Instant now = Instant.now();

        PasswordResetToken link = tokens.findByTokenHash(digestOf(presentedToken))
                .filter(candidate -> candidate.isLiveAt(now))
                .orElseThrow(PasswordResetLinkExpiredException::new);

        AppUser user = users.findById(link.getUserId())
                .orElseThrow(() -> new AuthenticationFailedException("Account no longer exists."));

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        link.spend(now);
        sessions.endEverySession(user.getId());
    }

    /** Drops rows whose links have expired. They already admit nothing; this is space, not safety. */
    @Transactional
    public int pruneExpired() {
        return tokens.deleteByExpiresAtBefore(Instant.now());
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        // URL-safe and unpadded: the token travels as a query parameter, and '+' or '=' in one
        // is a link that works until something along the way decides to re-encode it.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * A plain digest rather than a password hash. bcrypt exists to make guessing a human-chosen
     * secret expensive; there is nothing to guess in 32 random bytes, and a salted hash could
     * not be looked up by anyway.
     */
    private String digestOf(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
