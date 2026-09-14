package dev.nexus.core.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The shared password in front of the whole deployment while it is still in development.
 *
 * <p>Optional. No password means no gate, which is what a local run and the test suite want; a
 * deployment that is not ready to be found sets one.
 */
@ConfigurationProperties(prefix = "nexus.site-gate")
public record SiteGateProperties(String password) {}
