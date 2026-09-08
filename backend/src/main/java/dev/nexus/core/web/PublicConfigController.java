package dev.nexus.core.web;

import dev.nexus.core.security.TurnstileProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The handful of settings the browser needs before anyone has signed in.
 *
 * <p>Public by necessity: what it answers is what the sign-in and sign-up screens are built
 * from, and those are reached without a token. Nothing secret belongs here — the Turnstile
 * site key is published to every visitor by design, which is what makes it the site key
 * rather than the secret one. Serving it rather than baking it into the bundle keeps a key
 * rotation an env-var edit instead of an image rebuild.
 */
@RestController
public class PublicConfigController {

    /** @param turnstileSiteKey empty when the challenge is switched off, which dev is. */
    public record PublicConfig(String turnstileSiteKey) {}

    private final TurnstileProperties turnstile;

    public PublicConfigController(TurnstileProperties turnstile) {
        this.turnstile = turnstile;
    }

    @GetMapping("/config")
    public PublicConfig config() {
        return new PublicConfig(turnstile.siteKey() == null ? "" : turnstile.siteKey());
    }
}
