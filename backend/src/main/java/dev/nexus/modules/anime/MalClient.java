package dev.nexus.modules.anime;

import dev.nexus.core.web.OutboundRateLimiter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Speaks MAL's v2 REST API — the read-only slice of it.
 *
 * <p>Everything here works with the application's client id in a header; per-user OAuth is
 * what write-back and private lists would need, and this import does neither. The field
 * sets ask for exactly what the resolver's matching needs — alternative titles and the
 * episode, chapter and volume counts — since id, title and picture are all MAL volunteers
 * unprompted.
 */
@Component
public class MalClient {

    private static final Logger log = LoggerFactory.getLogger(MalClient.class);

    /** MAL caps list pages at 100 rows. */
    static final int PAGE_SIZE = 100;

    private static final int MAX_ATTEMPTS = 3;

    static final String ANIME_FIELDS = "alternative_titles,num_episodes,list_status{start_date,finish_date}";
    static final String MANGA_FIELDS =
            "alternative_titles,num_chapters,num_volumes,list_status{start_date,finish_date}";

    private final RestClient restClient;
    private final MalProperties properties;
    private final OutboundRateLimiter rateLimiter;

    public MalClient(RestClient.Builder builder, MalProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
        this.rateLimiter = new OutboundRateLimiter(properties.requestsPerSecond());
    }

    /**
     * The cheapest request that proves a username right: one row of their anime list. An
     * empty list is still a 200 — existence is the question, not taste.
     */
    public void probeUser(String username) {
        get(listUri(username, "animelist", "", 1, 0));
    }

    /** Every row of the user's anime list, page after page until MAL stops offering more. */
    public List<Map<String, Object>> fetchAnimeList(String username) {
        return fetchWholeList(username, "animelist", ANIME_FIELDS);
    }

    public List<Map<String, Object>> fetchMangaList(String username) {
        return fetchWholeList(username, "mangalist", MANGA_FIELDS);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchWholeList(String username, String list, String fields) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int offset = 0;

        while (true) {
            Map<String, Object> page = get(listUri(username, list, fields, PAGE_SIZE, offset));
            if (page.get("data") instanceof List<?> data) {
                data.stream().filter(Map.class::isInstance).forEach(row -> rows.add((Map<String, Object>) row));
            }

            // MAL paginates by handing back a "next" URL; its presence is the only signal.
            if (!(page.get("paging") instanceof Map<?, ?> paging) || paging.get("next") == null) {
                return rows;
            }
            offset += PAGE_SIZE;
        }
    }

    /**
     * Built as a {@link URI} rather than a string: the field list carries literal braces —
     * {@code list_status{start_date}} — which a string URI would be template-expanded on.
     */
    private URI listUri(String username, String list, String fields, int limit, int offset) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(properties.apiUrl())
                .pathSegment("users", username, list)
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                // A reader's own list must come back whole; by default MAL quietly
                // withholds entries it rates as adult, which would silently shrink imports.
                .queryParam("nsfw", "true");
        if (!fields.isEmpty()) {
            uri.queryParam("fields", fields);
        }
        return uri.build().encode().toUri();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(URI uri) {
        if (!properties.canConnectAccounts()) {
            throw new MalNotConfiguredException();
        }

        MalUnavailableException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            rateLimiter.acquire();
            try {
                Map<String, Object> body = restClient
                        .get()
                        .uri(uri)
                        .header("X-MAL-CLIENT-ID", properties.clientId())
                        .header("Accept", "application/json")
                        .exchange((request, response) -> {
                            int status = response.getStatusCode().value();
                            // 404 is a username MAL does not know; 403 is a list it will
                            // not show. Both are the reader's to fix, not ours to retry.
                            if (status == 404) {
                                throw new MalUserNotFoundException(usernameFrom(uri));
                            }
                            if (status == 403) {
                                throw new MalListPrivateException();
                            }
                            if (response.getStatusCode().isError()) {
                                throw new MalUnavailableException("MyAnimeList responded with " + status);
                            }
                            return (Map<String, Object>) response.bodyTo(Map.class);
                        });
                return body == null ? Map.of() : body;
            } catch (MalUnavailableException e) {
                lastFailure = e;
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                log.debug("MAL attempt {} failed ({}), retrying", attempt, e.getMessage());
                pause(properties.retryBackoffMs() * attempt);
            } catch (RestClientException e) {
                // A dropped connection is as transient as a gateway error.
                lastFailure = new MalUnavailableException("MyAnimeList request failed", e);
                if (attempt == MAX_ATTEMPTS) {
                    throw lastFailure;
                }
                log.debug("MAL attempt {} failed ({}), retrying", attempt, e.getMessage());
                pause(properties.retryBackoffMs() * attempt);
            }
        }

        throw lastFailure;
    }

    /** Only for the not-found message; the path is always users/{name}/{list}. */
    private static String usernameFrom(URI uri) {
        String[] segments = uri.getPath().split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            if ("users".equals(segments[i])) {
                return segments[i + 1];
            }
        }
        return "?";
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MalUnavailableException("Interrupted while waiting to retry MyAnimeList", e);
        }
    }
}
