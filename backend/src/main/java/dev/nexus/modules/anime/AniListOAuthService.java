package dev.nexus.modules.anime;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * AniList's authorization code grant: send the reader to AniList, take the code they come
 * back with, and swap it for a token server-side.
 */
@Service
public class AniListOAuthService {

    /** Who the token belongs to. Stored so the UI can name the connected account. */
    private static final String VIEWER_QUERY = "query { Viewer { id name } }";

    /** AniList issues year-long tokens and has no meaningful refresh flow. */
    private static final Duration DEFAULT_LIFETIME = Duration.ofDays(365);

    public record Connection(String externalUserId, String accessToken, String refreshToken, Instant expiresAt) {}

    private final RestClient restClient;
    private final AniListProperties properties;

    public AniListOAuthService(RestClient.Builder builder, AniListProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    /**
     * The address to send the reader to. The redirect is built from configuration rather
     * than taken from the caller, so this cannot be used to bounce someone elsewhere.
     */
    public String authorizationUrl(String redirectUri) {
        requireConfigured();
        return "%s?client_id=%s&redirect_uri=%s&response_type=code"
                .formatted(properties.authorizeUrl(), encode(properties.clientId()), encode(redirectUri));
    }

    /**
     * Swaps the code for a token and asks AniList who it belongs to.
     *
     * <p>The exchange carries the client secret, so it happens here and never in the
     * browser: a code that reached the wrong person is useless without it.
     */
    public Connection exchangeCode(String code, String redirectUri) {
        requireConfigured();

        Map<String, Object> token = post(
                properties.tokenUrl(),
                Map.of(
                        "grant_type", "authorization_code",
                        "client_id", properties.clientId(),
                        "client_secret", properties.clientSecret(),
                        "redirect_uri", redirectUri,
                        "code", code),
                null);

        String accessToken = string(token.get("access_token"));
        if (accessToken == null) {
            throw new AniListUnavailableException("AniList returned no access token");
        }

        Instant expiresAt = token.get("expires_in") instanceof Number seconds
                ? Instant.now().plusSeconds(seconds.longValue())
                : Instant.now().plus(DEFAULT_LIFETIME);

        return new Connection(viewerId(accessToken), accessToken, string(token.get("refresh_token")), expiresAt);
    }

    @SuppressWarnings("unchecked")
    private String viewerId(String accessToken) {
        Map<String, Object> body =
                post(properties.apiUrl(), Map.of("query", VIEWER_QUERY, "variables", Map.of()), accessToken);

        if (!(body.get("data") instanceof Map<?, ?> data) || !(data.get("Viewer") instanceof Map<?, ?> viewer)) {
            throw new AniListUnavailableException("AniList did not identify the account");
        }

        // The name is what a person recognises; the numeric id means nothing to them.
        Map<String, Object> found = (Map<String, Object>) viewer;
        String name = string(found.get("name"));
        return name != null ? name : string(found.get("id"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, Map<String, Object> body, String bearerToken) {
        try {
            RestClient.RequestBodySpec request =
                    restClient.post().uri(url).header("Accept", "application/json");
            if (bearerToken != null) {
                request = request.header("Authorization", "Bearer " + bearerToken);
            }

            Map<String, Object> response = request.body(body).exchange((req, res) -> {
                HttpStatusCode status = res.getStatusCode();
                if (status.isError()) {
                    throw new AniListUnavailableException("AniList responded with " + status.value());
                }
                return (Map<String, Object>) res.bodyTo(Map.class);
            });

            return response == null ? Map.of() : response;
        } catch (RestClientException e) {
            throw new AniListUnavailableException("AniList request failed", e);
        }
    }

    private void requireConfigured() {
        if (!properties.canConnectAccounts()) {
            throw new AniListNotConfiguredException();
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
