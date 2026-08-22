package dev.nexus.modules.games;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * One application-wide key, like IGDB's. Steam OpenID authenticates a visitor but issues
 * no per-user token, so every library pull uses this key plus the user's SteamID.
 *
 * <p>Optional at startup: a missing key disables Steam import, not the whole application.
 */
@ConfigurationProperties(prefix = "nexus.steam")
public record SteamProperties(String apiKey, String apiBaseUrl, String openIdEndpoint) {}
