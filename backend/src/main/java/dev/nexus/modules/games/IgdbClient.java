package dev.nexus.modules.games;

import dev.nexus.core.web.OutboundRateLimiter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** Drops DLC, expansions and re-releases from anything that lists games. */
    private static final String BASE_CONDITIONS = "parent_game = null & version_parent = null";

    /**
     * IGDB's "Erotic" theme, the only adult marking it keeps. Not an age rating: PEGI 18 is Doom,
     * and a game whose subject is sex is the narrow claim that belongs behind an adult switch.
     */
    static final int EROTIC_THEME = 42;

    /**
     * The theme as a condition for the queries that leave it out. A game with no themes at all
     * is kept explicitly, in case IGDB reads "not in" against a missing list as false.
     */
    static final String WITHOUT_EROTIC = "(themes = null | themes != (%d))".formatted(EROTIC_THEME);

    private static final String GAME_FIELDS =
            "id,name,summary,first_release_date,cover.url,platforms.id,platforms.name,"
                    + "genres.name,total_rating,status,themes";

    /** Whether IGDB files this game under its erotic theme, read off the theme ids. */
    static boolean isAdult(Map<String, Object> game) {
        return game.get("themes") instanceof List<?> themes
                && themes.stream().anyMatch(theme -> theme instanceof Number id && id.intValue() == EROTIC_THEME);
    }

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

    public List<Map<String, Object>> searchGames(String query, int limit, boolean includeAdult) {
        String escaped = quotedTerm(query);
        if (includeAdult) {
            return post("search \"%s\"; fields %s; limit %d;".formatted(escaped, GAME_FIELDS, limit));
        }
        return post("search \"%s\"; where %s; fields %s; limit %d;"
                .formatted(escaped, WITHOUT_EROTIC, GAME_FIELDS, limit));
    }

    /**
     * One browse shelf's worth of games.
     *
     * <p>Every shelf excludes {@code parent_game} and {@code version_parent}, which between
     * them drop DLC, expansions and the Game of the Year re-releases — a popularity sort
     * without them returns four editions of the same game. The obvious alternative, IGDB's
     * {@code category}, is deprecated in favour of {@code game_type}, and IGDB has already
     * retired {@code category} on {@code external_games} once; these two fields are older
     * than both and have not moved. A shelf is handed to every reader unasked, so it leaves
     * adult games out for everyone.
     *
     * @param where an APIcalypse condition, already written by the caller
     * @param sort the ordering, without the trailing semicolon
     * @param offset how many rows to skip, which is how a "view all" grid pages through
     */
    public List<Map<String, Object>> browseGames(String where, String sort, int offset, int limit) {
        return post("where %s & %s & %s; sort %s; fields %s; offset %d; limit %d;"
                .formatted(BASE_CONDITIONS, WITHOUT_EROTIC, where, sort, GAME_FIELDS, offset, limit));
    }

    /**
     * One page of a filtered grid.
     *
     * <p>A term and a sort cannot travel together — IGDB answers 406 to a query holding both,
     * since a search is already ordered by relevance. Without a term there is nothing to order
     * by but how many people have rated the game.
     *
     * @param where the caller's conditions, without the exclusions every query here carries
     */
    public List<Map<String, Object>> discoverGames(
            String search, String where, int offset, int limit, boolean includeAdult) {
        String conditions = where == null || where.isBlank()
                ? BASE_CONDITIONS
                : BASE_CONDITIONS + " & " + where;
        if (!includeAdult) {
            conditions = conditions + " & " + WITHOUT_EROTIC;
        }

        if (search == null || search.isBlank()) {
            return post("where %s; sort total_rating_count desc; fields %s; offset %d; limit %d;"
                    .formatted(conditions, GAME_FIELDS, offset, limit));
        }

        return post("search \"%s\"; where %s; fields %s; offset %d; limit %d;"
                .formatted(quotedTerm(search), conditions, GAME_FIELDS, offset, limit));
    }

    /** Every genre IGDB files a game under. Two dozen of them, and they do not move. */
    public List<Map<String, Object>> genres() {
        return post("fields id,name; sort name asc; limit 50;", "/genres");
    }

    /**
     * The games people are actually looking at, most first.
     *
     * <p>IGDB keeps this apart from the games themselves: {@code popularity_primitives} is a
     * daily table of game ids against a number, and the games have to be fetched after. Two
     * calls, then, for the one shelf that is worth them — a rating count says what was
     * popular for the last decade, and this says what is popular this week.
     *
     * @param type which measure to read, from {@code popularity_types}
     * @return game ids, in the order IGDB ranks them
     */
    public List<String> popularGameIds(int type, int offset, int limit) {
        List<Map<String, Object>> rows = post(
                "fields game_id,value; where popularity_type = %d; sort value desc; offset %d; limit %d;"
                        .formatted(type, offset, Math.min(limit, MAX_BATCH)),
                "/popularity_primitives");

        return rows.stream()
                .map(row -> row.get("game_id"))
                .filter(Number.class::isInstance)
                .map(id -> String.valueOf(((Number) id).longValue()))
                .toList();
    }

    /**
     * Names for the platforms worth offering, asked for by id.
     *
     * <p>IGDB knows 220 platforms, most of them things nobody is choosing between — the
     * Advanced Pico Beena is in there. The ids are the caller's shortlist; the names come from
     * IGDB so they stay whatever IGDB calls them.
     */
    public List<Map<String, Object>> platforms(Collection<Integer> ids) {
        String csv = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        return post("fields id,name; where id = (%s); sort name asc; limit %d;".formatted(csv, ids.size()), "/platforms");
    }

    /**
     * The extra fields a game's own page reads. Sub-fields are named one by one because IGDB
     * expands a relation only as far as it is asked to.
     */
    private static final String DETAIL_FIELDS = String.join(
            ",",
            "storyline",
            "involved_companies.company.name",
            "involved_companies.developer",
            "involved_companies.publisher",
            "game_engines.name",
            "game_modes.name",
            "player_perspectives.name",
            "themes.name",
            "rating",
            "rating_count",
            "aggregated_rating",
            "aggregated_rating_count",
            "similar_games.name",
            "similar_games.cover.image_id",
            "dlcs.name",
            "dlcs.cover.image_id",
            "expansions.name",
            "expansions.cover.image_id",
            "videos.video_id",
            "videos.name",
            "screenshots.image_id",
            "websites.url",
            "websites.type.type",
            "age_ratings.rating_category",
            "age_ratings.organization.name");

    /**
     * Everything a game's own page shows beyond the fields core models.
     *
     * <p>Asked for by name rather than with a wildcard, and capped where a list has no natural
     * end. IGDB will hand back six hundred screenshots and a hundred videos for a large game,
     * and the whole answer is stored on the shared item — a page shows a handful of each, so
     * fetching the rest costs bytes on every reader's behalf and shows nobody anything.
     */
    public Optional<Map<String, Object>> findGameDetail(String externalId) {
        List<Map<String, Object>> found = post("where id = %s; fields %s; limit 1;"
                .formatted(Long.parseLong(externalId), DETAIL_FIELDS));

        return found.stream().findFirst();
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

    /**
     * A term as an APIcalypse string literal. The backslash is escaped first and on purpose:
     * escaping only the quote leaves a backslash the reader typed free to escape the quote
     * this adds, which ends the literal early and hands the rest of the term to IGDB as
     * query clauses of its own.
     */
    private static String quotedTerm(String term) {
        return term.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String quotedCsv(Collection<String> values) {
        return values.stream()
                .map(v -> "\"" + v.replace("\\", "").replace("\"", "") + "\"")
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
