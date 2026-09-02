package dev.nexus.auth;

import java.time.Instant;
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

    private final JwtService jwtService;
    private final RefreshTokenRepository tokens;
    private final AppUserRepository users;

    public RefreshTokenService(JwtService jwtService, RefreshTokenRepository tokens, AppUserRepository users) {
        this.jwtService = jwtService;
        this.tokens = tokens;
        this.users = users;
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
     * <p>Presenting an already retired token is refused and nothing more. Treating it as proof
     * of theft and ending every session the account has would read a race as an attack: two
     * tabs waking together present the same token, and the slower one would sign the account
     * out of everything for doing nothing wrong.
     *
     * <p>The replacement keeps the client the retired token was issued to. A session does not
     * change from a phone's into a browser's by being renewed, and the delivery follows it.
     */
    @Transactional
    public Session renew(String presented) {
        Instant now = Instant.now();

        JwtService.RefreshTokenClaims claims =
                jwtService.readRefreshToken(presented).orElseThrow(RefreshTokenService::expired);

        RefreshToken row = tokens.findByJti(claims.jti())
                // The row carries the account, so the token's own subject is never trusted to
                // name one. They can only disagree if something is very wrong; refuse if so.
                .filter(candidate -> candidate.isLiveAt(now) && candidate.getUserId().equals(claims.userId()))
                .orElseThrow(RefreshTokenService::expired);

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

    private static AuthenticationFailedException expired() {
        return new AuthenticationFailedException("Session expired. Please sign in again.");
    }
}
