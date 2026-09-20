package dev.nexus.auth;

import dev.nexus.auth.dto.ForgotPasswordRequest;
import dev.nexus.auth.dto.ResetPasswordRequest;
import dev.nexus.config.NexusProperties;
import dev.nexus.core.security.TurnstileVerifier;
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
    private final TurnstileVerifier turnstile;
    private final ClientIpResolver clientIp;
    private final int authRequestsPerMinute;

    public PasswordResetController(
            PasswordResetService passwordResets,
            RateLimiter rateLimiter,
            TurnstileVerifier turnstile,
            ClientIpResolver clientIp,
            NexusProperties properties) {
        this.passwordResets = passwordResets;
        this.rateLimiter = rateLimiter;
        this.turnstile = turnstile;
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

        /*
         * Excused for a native client, which has no way to produce a token — the same exemption
         * register makes, and for the same reason. Unlike register there is no account created
         * here to charge a confirmed address for, so what pays for it is the per-account cap in
         * PasswordResetService: five links an hour, whoever is asking and however they claim to
         * be asking. That protects the address on the receiving end, which is the party at risk
         * here, and which the challenge never protected — a person with a browser could always
         * solve it by hand and aim the mail wherever they liked.
         */
        if (request.client() != AuthClient.NATIVE) {
            turnstile.verify(request.turnstileToken(), clientIp.resolve(http));
        }

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
