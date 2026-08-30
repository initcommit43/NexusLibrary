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
            nextAiringEpisode { episode airingAt }
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

    /**
     * The type is required: MAL numbers anime and manga separately, so a bare idMal is
     * ambiguous in a way an AniList id never is.
     */
    private static final String BY_MAL_IDS_QUERY =
            """
            query ($idsMal: [Int], $type: MediaType, $perPage: Int) {
              Page(page: 1, perPage: $perPage) {
                media(idMal_in: $idsMal, type: $type) { %s }
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
     * A browse shelf. Every argument but the type is optional, and an unused one is left out
     * of the query text entirely rather than declared and passed as null.
     *
     * <p>That distinction is the whole reason this query is assembled rather than a constant.
     * AniList treats an argument it never received as "no filter", but treats one it received
     * as null as a filter for null — so a shelf that declared {@code season} and passed
     * nothing matched almost no titles at all instead of ignoring the season.
     *
     * <p>{@code isAdult: false} is pinned rather than exposed: a browse page is what a reader
     * sees before choosing anything, and it should not be the place that decision gets made.
     */
    private static String browseQuery(List<String> declarations, List<String> arguments) {
        return """
            query (%s) {
              Page(page: $page, perPage: $perPage) {
                pageInfo { hasNextPage }
                media(%s) { %s }
              }
            }
            """
                .formatted(String.join(", ", declarations), String.join(", ", arguments), MEDIA_FIELDS);
    }

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
     * A reader's own activity stream, newest first, anime and manga alike.
     *
     * <p>This is the history a map is drawn from. A list entry carries the day it was started
     * and the day it was finished; the stream carries the day of every episode in between,
     * which is the difference between two squares a title and a year that looks lived in.
     *
     * <p>Text posts and message replies share the connection with list activity, so the type
     * is pinned to the two list kinds — everything else on it is someone's blog.
     */
    private static final String ACTIVITY_QUERY =
            """
            query ($userId: Int, $page: Int, $perPage: Int) {
              Page(page: $page, perPage: $perPage) {
                pageInfo { hasNextPage }
                activities(userId: $userId, type_in: [ANIME_LIST, MANGA_LIST], sort: ID_DESC) {
                  ... on ListActivity {
                    id
                    createdAt
                    status
                    progress
                    media { id type }
                  }
                }
              }
            }
            """;

    /**
     * What one studio made, newest first.
     *
     * <p>Asked of the studio rather than of the media list, because that is where AniList
     * keeps the relation: a studio's page is its own connection, and filtering all of anime by
     * a studio name would match the words rather than the company.
     */
    private static final String STUDIO_QUERY =
            """
            query ($id: Int, $page: Int, $perPage: Int) {
              Studio(id: $id) {
                name
                media(sort: START_DATE_DESC, page: $page, perPage: $perPage) {
                  pageInfo { hasNextPage }
                  nodes { %s }
                }
              }
            }
            """
                    .formatted(MEDIA_FIELDS);

    /** Who the token belongs to, as a number: the activity stream is not addressable by name. */
    private static final String VIEWER_ID_QUERY = "query { Viewer { id } }";

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


    /** One page of a browse shelf, with whether AniList has another behind it. */
    public MediaPage browseMedia(
            MediaType mediaType,
            String sort,
            String season,
            Integer seasonYear,
            String status,
            String format,
            int page,
            int perPage) {

        List<String> declarations = new java.util.ArrayList<>(
                List.of("$type: MediaType", "$sort: [MediaSort]", "$page: Int", "$perPage: Int"));
        List<String> arguments = new java.util.ArrayList<>(List.of("type: $type", "sort: $sort", "isAdult: false"));
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("type", anilistType(mediaType));
        variables.put("sort", List.of(sort));
        variables.put("page", page);
        variables.put("perPage", Math.min(perPage, MAX_BATCH));

        addOptional(declarations, arguments, variables, "season", "MediaSeason", season);
        addOptional(declarations, arguments, variables, "seasonYear", "Int", seasonYear);
        addOptional(declarations, arguments, variables, "status", "MediaStatus", status);
        addOptional(declarations, arguments, variables, "format", "MediaFormat", format);

        Map<String, Object> data = post(browseQuery(declarations, arguments), variables);
        return new MediaPage(pageMedia(data), hasNextPage(data));
    }

    /**
     * One page of a filtered browse grid.
     *
     * <p>Shares {@link #browseQuery} with the shelves, and for the same reason: an argument
     * AniList never received is no filter, while one it received as null is a filter for null.
     *
     * <p>Sorted by how well a title matches when there is a term to match, and by popularity
     * when there is not — an unranked list of everything in a genre is not an answer.
     */
    public MediaPage discoverMedia(
            MediaType mediaType,
            String search,
            List<String> genres,
            List<String> tags,
            Integer year,
            String season,
            String format,
            String status,
            int page,
            int perPage) {

        String term = blankToNull(search);

        List<String> declarations = new java.util.ArrayList<>(
                List.of("$type: MediaType", "$sort: [MediaSort]", "$page: Int", "$perPage: Int"));
        List<String> arguments = new java.util.ArrayList<>(List.of("type: $type", "sort: $sort", "isAdult: false"));
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("type", anilistType(mediaType));
        variables.put("sort", List.of(term == null ? "POPULARITY_DESC" : "SEARCH_MATCH"));
        variables.put("page", page);
        variables.put("perPage", Math.min(perPage, MAX_BATCH));

        addOptional(declarations, arguments, variables, "search", "String", term);
        addOptional(
                declarations,
                arguments,
                variables,
                "genre_in",
                "[String]",
                genres == null || genres.isEmpty() ? null : List.copyOf(genres));
        addOptional(
                declarations,
                arguments,
                variables,
                "tag_in",
                "[String]",
                tags == null || tags.isEmpty() ? null : List.copyOf(tags));
        addYear(declarations, arguments, variables, mediaType, year);
        addOptional(declarations, arguments, variables, "season", "MediaSeason", blankToNull(season));
        addOptional(declarations, arguments, variables, "format", "MediaFormat", blankToNull(format));
        addOptional(declarations, arguments, variables, "status", "MediaStatus", blankToNull(status));

        Map<String, Object> data = post(browseQuery(declarations, arguments), variables);
        return new MediaPage(pageMedia(data), hasNextPage(data));
    }

    /**
     * Every tag AniList files anything under, minus the adult ones.
     *
     * <p>Tags are the finer half of how AniList describes a title — a genre says "fantasy",
     * a tag says "isekai" or "found family" — and a filter bar with only the genres in it can
     * ask for a twentieth of what a reader can see on the page in front of them.
     *
     * <p>As slow-moving as the genre list, and held on to by the caller for the same reason.
     */
    @SuppressWarnings("unchecked")
    public List<String> tags() {
        Map<String, Object> data = post("query { MediaTagCollection { name isAdult } }", Map.of());
        if (!(data.get("MediaTagCollection") instanceof List<?> collection)) {
            return List.of();
        }

        return collection.stream()
                .filter(Map.class::isInstance)
                .map(tag -> (Map<String, Object>) tag)
                .filter(tag -> !Boolean.TRUE.equals(tag.get("isAdult")))
                .map(tag -> String.valueOf(tag.get("name")))
                .filter(name -> !name.isBlank() && !"null".equals(name))
                .toList();
    }

    /**
     * Every genre AniList files anything under. A short, slow-moving list, which is why the
     * caller holds on to it rather than asking per page view.
     */
    public List<String> genres() {
        Map<String, Object> data = post("query { GenreCollection }", Map.of());
        return data.get("GenreCollection") instanceof List<?> collection
                ? collection.stream().map(String::valueOf).filter(genre -> !genre.isBlank()).toList()
                : List.of();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Narrows to a year, by whichever field the medium actually carries one in.
     *
     * <p>AniList files a season — and so a {@code seasonYear} — against anime only. Manga has
     * neither, so asking it for a season year matches nothing at all rather than everything
     * from that year; its year lives in the start date, which has to be asked for as a range
     * across the whole of it.
     */
    private void addYear(
            List<String> declarations,
            List<String> arguments,
            Map<String, Object> variables,
            MediaType mediaType,
            Integer year) {

        if (year == null) {
            return;
        }

        if (mediaType == MediaType.ANIME) {
            addOptional(declarations, arguments, variables, "seasonYear", "Int", year);
            return;
        }

        addOptional(declarations, arguments, variables, "startDate_greater", "FuzzyDateInt", year * 10000);
        addOptional(declarations, arguments, variables, "startDate_lesser", "FuzzyDateInt", (year + 1) * 10000);
    }

    /** Adds one filter to the query, or leaves no trace of it when there is no value. */
    private void addOptional(
            List<String> declarations,
            List<String> arguments,
            Map<String, Object> variables,
            String name,
            String graphqlType,
            Object value) {

        if (value == null) {
            return;
        }
        declarations.add("$" + name + ": " + graphqlType);
        arguments.add(name + ": $" + name);
        variables.put(name, value);
    }

    /** One page of media, and whether asking for the next one is worth a request. */
    public record MediaPage(List<Map<String, Object>> media, boolean hasNextPage) {}

    private boolean hasNextPage(Map<String, Object> data) {
        return data.get("Page") instanceof Map<?, ?> page
                && page.get("pageInfo") instanceof Map<?, ?> info
                && Boolean.TRUE.equals(info.get("hasNextPage"));
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
     * The hard ID join in bulk: AniList stores the MAL id of the same work, so most of a
     * MAL list resolves fifty at a time with no title guessing at all.
     */
    public List<Map<String, Object>> findMediaByMalIds(MediaType mediaType, Collection<String> malIds) {
        List<Map<String, Object>> media = new java.util.ArrayList<>();
        for (List<String> batch : partition(malIds)) {
            List<Integer> ids = batch.stream().map(Integer::parseInt).toList();
            media.addAll(pageMedia(post(
                    BY_MAL_IDS_QUERY,
                    Map.of("idsMal", ids, "type", anilistType(mediaType), "perPage", MAX_BATCH))));
        }
        return media;
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

    /** A studio, and one page of what it made. */
    public record StudioPage(String name, List<Map<String, Object>> media, boolean hasNextPage) {}

    /** Empty when AniList has no such studio, which is a miss rather than a failure. */
    @SuppressWarnings("unchecked")
    public StudioPage fetchStudioWorks(String studioId, int page, int perPage) {
        Map<String, Object> data = post(
                STUDIO_QUERY,
                Map.of(
                        "id", Integer.parseInt(studioId),
                        "page", page,
                        "perPage", Math.min(perPage, MAX_BATCH)));

        if (!(data.get("Studio") instanceof Map<?, ?> found)) {
            return new StudioPage(null, List.of(), false);
        }

        Map<String, Object> studio = (Map<String, Object>) found;
        String name = studio.get("name") instanceof String called ? called : null;
        if (!(studio.get("media") instanceof Map<?, ?> media)) {
            return new StudioPage(name, List.of(), false);
        }

        Map<String, Object> works = (Map<String, Object>) media;
        List<Map<String, Object>> nodes = works.get("nodes") instanceof List<?> rows
                ? rows.stream().filter(Map.class::isInstance).map(row -> (Map<String, Object>) row).toList()
                : List.of();

        boolean more = works.get("pageInfo") instanceof Map<?, ?> info
                && Boolean.TRUE.equals(info.get("hasNextPage"));

        return new StudioPage(name, nodes, more);
    }

    /** One page of a reader's activity, and whether there is another behind it. */
    public record ActivityPage(List<Map<String, Object>> activities, boolean hasNextPage) {}

    /**
     * One page of the reader's own activity stream, sent with their token so a private
     * account still answers about itself.
     *
     * <p>Newest first, which is what lets a sync stop as soon as it reaches events it already
     * has rather than walking a decade to find out it learned nothing.
     */
    @SuppressWarnings("unchecked")
    public ActivityPage fetchActivity(int userId, int page, String accessToken) {
        Map<String, Object> data = post(
                ACTIVITY_QUERY,
                Map.of("userId", userId, "page", page, "perPage", MAX_BATCH),
                accessToken);

        if (!(data.get("Page") instanceof Map<?, ?> found)) {
            return new ActivityPage(List.of(), false);
        }

        Map<String, Object> body = (Map<String, Object>) found;
        List<Map<String, Object>> activities = new java.util.ArrayList<>();
        if (body.get("activities") instanceof List<?> rows) {
            rows.stream()
                    // A ListActivity is the only kind asked for, but the field is a union and
                    // an inline fragment leaves an empty object where another kind stood.
                    .filter(Map.class::isInstance)
                    .map(row -> (Map<String, Object>) row)
                    .filter(row -> row.get("id") != null)
                    .forEach(activities::add);
        }
        // Read off the whole answer, not off the page inside it: that is where the helper
        // every other paged call uses looks for the flag.
        return new ActivityPage(activities, hasNextPage(data));
    }

    /** The numeric id of whoever the token belongs to. */
    public int viewerId(String accessToken) {
        Map<String, Object> data = post(VIEWER_ID_QUERY, Map.of(), accessToken);

        if (data.get("Viewer") instanceof Map<?, ?> viewer && viewer.get("id") instanceof Number id) {
            return id.intValue();
        }
        throw new AniListUnavailableException("AniList did not identify the account");
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
                                    "AniList responded with " + status.value(),
                                    status.value(),
                                    graphqlErrorMessage(response));
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

    /** A refusal explains itself far better in the service's words than in a status code. */
    private static final int MAX_UPSTREAM_MESSAGE_LENGTH = 300;

    /**
     * AniList's own words from an error body, or null when it said nothing readable.
     *
     * <p>When AniList refuses on purpose it says why in a GraphQL error — "temporarily
     * disabled due to severe stability issues", once, for weeks — and those words explain
     * the failure better than anything written on this side could. Only a parsed GraphQL
     * message counts: a Cloudflare error page is the gateway talking, not AniList, and
     * repeating its HTML to a reader would be worse than silence.
     */
    private static String graphqlErrorMessage(
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            Map<?, ?> body = response.bodyTo(Map.class);
            if (body == null
                    || !(body.get("errors") instanceof List<?> errors)
                    || errors.isEmpty()
                    || !(errors.getFirst() instanceof Map<?, ?> error)
                    || !(error.get("message") instanceof String message)
                    || message.isBlank()) {
                return null;
            }
            String trimmed = message.strip();
            return trimmed.length() <= MAX_UPSTREAM_MESSAGE_LENGTH
                    ? trimmed
                    : trimmed.substring(0, MAX_UPSTREAM_MESSAGE_LENGTH) + "…";
        } catch (RuntimeException e) {
            // Not JSON at all — a gateway's error page. There are no words worth keeping.
            return null;
        }
    }
}
