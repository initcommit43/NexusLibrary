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

    /** TMDB refuses a page number above this, whatever total_pages says. */
    private static final int MAX_PAGE = 500;

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

    /**
     * One page of a browse shelf. Every shelf is one of TMDB's own curated lists, which is
     * why the path varies rather than a sort parameter: TMDB exposes "popular" and "top
     * rated" as endpoints, not as orderings of a general query.
     *
     * @param path the list's path under the kind, such as {@code popular} or {@code top_rated}
     */
    public Map<String, Object> browse(TmdbKind kind, String path, int page) {
        return get("/{kind}/{path}?page={page}", kind.path(), path, page).orElse(Map.of());
    }

    /**
     * Trending sits outside the per-kind lists: it is its own endpoint, addressed by the
     * media type and a window rather than by a list name.
     */
    public Map<String, Object> trending(TmdbKind kind, String window, int page) {
        return get("/trending/{kind}/{window}?page={page}", kind.path(), window, page)
                .orElse(Map.of());
    }

    /** The rows of a paged list response, whatever endpoint produced it. */
    public List<Map<String, Object>> resultsOf(Map<String, Object> body) {
        return results(body);
    }

    /**
     * Whether a page has another behind it. TMDB reports a total, and also caps paging at 500
     * pages however many it claims — asking past that is an error rather than an empty page.
     */
    public boolean hasMorePages(Map<String, Object> body, int page) {
        int totalPages = body.get("total_pages") instanceof Number total ? total.intValue() : 0;
        return page < Math.min(totalPages, MAX_PAGE);
    }

    /** Empty when TMDB has no such title — a deleted id, or one that was never ours. */
    public Optional<Map<String, Object>> findById(TmdbKind kind, String tmdbId) {
        return get("/{kind}/{id}", kind.path(), tmdbId);
    }

    /**
     * The TMDB id behind an IMDb one, for the kind asked for. TMDB indexes both, which is
     * what lets a library whose provider only knew an IMDb id still land on a canonical.
     *
     * <p>Costs one call per lookup, so it belongs in a resolver fallback rather than on the
     * main path — a whole library resolved this way would be a request per title.
     */
    public Optional<String> findIdByImdbId(TmdbKind kind, String imdbId) {
        Map<String, Object> body = get("/find/{imdbId}?external_source=imdb_id", imdbId).orElse(Map.of());
        String key = kind == TmdbKind.MOVIE ? "movie_results" : "tv_results";

        return results(body, key).stream()
                .map(result -> result.get("id"))
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> results(Map<String, Object> body) {
        return results(body, "results");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> results(Map<String, Object> body, String key) {
        return body.get(key) instanceof List<?> results
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
