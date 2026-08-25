package dev.nexus.modules.film;

import dev.nexus.core.web.OutboundRateLimiter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Speaks TMDB's REST API. Every call is a GET carrying the v4 read token as a bearer.
 *
 * <p>TMDB has no bulk endpoint: there is no way to ask for two hundred films in one request,
 * so an import pays one call per title. That is why {@link TmdbMetadataAdapter} leaves the
 * one-at-a-time default in place and why the rate limiter matters more here than for a
 * source that answers in batches.
 */
@Component
public class TmdbClient {

    private final RestClient restClient;
    private final TmdbProperties properties;
    private final OutboundRateLimiter rateLimiter;

    public TmdbClient(RestClient.Builder builder, TmdbProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
        this.rateLimiter = new OutboundRateLimiter(properties.requestsPerSecond());
    }

    /** TMDB pages at 20 results; anything past the first page is noise for a search box. */
    public List<Map<String, Object>> search(TmdbKind kind, String query, int limit) {
        Map<String, Object> body = get("/search/{kind}?query={query}&include_adult=false&page=1", kind.path(), query)
                .orElse(Map.of());
        return results(body).stream().limit(limit).toList();
    }

    /** Empty when TMDB has no such title — a deleted id, or one that was never ours. */
    public Optional<Map<String, Object>> findById(TmdbKind kind, String tmdbId) {
        return get("/{kind}/{id}", kind.path(), tmdbId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> results(Map<String, Object> body) {
        return body.get("results") instanceof List<?> results
                ? results.stream()
                        .filter(Map.class::isInstance)
                        .map(result -> (Map<String, Object>) result)
                        .toList()
                : List.of();
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> get(String path, Object... uriVariables) {
        if (!properties.canSearch()) {
            throw new TmdbUnavailableException("TMDB credentials are not configured; set TMDB_ACCESS_TOKEN");
        }
        rateLimiter.acquire();

        try {
            return Optional.ofNullable(restClient
                    .get()
                    .uri(properties.apiBaseUrl() + path, uriVariables)
                    .header("Authorization", "Bearer " + properties.accessToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.value() == 404) {
                            // Not an outage: the title is gone, and the caller wants an empty.
                            return null;
                        }
                        if (status.isError()) {
                            throw new TmdbUnavailableException(
                                    "TMDB responded with " + status.value(), statusMessage(response));
                        }
                        return (Map<String, Object>) response.bodyTo(Map.class);
                    }));
        } catch (RestClientException e) {
            throw new TmdbUnavailableException("TMDB request failed", e);
        }
    }

    /**
     * TMDB explains its own refusals — "Invalid API key: You must be granted a valid key" —
     * and that sentence is worth more to a reader than the status code it came with.
     */
    private String statusMessage(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            Map<?, ?> body = response.bodyTo(Map.class);
            return body != null && body.get("status_message") != null
                    ? body.get("status_message").toString()
                    : null;
        } catch (RestClientException | IllegalStateException e) {
            // An error page that is not TMDB's JSON is the gateway talking, not TMDB.
            return null;
        }
    }
}
