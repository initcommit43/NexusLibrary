package dev.nexus.auth;

import dev.nexus.auth.dto.AuthResponse;
import dev.nexus.auth.dto.LoginRequest;
import dev.nexus.auth.dto.RegisterRequest;
import dev.nexus.auth.dto.UserResponse;
import dev.nexus.config.NexusProperties;
import dev.nexus.core.web.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
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
    private final RateLimiter rateLimiter;
    private final int authRequestsPerMinute;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            RefreshCookies refreshCookies,
            RateLimiter rateLimiter,
            NexusProperties properties) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.refreshCookies = refreshCookies;
        this.rateLimiter = rateLimiter;
        this.authRequestsPerMinute = properties.rateLimit().authRequestsPerMinute();
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        rateLimiter.check("register:" + http.getRemoteAddr(), authRequestsPerMinute);
        return sessionResponse(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimiter.check("login:" + http.getRemoteAddr(), authRequestsPerMinute);
        return sessionResponse(authService.authenticate(request), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest http) {
        AppUser user = refreshCookies
                .read(http)
                .flatMap(jwtService::readRefreshToken)
                .map(authService::requireById)
                .orElseThrow(() -> new AuthenticationFailedException("Session expired. Please sign in again."));

        return sessionResponse(user, HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
        return UserResponse.from(authService.requireById(currentUser.id()));
    }

    /**
     * Rotates the refresh cookie on every issue, so a token captured earlier stops being
     * the one the browser will present next.
     */
    private ResponseEntity<AuthResponse> sessionResponse(AppUser user, HttpStatus status) {
        ResponseCookie cookie = refreshCookies.issue(jwtService.issueRefreshToken(user), jwtService.refreshTtl());

        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(jwtService.issueAccessToken(user), UserResponse.from(user)));
    }
}
