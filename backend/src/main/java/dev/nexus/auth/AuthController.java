package dev.nexus.auth;

import dev.nexus.auth.dto.AuthResponse;
import dev.nexus.auth.dto.LoginRequest;
import dev.nexus.auth.dto.RefreshRequest;
import dev.nexus.auth.dto.RegisterRequest;
import dev.nexus.auth.dto.ResendVerificationRequest;
import dev.nexus.auth.dto.VerifyEmailRequest;
import dev.nexus.auth.dto.UserResponse;
import dev.nexus.config.NexusProperties;
import dev.nexus.core.security.TurnstileVerifier;
import dev.nexus.core.web.ClientIpResolver;
import dev.nexus.core.web.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final JwtService jwtService;
    private final RefreshCookies refreshCookies;
    private final RefreshTokenService refreshTokens;
    private final SessionResponses sessions;
    private final RateLimiter rateLimiter;
    private final TurnstileVerifier turnstile;
    private final ClientIpResolver clientIp;
    private final int authRequestsPerMinute;

    /** Whether this deployment still takes new accounts. Signing in is never affected. */
    private final boolean registrationOpen;
    private final EmailVerificationService verification;
    private final boolean verificationRequired;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            RefreshCookies refreshCookies,
            RefreshTokenService refreshTokens,
            SessionResponses sessions,
            RateLimiter rateLimiter,
            TurnstileVerifier turnstile,
            ClientIpResolver clientIp,
            EmailVerificationService verification,
            NexusProperties properties,
            @org.springframework.beans.factory.annotation.Value("${nexus.verification.required:false}")
                    boolean verificationRequired) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.refreshCookies = refreshCookies;
        this.refreshTokens = refreshTokens;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.turnstile = turnstile;
        this.clientIp = clientIp;
        this.verification = verification;
        this.verificationRequired = verificationRequired;
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

        // Throttle first: the counter is free, and confirming the challenge is an outbound
        // call to Cloudflare that a flood should not get to spend.
        rateLimiter.check("register:" + clientIp.resolve(http), authRequestsPerMinute);

        /*
         * Turnstile is a browser widget, and Cloudflare ships no native SDK — a native client
         * has no way to obtain a token at all. The exemption is therefore given, but it is not
         * given away: `client` is chosen by the caller, so anyone willing to claim NATIVE takes
         * the same path, and what that path costs has to stand on its own.
         *
         * It does. A native sign-up must confirm its address whatever the deployment-wide
         * setting says, so claiming NATIVE buys an account that is inert until a mailbox
         * someone actually owns is read. That is the cost the challenge was imposing, collected
         * differently; the per-address rate limit above is unchanged either way.
         */
        boolean nativeClient = request.client() == AuthClient.NATIVE;
        if (nativeClient) {
            // Refused before the row exists rather than after: an account gated behind a link
            // this deployment cannot send is one nobody could ever open.
            if (!verification.canSend()) {
                throw new VerificationUnavailableException();
            }
        } else {
            turnstile.verify(request.turnstileToken(), clientIp.resolve(http));
        }

        AppUser created = authService.register(request);

        /*
         * With the gate closed, registering hands back no session at all. Issuing one and then
         * refusing the next sign-in would be an account that worked once and then stopped, and
         * the reader would have no way to tell that from something broken.
         *
         * A failure to send is not swallowed here for the same reason: "check your inbox" for a
         * mail that never left is an account nobody can ever open.
         */
        if (verificationRequired || nativeClient) {
            verification.sendLink(created);
            return ResponseEntity.status(HttpStatus.ACCEPTED).<AuthResponse>build();
        }

        // Gate open: the link still goes out where it can, but nothing waits on it. A mailer
        // that is missing or failing must not stop an account being created.
        try {
            if (verification.canSend()) {
                verification.sendLink(created);
            }
        } catch (RuntimeException e) {
            log.warn("Could not send a verification link for a new account", e);
        }

        return sessions.issue(refreshTokens.begin(created, request.client()), HttpStatus.CREATED);
    }

    /**
     * Follows a confirmation link.
     *
     * <p>Public, because nobody can be signed in yet — that is the whole point of the gate.
     * The token in the body rather than the query string: a link's query travels in the
     * browser's history and in any referrer the page sends on.
     */
    @PostMapping("/verify-email")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request, HttpServletRequest http) {
        rateLimiter.check("verify:" + clientIp.resolve(http), authRequestsPerMinute);
        verification.verify(request.token());
    }

    /**
     * Sends another link. Answers the same whether or not there was an account to send to, so
     * there is nothing here to tell the caller which it was.
     */
    @PostMapping("/verify-email/resend")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(
            @Valid @RequestBody ResendVerificationRequest request, HttpServletRequest http) {
        rateLimiter.check("verify-resend:" + clientIp.resolve(http), authRequestsPerMinute);
        verification.resend(request.email());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimiter.check("login:" + clientIp.resolve(http), authRequestsPerMinute);
        return sessions.issue(
                refreshTokens.begin(authService.authenticate(request), request.client()), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody(required = false) RefreshRequest request, HttpServletRequest http) {
        // Unauthenticated, and every call parses a signed token and then reads its row. Forging
        // one is not the threat the limit answers; asking for that work for free is.
        rateLimiter.check("refresh:" + clientIp.resolve(http), authRequestsPerMinute);
        return sessions.issue(refreshTokens.renew(presentedToken(request, http)), HttpStatus.OK);
    }

    /**
     * Ends this session on the server as well as in the client, which clearing the cookie
     * never did: the token it held stayed valid until it expired on its own.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody(required = false) RefreshRequest request, HttpServletRequest http) {
        rateLimiter.check("logout:" + clientIp.resolve(http), authRequestsPerMinute);
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
