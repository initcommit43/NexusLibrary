package dev.nexus.modules.film;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Trakt's authorization code grant.
 *
 * <p>Closest to AniList's of the three: a plain code exchange with a client secret and no
 * PKCE, so nothing has to be remembered between sending the reader away and their coming
 * back — no pending state, no eviction. Where it follows MAL instead is lifetime: a Trakt
 * token lasts three months and comes with a refresh token, so {@link #refresh} is a path
 * that will actually be walked rather than dead code.
 *
 * <p>The token endpoint takes JSON, not the form encoding MAL's insists on.
 */
@Service
public class TraktOAuthService {

    /**
     * Where Trakt returns the browser. Named here rather than at each call site because a
     * refresh has to present the very same value the code exchange did — Trakt checks it.
     */
    public static final String CALLBACK_PATH = "/settings/trakt/callback";

    /** Fallback when Trakt omits expires_in; its documented token lifetime is 90 days. */
    private static final Duration DEFAULT_LIFETIME = Duration.ofDays(90);

    public record Connection(String externalUserId, String accessToken, String refreshToken, Instant expiresAt) {}

    /** What a refresh yields; the caller persists it onto the account. */
    public record Tokens(String accessToken, String refreshToken, Instant expiresAt) {}

    private final RestClient restClient;
    private final TraktProperties properties;

    public TraktOAuthService(RestClient.Builder builder, TraktProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    /** The address to send the reader to. Stateless: Trakt asks for no challenge. */
    public String authorizationUrl(String redirectUri) {
        requireConfigured();

        return "%s?response_type=code&client_id=%s&redirect_uri=%s"
                .formatted(properties.authorizeUrl(), encode(properties.clientId()), encode(redirectUri));
    }

    /** Swaps the code for tokens and asks Trakt whose account they are. */
    public Connection exchangeCode(String code, String redirectUri) {
        requireConfigured();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("client_id", properties.clientId());
        body.put("client_secret", properties.clientSecret());
        body.put("redirect_uri", redirectUri);
        body.put("grant_type", "authorization_code");

        Tokens tokens = tokens(postJson(body), null);
        return new Connection(username(tokens.accessToken()), tokens.accessToken(), tokens.refreshToken(), tokens.expiresAt());
    }

    /**
     * Trades the refresh token for a fresh pair. Trakt rotates the refresh token on every
     * exchange, so the new one has to be persisted or the link is one refresh from dead.
     */
    public Tokens refresh(String refreshToken, String redirectUri) {
        requireConfigured();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("refresh_token", refreshToken);
        body.put("client_id", properties.clientId());
        body.put("client_secret", properties.clientSecret());
        body.put("redirect_uri", redirectUri);
        body.put("grant_type", "refresh_token");

        return tokens(postJson(body), refreshToken);
    }

    private Tokens tokens(Map<String, Object> token, String previousRefreshToken) {
        String accessToken = string(token.get("access_token"));
        if (accessToken == null) {
            throw new TraktUnavailableException("Trakt returned no access token");
        }

        Instant expiresAt = token.get("expires_in") instanceof Number seconds
                ? Instant.now().plusSeconds(seconds.longValue())
                : Instant.now().plus(DEFAULT_LIFETIME);

        String refreshToken = string(token.get("refresh_token"));
        return new Tokens(accessToken, refreshToken != null ? refreshToken : previousRefreshToken, expiresAt);
    }

    /** Who the token belongs to: the username a person recognises, from {@code users/me}. */
    @SuppressWarnings("unchecked")
    private String username(String accessToken) {
        try {
            Map<String, Object> me = restClient
                    .get()
                    .uri(properties.apiBaseUrl() + "/users/me")
                    .headers(headers -> {
                        headers.set("Authorization", "Bearer " + accessToken);
                        headers.set("trakt-api-version", "2");
                        headers.set("trakt-api-key", properties.clientId());
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new TraktUnavailableException(
                                    "Trakt responded with " + response.getStatusCode().value());
                        }
                        return (Map<String, Object>) response.bodyTo(Map.class);
                    });

            String name = me == null ? null : string(me.get("username"));
            if (name == null) {
                throw new TraktUnavailableException("Trakt did not identify the account");
            }
            return name;
        } catch (RestClientException e) {
            throw new TraktUnavailableException("Trakt request failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postJson(Map<String, String> body) {
        try {
            Map<String, Object> response = restClient
                    .post()
                    .uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, res) -> {
                        HttpStatusCode status = res.getStatusCode();
                        if (status.isError()) {
                            throw new TraktUnavailableException("Trakt responded with " + status.value());
                        }
                        return (Map<String, Object>) res.bodyTo(Map.class);
                    });

            return response == null ? Map.of() : response;
        } catch (RestClientException e) {
            throw new TraktUnavailableException("Trakt request failed", e);
        }
    }

    private void requireConfigured() {
        if (!properties.canConnectAccounts()) {
            throw new TraktNotConfiguredException();
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
