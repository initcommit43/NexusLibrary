package dev.nexus.auth;

import dev.nexus.config.NexusProperties;
import java.time.Duration;
import java.time.Instant;
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
     * Mirrors {@link EmailVerificationService}, which has always had this and which this flow is
     * otherwise built from the same parts as. It protects the inbox on the other end, not this
     * server: the per-IP limiter caps how fast one caller can ask, but says nothing about how
     * many of those asks may name the same stranger's address.
     */
    private static final int MAX_LINKS_PER_HOUR = 5;

    private final AppUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService sessions;
    private final Optional<PasswordResetMailer> mailer;
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

        users.findByEmailIgnoreCase(EmailAddresses.normalise(email)).ifPresent(user -> {
            Instant now = Instant.now();

            /*
             * Over the cap, nothing is sent and nothing is said. Deliberately not an exception:
             * a 429 for a real address beside a 204 for a stranger's is exactly the difference
             * this endpoint exists to hide, and six tries would be all it took to read it.
             *
             * Checked before the retirement below, not after. The other order would answer the
             * sixth ask by killing the five live links and sending no replacement, leaving
             * someone who really is locked out with nothing at all.
             */
            if (tokens.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1)))
                    >= MAX_LINKS_PER_HOUR) {
                return;
            }

            tokens.spendEveryOutstandingLink(user.getId(), now);

            String token = LinkTokens.mint();
            tokens.save(new PasswordResetToken(LinkTokens.digestOf(token), user.getId(), now.plus(ttl)));

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

        PasswordResetToken link = tokens.findByTokenHash(LinkTokens.digestOf(presentedToken))
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
}
