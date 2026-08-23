package dev.nexus.modules.anime;

import dev.nexus.core.domain.MediaType;
import dev.nexus.core.web.OutboundRateLimiter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AniListClient.class);

    /** AniList caps a page at 50 rows, so callers resolve in chunks of that size. */
    static final int MAX_BATCH = 50;

    /**
     * AniList sits behind Cloudflare and intermittently answers 502 or 504 under no load at
     * all. Importing a list is dozens of calls, so without retries a single blip anywhere in
     * the run loses the whole import — which is exactly what a reader notices.
     */
    private static final int MAX_ATTEMPTS = 3;



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

    /**
     * A user's own list. Sent with their token, so private lists come back too — without it
     * AniList answers with only what the world can see, which would silently drop entries.
     */
    private static final String LIST_QUERY =
            """
            query ($userName: String, $type: MediaType) {
              MediaListCollection(userName: $userName, type: $type) {
                lists {
                  entries {
                    status
                    progress
                    progressVolumes
                    score(format: POINT_100)
                    startedAt { year month day }
                    completedAt { year month day }
                    media { id idMal type episodes chapters volumes title { romaji english native } }
                  }
                }
              }
            }
            """;

    /**
     * Everything the detail page shows. One call, kept apart from the fields every list row
     * needs: relations alone carry a nested media record each.
     *
     * <p>The next episode is asked for as an absolute airing time rather than a countdown:
     * a duration cached for a day is wrong by a day, while a timestamp stays true.
     */
    private static final String DETAIL_QUERY =
            """
            query ($id: Int) {
              Media(id: $id) {
                title { romaji english native }
                bannerImage
                nextAiringEpisode { episode airingAt }
                season
                seasonYear
                duration
                source
                hashtag
                popularity
                favourites
                meanScore
                studios { edges { isMain node { id name } } }
                relations {
                  edges {
                    relationType(version: 2)
                    node {
                      id type format status
                      title { romaji english native }
                      coverImage { large }
                      startDate { year }
                    }
                  }
                }
                characters(sort: [ROLE, RELEVANCE], perPage: 12) {
                  edges {
                    role
                    node { id name { full } image { medium } }
                    voiceActors(language: JAPANESE) { id name { full } image { medium } }
                  }
                }
                staff(perPage: 8) { edges { role node { id name { full } image { medium } } } }
                tags { name rank isMediaSpoiler }
                stats {
                  statusDistribution { status amount }
                  scoreDistribution { score amount }
                }
                trailer { id site thumbnail }
                externalLinks { site url icon language }
                rankings { rank type year season allTime context }
              }
            }
            """;

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

    /**
     * Every entry of one media type from a user's list, flattened: AniList groups them by
     * the user's own custom list names, which are theirs to arrange and mean nothing here.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchList(String userName, MediaType mediaType, String accessToken) {
        Map<String, Object> data = post(
                LIST_QUERY, Map.of("userName", userName, "type", anilistType(mediaType)), accessToken);

        if (!(data.get("MediaListCollection") instanceof Map<?, ?> collection)
                || !(collection.get("lists") instanceof List<?> lists)) {
            return List.of();
        }

        List<Map<String, Object>> entries = new java.util.ArrayList<>();
        for (Object list : lists) {
            if (list instanceof Map<?, ?> group && group.get("entries") instanceof List<?> rows) {
                rows.stream().filter(Map.class::isInstance).forEach(row -> entries.add((Map<String, Object>) row));
            }
        }
        return entries;
    }

    /** Empty when AniList has no such media; the caller treats that as nothing to add. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> findMediaDetail(String externalId) {
        Map<String, Object> data = post(DETAIL_QUERY, Map.of("id", Integer.parseInt(externalId)));
        return data.get("Media") instanceof Map<?, ?> media ? (Map<String, Object>) media : Map.of();
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
        return post(query, variables, null);
    }

    private Map<String, Object> post(String query, Map<String, Object> variables, String accessToken) {
        AniListUnavailableException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            rateLimiter.acquire();
            try {
                return attempt(query, variables, accessToken);
            } catch (AniListUnavailableException e) {
                lastFailure = e;
                if (!e.isWorthRetrying() || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                log.debug("AniList attempt {} failed ({}), retrying", attempt, e.getMessage());
                pause(properties.retryBackoffMs() * attempt);
            }
        }

        throw lastFailure;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> attempt(String query, Map<String, Object> variables, String accessToken) {
        try {
            RestClient.RequestBodySpec request =
                    restClient.post().uri(properties.apiUrl()).header("Accept", "application/json");
            if (accessToken != null) {
                request = request.header("Authorization", "Bearer " + accessToken);
            }

            Map<String, Object> body = request
                    .body(Map.of("query", query, "variables", variables))
                    .exchange((sent, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.isError()) {
                            // A 404 for a missing title still arrives as an error status with a
                            // GraphQL body; the caller wants an empty result, not an exception.
                            if (status.value() == 404) {
                                return Map.<String, Object>of();
                            }
                            throw new AniListUnavailableException(
                                    "AniList responded with " + status.value(), status.value());
                        }
                        return (Map<String, Object>) response.bodyTo(Map.class);
                    });

            if (body == null) {
                return Map.of();
            }
            return body.get("data") instanceof Map<?, ?> data ? (Map<String, Object>) data : Map.of();
        } catch (RestClientException e) {
            // A dropped connection is as transient as a gateway error, and as worth retrying.
            throw new AniListUnavailableException("AniList request failed", e, true);
        }
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AniListUnavailableException("Interrupted while waiting to retry AniList", e, false);
        }
    }
}
