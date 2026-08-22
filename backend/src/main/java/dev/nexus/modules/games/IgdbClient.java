package dev.nexus.modules.games;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Speaks IGDB's APIcalypse query language: queries are posted as plain text, not JSON.
 */
@Component
public class IgdbClient {

    private static final String GAME_FIELDS =
            "id,name,summary,first_release_date,cover.url,platforms.name,genres.name,total_rating,status";

    private final RestClient restClient;
    private final IgdbAuthClient auth;
    private final IgdbProperties properties;
    private final OutboundRateLimiter rateLimiter;

    public IgdbClient(RestClient.Builder builder, IgdbAuthClient auth, IgdbProperties properties) {
        this.restClient = builder.build();
        this.auth = auth;
        this.properties = properties;
        this.rateLimiter = new OutboundRateLimiter(properties.requestsPerSecond());
    }

    public List<Map<String, Object>> searchGames(String query, int limit) {
        String escaped = query.replace("\"", "\\\"");
        return post("search \"%s\"; fields %s; limit %d;".formatted(escaped, GAME_FIELDS, limit));
    }

    public List<Map<String, Object>> findGameById(String externalId) {
        return post("where id = %s; fields %s; limit 1;".formatted(Long.parseLong(externalId), GAME_FIELDS));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> post(String body) {
        rateLimiter.acquire();

        try {
            return execute(body, true);
        } catch (RestClientException e) {
            throw new IgdbUnavailableException("IGDB request failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> execute(String body, boolean allowRetry) {
        List<Map<String, Object>> response = restClient
                .post()
                .uri(properties.apiBaseUrl() + "/games")
                .header("Client-ID", properties.clientId())
                .header("Authorization", "Bearer " + auth.accessToken())
                .contentType(MediaType.TEXT_PLAIN)
                .body(body)
                .exchange((request, clientResponse) -> {
                    HttpStatusCode status = clientResponse.getStatusCode();
                    if (status.value() == 401 && allowRetry) {
                        return null;
                    }
                    if (status.isError()) {
                        throw new IgdbUnavailableException("IGDB responded with " + status.value());
                    }
                    return (List<Map<String, Object>>) clientResponse.bodyTo(List.class);
                });

        if (response == null) {
            // The token was rejected; drop it and try once with a freshly minted one.
            auth.invalidate();
            return execute(body, false);
        }
        return response;
    }
}
