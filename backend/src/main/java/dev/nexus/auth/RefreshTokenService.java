package dev.nexus.auth;

import dev.nexus.config.NexusProperties;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The lifetime of a session, from the token that starts it to the one that ends it.
 *
 * <p>Every path in and out goes through here so that "signed out" means the same thing to a
 * browser and to a phone: the row is gone, whatever the client did with its copy.
 */
@Service
public class RefreshTokenService {

    /**
     * A session that has just started, and the token whoever asked for it must now keep.
     * The client travels with it because it decides how that token is handed back.
     */
    public record Session(AppUser user, String refreshToken, AuthClient client) {}

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final JwtService jwtService;
    private final RefreshTokenRepository tokens;
    private final AppUserRepository users;
    private final Duration reuseGrace;

    public RefreshTokenService(
            JwtService jwtService,
            RefreshTokenRepository tokens,
            AppUserRepository users,
            NexusProperties properties) {
        this.jwtService = jwtService;
        this.tokens = tokens;
        this.users = users;
        this.reuseGrace = Duration.ofSeconds(properties.jwt().refreshReuseGraceSeconds());
    }

    @Transactional
    public Session begin(AppUser user, AuthClient client) {
        JwtService.IssuedRefreshToken issued = jwtService.issueRefreshToken(user);
        tokens.save(new RefreshToken(issued.jti(), user.getId(), client, issued.expiresAt()));

        return new Session(user, issued.token(), client);
    }

    /**
     * Trades a refresh token for its successor and retires the one presented, so a copy taken
     * from a browser or a backup stops working the moment its owner next refreshes.
     *
     * <p>A token that was already retired is judged by how long ago that happened. Inside the
     * grace window it is two clients refreshing at once — a phone waking two requests together,
     * or two tabs — and the loser is refused and nothing more, which costs it nothing because
     * the winner's token is the one its cookie or keychain now holds. Past that window nobody
     * holds a retired token innocently: it was kept after signing out, or it was taken. Every
     * session on the account ends, because rotation means the thief's copy working is exactly
     * what stops the owner's from working, and there is no telling from here which is which.
     *
     * <p>The replacement keeps the client the retired token was issued to. A session does not
     * change from a phone's into a browser's by being renewed, and the delivery follows it.
     */
    // Ending the other sessions is a write that has to survive the refusal thrown right after
    // it, which a rollback would undo.
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public Session renew(String presented) {
        Instant now = Instant.now();

        JwtService.RefreshTokenClaims claims =
                jwtService.readRefreshToken(presented).orElseThrow(RefreshTokenService::expired);

        RefreshToken row = tokens.findByJti(claims.jti()).orElseThrow(RefreshTokenService::expired);

        // The row carries the account, so the token's own subject is never trusted to name
        // one. They can only disagree if something is very wrong; refuse if so.
        if (!row.getUserId().equals(claims.userId())) {
            throw expired();
        }
        if (row.getRevokedAt() != null) {
            throw retired(row, now);
        }
        // Expired on its own, which is nobody's fault and no cause to end anything else.
        if (!row.getExpiresAt().isAfter(now)) {
            throw expired();
        }

        row.revoke(now);

        AppUser user = users.findById(row.getUserId())
                .orElseThrow(() -> new AuthenticationFailedException("Account no longer exists."));

        return begin(user, row.getClient());
    }

    /** Ends the one session the token belongs to. Every other stays live. */
    @Transactional
    public void end(String presented) {
        jwtService
                .readRefreshToken(presented)
                .flatMap(claims -> tokens.findByJti(claims.jti()))
                .ifPresent(row -> row.revoke(Instant.now()));
    }

    /** Ends every session an account has, which is what a lost device needs. */
    @Transactional
    public int endEverySession(Long userId) {
        return tokens.revokeEveryLiveToken(userId, Instant.now());
    }

    /**
     * Drops rows whose tokens have expired on their own. They already admit nothing — the
     * token is checked for expiry as well as the row — so this is housekeeping, not security.
     */
    @Transactional
    public int pruneExpired() {
        return tokens.deleteByExpiresAtBefore(Instant.now());
    }

    /**
     * Answers a retired token, and decides on the way out whether its presentation was a race
     * or a theft. The caller is told the same thing either way — which of the two it was is
     * not something to report to whoever is asking.
     */
    private AuthenticationFailedException retired(RefreshToken row, Instant now) {
        if (row.getRevokedAt().plus(reuseGrace).isBefore(now)) {
            int ended = endEverySession(row.getUserId());
            // No account named: the point is that it happened and how far outside the window,
            // and a log is not a place to put who it happened to.
            log.warn(
                    "A refresh token was presented {} after being retired, past the {} allowed for "
                            + "two clients refreshing at once. Ended {} live session(s) on that account.",
                    Duration.between(row.getRevokedAt(), now),
                    reuseGrace,
                    ended);
        }
        return expired();
    }

    private static AuthenticationFailedException expired() {
        return new AuthenticationFailedException("Session expired. Please sign in again.");
    }
}
