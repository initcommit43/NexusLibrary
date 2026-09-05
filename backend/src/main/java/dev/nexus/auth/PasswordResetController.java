package dev.nexus.auth;

import dev.nexus.auth.dto.ForgotPasswordRequest;
import dev.nexus.auth.dto.ResetPasswordRequest;
import dev.nexus.config.NexusProperties;
import dev.nexus.core.web.ClientIpResolver;
import dev.nexus.core.web.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Getting back in without the password. Public by necessity — someone who cannot sign in
 * cannot present a token first — so both routes are throttled per address the same way
 * signing in is.
 */
@RestController
@RequestMapping("/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResets;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIp;
    private final int authRequestsPerMinute;

    public PasswordResetController(
            PasswordResetService passwordResets,
            RateLimiter rateLimiter,
            ClientIpResolver clientIp,
            NexusProperties properties) {
        this.passwordResets = passwordResets;
        this.rateLimiter = rateLimiter;
        this.clientIp = clientIp;
        this.authRequestsPerMinute = properties.rateLimit().authRequestsPerMinute();
    }

    /**
     * Always 204, whether or not the address has an account. The throttle is what keeps this
     * from being walked through a list of addresses to see which ones get mail.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> requestLink(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        rateLimiter.check("forgot-password:" + clientIp.resolve(http), authRequestsPerMinute);
        passwordResets.requestLink(request.email());

        return ResponseEntity.noContent().build();
    }

    /**
     * Throttled as well as the request that issues links: this one takes a token, and an
     * endpoint that will check as many as it is given is one that can be guessed at.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        rateLimiter.check("reset-password:" + clientIp.resolve(http), authRequestsPerMinute);
        passwordResets.reset(request.token(), request.password());

        return ResponseEntity.noContent().build();
    }
}
