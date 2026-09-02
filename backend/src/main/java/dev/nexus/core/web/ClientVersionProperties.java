package dev.nexus.core.web;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The oldest app build this deployment still answers.
 *
 * <p>One value rather than one per platform: the app is a single Flutter codebase with a
 * single version, so iOS and Android ship the same number. A platform that needed its own
 * would be a second property, not a different shape.
 */
@Validated
@ConfigurationProperties(prefix = "nexus.client-version")
public record ClientVersionProperties(@NotBlank String minimum) {}
