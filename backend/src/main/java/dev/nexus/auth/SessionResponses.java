package dev.nexus.auth;

import dev.nexus.auth.agreements.AgreementPlatform;
import dev.nexus.auth.agreements.AgreementResponse;
import dev.nexus.auth.agreements.AgreementService;
import dev.nexus.auth.dto.AuthResponse;
import dev.nexus.auth.dto.UserResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Hands a session back the only way its client can keep it.
 *
 * <p>A browser gets a cookie it cannot read, so a script injected into the page cannot read
 * it either. A native client gets the value, because a keychain is not somewhere a server
 * can write. Shared rather than written twice: signing in and changing a password both start
 * a session, and a second copy of this is where the two would drift apart.
 */
@Component
public class SessionResponses {

    private final JwtService jwtService;
    private final RefreshCookies refreshCookies;
    private final AgreementService agreements;

    public SessionResponses(JwtService jwtService, RefreshCookies refreshCookies, AgreementService agreements) {
        this.jwtService = jwtService;
        this.refreshCookies = refreshCookies;
        this.agreements = agreements;
    }

    public ResponseEntity<AuthResponse> issue(RefreshTokenService.Session session, HttpStatus status) {
        AppUser user = session.user();
        String accessToken = jwtService.issueAccessToken(user);
        UserResponse body = UserResponse.from(user);

        // Every route that hands back a session answers the same question, so a client never
        // has to know which of them tells it and which does not — signing in, registering,
        // refreshing and changing a password all carry the outstanding set. Scoped to the
        // platform this session belongs to: accepting in a browser leaves the phone asking.
        List<AgreementResponse> outstanding =
                agreements.outstanding(user.getId(), AgreementPlatform.of(session.client()));

        if (session.client() == AuthClient.NATIVE) {
            return ResponseEntity.status(status)
                    .body(AuthResponse.forNativeClient(accessToken, session.refreshToken(), body, outstanding));
        }

        ResponseCookie cookie = refreshCookies.issue(session.refreshToken(), jwtService.refreshTtl());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(AuthResponse.forBrowser(accessToken, body, outstanding));
    }

    /** Ends a session in the client as well as on the server, for a browser's part of it. */
    public ResponseEntity<Void> cleared() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }
}
