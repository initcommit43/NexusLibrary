package dev.nexus.modules.film;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.web.OutboundRateLimiter;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Speaks Simkl's API — the read-only slice of it, as the reader themselves.
 *
 * <p>{@code /sync/all-items} answers with a whole library in one response, so an import
 * costs two calls however much someone has watched. Anime is deliberately not among them:
 * Simkl tracks it, but the anime module already owns that shelf on AniList canonicals, and
 * importing it here would file the same titles a second time under TMDB.
 *
 * <p>Nothing refreshes a token here, unlike the MyAnimeList client. A Simkl token lives
 * until the reader revokes the app, so a refusal means revoked rather than stale.
 */
@Component
public class SimklClient {

    /**
     * {@code extended=full} rather than the default: it is the form documented to carry the
     * external ids, and without a TMDB id every row would land in the unmatched report.
     */
    private static final String MOVIES = "/sync/all-items/movies?extended=full";
    private static final String SHOWS = "/sync/all-items/shows?extended=full";

    private final RestClient restClient;
    private final SimklProperties properties;
    private final OutboundRateLimiter rateLimiter;

    public SimklClient(RestClient.Builder builder, SimklProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
        this.rateLimiter = new OutboundRateLimiter(properties.requestsPerSecond());
    }

    /** The films on someone's shelf, whatever state each is in. */
    public List<Map<String, Object>> movies(ExternalAccount account) {
        return get(account, MOVIES, "movies");
    }

    public List<Map<String, Object>> shows(ExternalAccount account) {
        return get(account, SHOWS, "shows");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> get(ExternalAccount account, String path, String key) {
        if (!properties.canConnectAccounts()) {
            throw new SimklNotConfiguredException();
        }
        rateLimiter.acquire();

        try {
            Map<String, Object> body = restClient
                    .get()
                    .uri(properties.apiBaseUrl() + path + "&" + properties.identifyingParams())
                    .headers(headers -> {
                        headers.set("Authorization", "Bearer " + account.getAccessToken());
                        headers.set("User-Agent", properties.userAgent());
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.value() == 401 || status.value() == 403) {
                            // Revoked, not expired: no refresh exists that could mend this.
                            throw new SimklReconnectRequiredException();
                        }
                        if (status.isError()) {
                            throw new SimklUnavailableException("Simkl responded with " + status.value());
                        }
                        return (Map<String, Object>) response.bodyTo(Map.class);
                    });

            // An empty library answers with {}, not with an empty list under the key.
            if (body == null || !(body.get(key) instanceof List<?> rows)) {
                return List.of();
            }
            return rows.stream()
                    .filter(Map.class::isInstance)
                    .map(row -> (Map<String, Object>) row)
                    .toList();
        } catch (RestClientException e) {
            throw new SimklUnavailableException("Simkl request failed", e);
        }
    }
}
