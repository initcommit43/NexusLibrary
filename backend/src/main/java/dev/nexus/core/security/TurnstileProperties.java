package dev.nexus.core.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cloudflare Turnstile, which stands in front of the forms anyone can reach without an
 * account. Keys are deliberately not required at startup, matching the module credentials: a
 * local run has none and must still be able to register. Leaving them unset on a public
 * deployment removes the check rather than breaking it, so {@link TurnstileVerifier} says so
 * on boot instead of failing quietly.
 */
@Validated
@ConfigurationProperties(prefix = "nexus.turnstile")
public record TurnstileProperties(String siteKey, String secretKey, String verifyUrl) {}
