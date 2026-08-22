package dev.nexus.modules.games;

import java.util.Collection;
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

    static final int MAX_BATCH = 500;

    /** IGDB's external_game_source id for Steam. */
    private static final int STEAM_SOURCE_ID = 1;

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

    /** Bulk variant, so importing a library costs a handful of requests rather than one each. */
    public List<Map<String, Object>> findGamesByIds(Collection<String> externalIds) {
        return post("where id = (%s); fields %s; limit %d;"
                .formatted(numericCsv(externalIds), GAME_FIELDS, MAX_BATCH));
    }

    /**
     * Cross-references Steam appids to IGDB games.
     *
     * <p>Filters on {@code external_game_source}, not the older {@code category} field: IGDB
     * has retired category on these rows, so filtering by it silently matches nothing.
     */
    public List<Map<String, Object>> findGamesBySteamAppIds(Collection<String> appIds) {
        return post(
                "where external_game_source = %d & uid = (%s); fields game,uid; limit %d;"
                        .formatted(STEAM_SOURCE_ID, quotedCsv(appIds), MAX_BATCH),
                "/external_games");
    }

    /** IGDB caps a response at 500 rows, so callers resolve in chunks of that size. */
    public static List<List<String>> partition(Collection<String> values) {
        List<String> all = List.copyOf(values);
        List<List<String>> batches = new java.util.ArrayList<>();
        for (int i = 0; i < all.size(); i += MAX_BATCH) {
            batches.add(all.subList(i, Math.min(all.size(), i + MAX_BATCH)));
        }
        return batches;
    }

    private String numericCsv(Collection<String> values) {
        return values.stream().map(Long::parseLong).map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private String quotedCsv(Collection<String> values) {
        return values.stream()
                .map(v -> "\"" + v.replace("\"", "") + "\"")
                .collect(java.util.stream.Collectors.joining(","));
    }

    private List<Map<String, Object>> post(String body) {
        return post(body, "/games");
    }

    private List<Map<String, Object>> post(String body, String path) {
        rateLimiter.acquire();

        try {
            return execute(body, path, true);
        } catch (RestClientException e) {
            throw new IgdbUnavailableException("IGDB request failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> execute(String body, String path, boolean allowRetry) {
        List<Map<String, Object>> response = restClient
                .post()
                .uri(properties.apiBaseUrl() + path)
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
            return execute(body, path, false);
        }
        return response;
    }
}
