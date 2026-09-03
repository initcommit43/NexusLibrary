package dev.nexus.auth;

import dev.nexus.auth.dto.AuthResponse;
import dev.nexus.auth.dto.LoginRequest;
import dev.nexus.auth.dto.RefreshRequest;
import dev.nexus.auth.dto.RegisterRequest;
import dev.nexus.auth.dto.UserResponse;
import dev.nexus.config.NexusProperties;
import dev.nexus.core.web.ClientIpResolver;
import dev.nexus.core.web.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final RefreshCookies refreshCookies;
    private final RefreshTokenService refreshTokens;
    private final SessionResponses sessions;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIp;
    private final int authRequestsPerMinute;

    /** Whether this deployment still takes new accounts. Signing in is never affected. */
    private final boolean registrationOpen;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            RefreshCookies refreshCookies,
            RefreshTokenService refreshTokens,
            SessionResponses sessions,
            RateLimiter rateLimiter,
            ClientIpResolver clientIp,
            NexusProperties properties) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.refreshCookies = refreshCookies;
        this.refreshTokens = refreshTokens;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.clientIp = clientIp;
        this.authRequestsPerMinute = properties.rateLimit().authRequestsPerMinute();
        this.registrationOpen = properties.security().registrationOpen();
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        // Checked before the rate limiter has anything to say: a closed door is not a
        // question about how often it is being knocked on.
        if (!registrationOpen) {
            throw new RegistrationClosedException();
        }

        rateLimiter.check("register:" + clientIp.resolve(http), authRequestsPerMinute);
        return sessions.issue(
                refreshTokens.begin(authService.register(request), request.client()), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimiter.check("login:" + clientIp.resolve(http), authRequestsPerMinute);
        return sessions.issue(
                refreshTokens.begin(authService.authenticate(request), request.client()), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody(required = false) RefreshRequest request, HttpServletRequest http) {
        return sessions.issue(refreshTokens.renew(presentedToken(request, http)), HttpStatus.OK);
    }

    /**
     * Ends this session on the server as well as in the client, which clearing the cookie
     * never did: the token it held stayed valid until it expired on its own.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request, HttpServletRequest http) {
        readToken(request, http).ifPresent(refreshTokens::end);
        return sessions.cleared();
    }

    /**
     * Ends every session the account has, wherever it is signed in. What a lost phone needs,
     * and the only answer to a refresh token that has left the device holding it.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutEverywhere(@AuthenticationPrincipal CurrentUser currentUser) {
        refreshTokens.endEverySession(currentUser.id());
        return sessions.cleared();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
        return UserResponse.from(authService.requireById(currentUser.id()));
    }

    /** A browser presents its cookie; a native client, which has none, sends the token. */
    private Optional<String> readToken(RefreshRequest request, HttpServletRequest http) {
        return refreshCookies
                .read(http)
                .or(() -> Optional.ofNullable(request)
                        .map(RefreshRequest::refreshToken)
                        .filter(token -> !token.isBlank()));
    }

    private String presentedToken(RefreshRequest request, HttpServletRequest http) {
        return readToken(request, http)
                .orElseThrow(() -> new AuthenticationFailedException("Session expired. Please sign in again."));
    }

}
