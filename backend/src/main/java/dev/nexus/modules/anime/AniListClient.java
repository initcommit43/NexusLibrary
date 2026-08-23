package dev.nexus.modules.anime;

import dev.nexus.core.domain.MediaType;
import dev.nexus.core.web.OutboundRateLimiter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Speaks AniList's GraphQL API: one endpoint, one POST per call, the query carried in the
 * body rather than the path.
 *
 * <p>Every query asks for the same media fields, so a title looks identical however it was
 * found — searched for, resolved from a MAL id, or refreshed later.
 */
@Component
public class AniListClient {

    /** AniList caps a page at 50 rows, so callers resolve in chunks of that size. */
    static final int MAX_BATCH = 50;

    private static final String MEDIA_FIELDS =
            """
            id
            idMal
            type
            format
            status
            episodes
            chapters
            volumes
            averageScore
            description(asHtml: false)
            title { romaji english native }
            coverImage { large }
            startDate { year month day }
            genres
            studios(isMain: true) { nodes { name } }
            """;

    private static final String SEARCH_QUERY =
            """
            query ($search: String, $type: MediaType, $perPage: Int) {
              Page(page: 1, perPage: $perPage) {
                media(search: $search, type: $type, sort: SEARCH_MATCH) { %s }
              }
            }
            """
                    .formatted(MEDIA_FIELDS);

    private static final String BY_ID_QUERY =
            """
            query ($id: Int) {
              Media(id: $id) { %s }
            }
            """
                    .formatted(MEDIA_FIELDS);

    private static final String BY_IDS_QUERY =
            """
            query ($ids: [Int], $perPage: Int) {
              Page(page: 1, perPage: $perPage) {
                media(id_in: $ids) { %s }
              }
            }
            """
                    .formatted(MEDIA_FIELDS);

    private static final String BY_MAL_ID_QUERY =
            """
            query ($idMal: Int, $type: MediaType) {
              Media(idMal: $idMal, type: $type) { %s }
            }
            """
                    .formatted(MEDIA_FIELDS);

    private final RestClient restClient;
    private final AniListProperties properties;
    private final OutboundRateLimiter rateLimiter;

    public AniListClient(RestClient.Builder builder, AniListProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
        this.rateLimiter = new OutboundRateLimiter(properties.requestsPerMinute() / 60.0);
    }

    public List<Map<String, Object>> searchMedia(MediaType mediaType, String query, int limit) {
        Map<String, Object> data = post(
                SEARCH_QUERY,
                Map.of("search", query, "type", anilistType(mediaType), "perPage", Math.min(limit, MAX_BATCH)));
        return pageMedia(data);
    }

    /** Empty when AniList has no such media, which is a miss rather than a failure. */
    public List<Map<String, Object>> findMediaById(String externalId) {
        Map<String, Object> data = post(BY_ID_QUERY, Map.of("id", Integer.parseInt(externalId)));
        return single(data.get("Media"));
    }

    /** Bulk variant, so importing a list costs a handful of requests rather than one each. */
    public List<Map<String, Object>> findMediaByIds(Collection<String> externalIds) {
        List<Integer> ids = externalIds.stream().map(Integer::parseInt).toList();
        return pageMedia(post(BY_IDS_QUERY, Map.of("ids", ids, "perPage", MAX_BATCH)));
    }

    /**
     * The hard ID join a MAL import rests on: AniList stores the MAL id of the same work, so
     * most of a list resolves with no title guessing at all.
     */
    public List<Map<String, Object>> findMediaByMalId(MediaType mediaType, String malId) {
        Map<String, Object> data =
                post(BY_MAL_ID_QUERY, Map.of("idMal", Integer.parseInt(malId), "type", anilistType(mediaType)));
        return single(data.get("Media"));
    }

    /** AniList caps a page at 50 rows, so callers resolve in chunks of that size. */
    public static List<List<String>> partition(Collection<String> values) {
        List<String> all = List.copyOf(values);
        List<List<String>> batches = new java.util.ArrayList<>();
        for (int i = 0; i < all.size(); i += MAX_BATCH) {
            batches.add(all.subList(i, Math.min(all.size(), i + MAX_BATCH)));
        }
        return batches;
    }

    static String anilistType(MediaType mediaType) {
        return mediaType == MediaType.MANGA ? "MANGA" : "ANIME";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pageMedia(Map<String, Object> data) {
        if (!(data.get("Page") instanceof Map<?, ?> page) || !(page.get("media") instanceof List<?> media)) {
            return List.of();
        }
        return (List<Map<String, Object>>) media;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> single(Object media) {
        return media instanceof Map<?, ?> found ? List.of((Map<String, Object>) found) : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String query, Map<String, Object> variables) {
        rateLimiter.acquire();

        try {
            Map<String, Object> body = restClient
                    .post()
                    .uri(properties.apiUrl())
                    .header("Accept", "application/json")
                    .body(Map.of("query", query, "variables", variables))
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.isError()) {
                            // A 404 for a missing title still arrives as an error status with a
                            // GraphQL body; the caller wants an empty result, not an exception.
                            if (status.value() == 404) {
                                return Map.<String, Object>of();
                            }
                            throw new AniListUnavailableException("AniList responded with " + status.value());
                        }
                        return (Map<String, Object>) response.bodyTo(Map.class);
                    });

            if (body == null) {
                return Map.of();
            }
            return body.get("data") instanceof Map<?, ?> data ? (Map<String, Object>) data : Map.of();
        } catch (RestClientException e) {
            throw new AniListUnavailableException("AniList request failed", e);
        }
    }
}
