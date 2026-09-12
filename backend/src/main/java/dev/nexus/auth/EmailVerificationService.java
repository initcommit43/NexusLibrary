package dev.nexus.auth;

import dev.nexus.core.mail.Mailer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the address on an account reaches whoever opened it.
 *
 * <p>Shaped like {@link PasswordResetService} on purpose — same token size, same digest, same
 * one-live-link rule — because the two are the same mechanism pointed at different questions,
 * and two flows that look alike and behave differently are how one of them ends up wrong.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final int TOKEN_BYTES = 32;

    /** Enough to stop a resend button becoming a mail cannon aimed at someone else's inbox. */
    private static final int MAX_LINKS_PER_HOUR = 5;

    private final AppUserRepository users;
    private final EmailVerificationTokenRepository tokens;
    private final Optional<Mailer> mailer;
    private final SecureRandom random = new SecureRandom();
    private final String frontendUrl;
    private final Duration ttl;

    public EmailVerificationService(
            AppUserRepository users,
            EmailVerificationTokenRepository tokens,
            Optional<Mailer> mailer,
            @Value("${nexus.security.frontend-url}") String frontendUrl,
            @Value("${nexus.verification.ttl-hours:48}") long ttlHours) {
        this.users = users;
        this.tokens = tokens;
        this.mailer = mailer;
        this.frontendUrl = frontendUrl;
        this.ttl = Duration.ofHours(ttlHours);
    }

    /** Whether this deployment can send at all, which decides whether the gate may be closed. */
    public boolean canSend() {
        return mailer.isPresent();
    }

    /**
     * Issues a link and sends it.
     *
     * <p>Failing to send is not swallowed. A page that says "check your inbox" for a mail that
     * was never handed over sends the reader to wait on nothing, and with sign-in gated behind
     * verification that is an account nobody can open.
     */
    @Transactional
    public void sendLink(AppUser user) {
        Mailer send = mailer.orElseThrow(() -> new VerificationUnavailableException());

        Instant now = Instant.now();
        if (tokens.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1)))
                >= MAX_LINKS_PER_HOUR) {
            throw new TooManyVerificationLinksException();
        }

        tokens.spendEveryOutstandingLink(user.getId(), now);

        String token = newToken();
        tokens.save(new EmailVerificationToken(digestOf(token), user.getId(), now.plus(ttl)));

        send.send(user.getEmail(), "Confirm your email for NexusLibrary", body(user, token));
    }

    /**
     * Follows a link.
     *
     * <p>No session is started in exchange. Whoever held the link proved they can read the
     * mailbox, not that they know the password — so they sign in afterwards, which is also the
     * last check that the account being opened is theirs.
     */
    @Transactional
    public void verify(String presentedToken) {
        Instant now = Instant.now();

        EmailVerificationToken link = tokens.findByTokenHash(digestOf(presentedToken))
                .filter(candidate -> candidate.isLiveAt(now))
                .orElseThrow(VerificationLinkExpiredException::new);

        AppUser user = users.findById(link.getUserId())
                .orElseThrow(() -> new AuthenticationFailedException("Account no longer exists."));

        user.verifyEmail(now);
        link.spend(now);
        log.info("Verified the address on account {}", user.getId());
    }

    /**
     * Sends another link for an address, saying nothing about whether there was one to send to.
     *
     * <p>Same reasoning as asking for a reset: an answer that differs between a registered and
     * an unregistered address turns this endpoint into a way to ask who has an account here.
     * An address that is already verified quietly gets nothing.
     */
    @Transactional
    public void resend(String email) {
        users.findByEmail(EmailAddresses.normalise(email))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendLink);
    }

    /** Drops rows whose links have expired. They already admit nothing; this is space, not safety. */
    @Transactional
    public int pruneExpired() {
        return tokens.deleteByExpiresAtBefore(Instant.now());
    }

    private String body(AppUser user, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        return """
               <p>Hello %s,</p>
               <p>Confirm this address to finish setting up your NexusLibrary account.</p>
               <p><a href="%s">Confirm my email</a></p>
               <p>The link works for %d hours. If you did not create an account, ignore this \
               message and nothing happens.</p>
               """
                .formatted(escape(user.getUsername()), link, ttl.toHours());
    }

    /** The username is the reader's own text going into HTML; it is not markup. */
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        // URL-safe and unpadded: the token travels as a query parameter, and '+' or '=' in one
        // is a link that works until something along the way decides to re-encode it.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String digestOf(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every JVM", impossible);
        }
    }
}
