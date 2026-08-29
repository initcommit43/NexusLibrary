package dev.nexus.modules.film;

import dev.nexus.core.adapter.BrowseResults;
import dev.nexus.core.adapter.BrowseShelf;
import dev.nexus.core.adapter.DiscoverFilters;
import dev.nexus.core.adapter.FilterField;
import dev.nexus.core.adapter.FilterField.FilterOption;
import dev.nexus.core.adapter.ItemSearchResult;
import dev.nexus.core.adapter.MetadataAdapter;
import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The canonical catalogue for films and shows. One adapter serves both, the way AniList's
 * serves anime and manga — but TMDB numbers its two kinds separately, so ids carry which
 * kind they are (see {@link TmdbKind}).
 */
@Component
public class TmdbMetadataAdapter implements MetadataAdapter {

    /** TMDB rates on 0-10; core stores 0-100, so ratings scale once, here. */
    private static final int RATING_SCALE = 10;

    private static final String STATUS_RELEASED = "Released";

    /**
     * What a page keeps of each list TMDB appends.
     *
     * <p>TMDB will hand back six hundred crew on a large film. The answer is cached on the
     * shared item, so anything kept is paid for by every reader who opens the page and shows
     * none of it — the same bargain IGDB's detail strikes.
     */
    private static final int MAX_CAST = 18;

    private static final int MAX_CREW = 12;

    private static final int MAX_VIDEOS = 6;

    private static final int MAX_BACKDROPS = 8;

    private static final int MAX_KEYWORDS = 15;

    private static final int MAX_RECOMMENDATIONS = 12;

    /**
     * The credits a page names. Crew is most of what TMDB knows about a film and almost none
     * of it is worth a tile: a reader wants who directed and wrote it, not the second unit.
     */
    private static final Set<String> KEY_JOBS = Set.of(
            "Director",
            "Screenplay",
            "Writer",
            "Story",
            "Novel",
            "Producer",
            "Executive Producer",
            "Original Music Composer",
            "Director of Photography",
            "Editor",
            "Creator");

    /** The one host whose ids the trailer panel can embed. */
    private static final String YOUTUBE = "YouTube";
    private static final Set<String> FINISHED_SHOW_STATUSES = Set.of("Ended", "Canceled", "Cancelled");

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TmdbMetadataAdapter.class);

    private final TmdbClient client;

    /** Genre lists for the filter bar, one per kind, each fetched at most once per run. */
    private final Map<TmdbKind, List<FilterOption>> genres = new java.util.concurrent.ConcurrentHashMap<>();
    private final TmdbProperties properties;

    public TmdbMetadataAdapter(TmdbClient client, TmdbProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Set<MediaType> mediaTypes() {
        return Set.of(MediaType.MOVIE, MediaType.SHOW);
    }

    @Override
    public Source source() {
        return Source.TMDB;
    }

    @Override
    public List<ItemSearchResult> search(MediaType mediaType, String query, int limit) {
        TmdbKind kind = TmdbKind.of(mediaType);
        return client.search(kind, query, limit).stream()
                .map(row -> new ItemSearchResult(
                        kind.mediaType(),
                        Source.TMDB,
                        kind.externalId(row.get("id")),
                        title(kind, row),
                        posterUrl(row),
                        releaseDate(kind, row)))
                .filter(result -> result.title() != null)
                .toList();
    }

    @Override
    public Optional<TrackableItemData> fetchById(String externalId) {
        return TmdbKind.ofExternalId(externalId)
                .flatMap(kind -> client.findById(kind, TmdbKind.tmdbId(externalId))
                        .map(row -> toItemData(kind, row)));
    }


    /**
     * Everything a title's own page shows beyond the fields core models.
     *
     * <p>Trimmed on the way in rather than stored whole, and shaped so the page's reader never
     * has to know that TMDB says {@code profile_path} where IGDB hands over a URL.
     */
    @Override
    public Optional<Map<String, Object>> fetchDetail(String externalId) {
        return TmdbKind.ofExternalId(externalId)
                .flatMap(kind -> client.findDetail(kind, TmdbKind.tmdbId(externalId))
                        .map(row -> toDetail(kind, row)));
    }

    /**
     * The first backdrop, which is the one the detail page already treats as the banner —
     * TMDB returns them most-voted first, so it is the still the site itself leads with.
     */
    @Override
    public Optional<String> bannerFrom(Map<String, Object> detail) {
        return firstUrl(detail.get("backdrops"));
    }

    private Map<String, Object> toDetail(TmdbKind kind, Map<String, Object> row) {
        Map<String, Object> detail = new LinkedHashMap<>();

        putIfPresent(detail, "tagline", row.get("tagline"));
        putIfPresent(detail, "voteAverage", row.get("vote_average"));
        putIfPresent(detail, "voteCount", row.get("vote_count"));

        if (kind == TmdbKind.MOVIE) {
            putIfPresent(detail, "runtime", row.get("runtime"));
            // Zero is how TMDB writes "nobody told us", not a film that cost nothing.
            putIfPositive(detail, "budget", row.get("budget"));
            putIfPositive(detail, "revenue", row.get("revenue"));
        } else {
            putIfPresent(detail, "networks", names(row.get("networks")));
        }

        Map<String, Object> credits = nested(row.get("credits"));
        putIfAny(detail, "cast", people(list(credits.get("cast")), MAX_CAST, "character"));
        putIfAny(detail, "crew", crew(list(credits.get("crew"))));

        putIfAny(detail, "videos", videos(nested(row.get("videos"))));
        putIfAny(detail, "backdrops", backdrops(nested(row.get("images"))));
        putIfAny(detail, "keywords", keywords(kind, nested(row.get("keywords"))));
        putIfAny(detail, "recommendations", recommendations(kind, nested(row.get("recommendations"))));
        putIfAny(detail, "links", links(row));

        return detail;
    }

    /** Cast and crew are the same tile; only the line under the name differs. */
    private List<Map<String, Object>> people(List<Map<String, Object>> rows, int limit, String roleKey) {
        return rows.stream()
                .filter(person -> person.get("name") != null)
                .limit(limit)
                .map(person -> {
                    Map<String, Object> tile = new LinkedHashMap<>();
                    tile.put("name", person.get("name").toString());
                    tile.put("role", person.get(roleKey) == null ? null : person.get(roleKey).toString());
                    tile.put("image", profileUrl(person));
                    return tile;
                })
                .toList();
    }

    private String profileUrl(Map<String, Object> person) {
        Object path = person.get("profile_path");
        return path == null ? null : properties.profileUrl(path.toString());
    }

    /**
     * One line per person rather than per job: someone who directed, wrote and produced is one
     * tile, credited with the first of those jobs TMDB listed them under.
     */
    private List<Map<String, Object>> crew(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();

        for (Map<String, Object> member : rows) {
            Object job = member.get("job");
            Object name = member.get("name");
            if (name == null || job == null || !KEY_JOBS.contains(job.toString())) {
                continue;
            }
            byName.putIfAbsent(name.toString(), member);
        }

        return people(List.copyOf(byName.values()), MAX_CREW, "job");
    }

    /** Trailers before teasers, and only what the page is able to embed. */
    private List<Map<String, Object>> videos(Map<String, Object> videos) {
        return list(videos.get("results")).stream()
                .filter(video -> YOUTUBE.equals(String.valueOf(video.get("site"))))
                .filter(video -> video.get("key") != null)
                .sorted(java.util.Comparator.comparingInt(
                        video -> "Trailer".equals(String.valueOf(video.get("type"))) ? 0 : 1))
                .limit(MAX_VIDEOS)
                .map(video -> Map.<String, Object>of(
                        "id",
                        video.get("key").toString(),
                        "name",
                        String.valueOf(video.getOrDefault("name", ""))))
                .toList();
    }

    private List<String> backdrops(Map<String, Object> images) {
        return list(images.get("backdrops")).stream()
                .filter(image -> image.get("file_path") != null)
                .limit(MAX_BACKDROPS)
                .map(image -> properties.backdropUrl(image.get("file_path").toString()))
                .filter(Objects::nonNull)
                .toList();
    }

    /** TMDB keys a film's keywords under "keywords" and a show's under "results". */
    private List<String> keywords(TmdbKind kind, Map<String, Object> keywords) {
        Object entries = kind == TmdbKind.MOVIE ? keywords.get("keywords") : keywords.get("results");
        return names(entries).stream().limit(MAX_KEYWORDS).toList();
    }

    /**
     * Titles like this one, carrying ids in the form this app addresses them by.
     *
     * <p>TMDB numbers films and shows separately, so a bare id names two different titles
     * depending on which kind is asked for — a card carrying one would open a page that
     * cannot resolve.
     */
    private List<Map<String, Object>> recommendations(TmdbKind kind, Map<String, Object> recommendations) {
        return list(recommendations.get("results")).stream()
                .filter(row -> row.get("id") != null && title(kind, row) != null)
                .limit(MAX_RECOMMENDATIONS)
                .map(row -> {
                    Map<String, Object> like = new LinkedHashMap<>();
                    like.put("id", kind.externalId(row.get("id")));
                    like.put("name", title(kind, row));
                    like.put("cover", posterUrl(row));
                    LocalDate released = releaseDate(kind, row);
                    like.put("year", released == null ? null : String.valueOf(released.getYear()));
                    return like;
                })
                .toList();
    }

    private List<Map<String, Object>> links(Map<String, Object> row) {
        List<Map<String, Object>> links = new ArrayList<>();

        Object homepage = row.get("homepage");
        if (homepage != null && !homepage.toString().isBlank()) {
            links.add(Map.of("site", "Official site", "url", homepage.toString()));
        }

        Object imdbId = nested(row.get("external_ids")).get("imdb_id");
        if (imdbId != null && !imdbId.toString().isBlank()) {
            links.add(Map.of("site", "IMDb", "url", "https://www.imdb.com/title/" + imdbId));
        }
        return links;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object raw) {
        return raw instanceof List<?> entries
                ? entries.stream()
                        .filter(Map.class::isInstance)
                        .map(entry -> (Map<String, Object>) entry)
                        .toList()
                : List.of();
    }

    private void putIfAny(Map<String, Object> target, String key, List<?> values) {
        if (!values.isEmpty()) {
            target.put(key, values);
        }
    }

    private void putIfPositive(Map<String, Object> target, String key, Object value) {
        if (value instanceof Number number && number.longValue() > 0) {
            target.put(key, number);
        }
    }

    @Override
    public List<BrowseShelf> browseShelves(MediaType mediaType) {
        return TmdbShelves.shelvesFor(TmdbKind.of(mediaType));
    }

    @Override
    public List<FilterField> discoverFilters(MediaType mediaType) {
        TmdbKind kind = TmdbKind.of(mediaType);
        return TmdbFilters.fields(kind, genresFor(kind), LocalDate.now());
    }

    @Override
    public BrowseResults discover(MediaType mediaType, DiscoverFilters filters, int page, int size) {
        TmdbKind kind = TmdbKind.of(mediaType);
        String term = filters.one("q");

        if (term == null || term.isBlank()) {
            Map<String, Object> body = client.discover(kind, TmdbFilters.discoverQuery(kind, filters), page);
            return new BrowseResults(toSearchResults(kind, client.resultsOf(body)), client.hasMorePages(body, page));
        }

        // Search takes no filters of its own, so the rest are applied to what it answers.
        Map<String, Object> body = client.searchPage(kind, term, page);
        List<Map<String, Object>> rows = client.resultsOf(body).stream()
                .filter(row -> TmdbFilters.matches(kind, row, filters))
                .toList();

        return new BrowseResults(toSearchResults(kind, rows), client.hasMorePages(body, page));
    }

    /**
     * The genre list for one kind, fetched once and kept. Movies and shows have different
     * lists and neither has changed in years, so a copy each is enough for the run.
     *
     * <p>A failure leaves the list empty and uncached, which drops the control from the bar
     * rather than showing an empty one; the next visit tries again.
     */
    private List<FilterOption> genresFor(TmdbKind kind) {
        List<FilterOption> known = genres.get(kind);
        if (known != null) {
            return known;
        }

        try {
            List<FilterOption> options = client.genres(kind).stream()
                    .map(row -> new FilterOption(String.valueOf(row.get("id")), String.valueOf(row.get("name"))))
                    .filter(option -> !option.value().equals("null") && !option.label().equals("null"))
                    .toList();

            if (!options.isEmpty()) {
                genres.put(kind, options);
            }
            return options;
        } catch (RuntimeException e) {
            log.warn("Could not fetch the TMDB genre list, leaving that filter out: {}", e.toString());
            return List.of();
        }
    }

    /** Listing rows as the shared shape, shared by the shelves, the search and the grid. */
    private List<ItemSearchResult> toSearchResults(TmdbKind kind, List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> new ItemSearchResult(
                        kind.mediaType(),
                        Source.TMDB,
                        kind.externalId(row.get("id")),
                        title(kind, row),
                        posterUrl(row),
                        releaseDate(kind, row),
                        facets(row)))
                .filter(result -> result.title() != null && !result.title().isBlank())
                .toList();
    }

    @Override
    public BrowseResults browse(MediaType mediaType, String shelfId, int page, int size) {
        TmdbKind kind = TmdbKind.of(mediaType);
        TmdbShelves.Definition shelf = TmdbShelves.find(kind, shelfId);
        if (shelf == null) {
            return BrowseResults.empty();
        }

        Map<String, Object> body = shelf.trending()
                ? client.trending(kind, shelf.path(), page)
                : client.browse(kind, shelf.path(), page);

        List<ItemSearchResult> items = toSearchResults(kind, client.resultsOf(body));

        return new BrowseResults(items, client.hasMorePages(body, page));
    }

    /**
     * TMDB's list rows already carry a score and a vote count, so a ranked shelf costs no
     * extra request. A zero score means unrated rather than terrible, and is left out.
     */
    private Map<String, Object> facets(Map<String, Object> row) {
        Map<String, Object> facets = new HashMap<>();
        if (row.get("vote_average") instanceof Number rating && rating.doubleValue() > 0) {
            facets.put("score", Math.round(rating.doubleValue() * RATING_SCALE));
        }
        return Map.copyOf(facets);
    }

    private TrackableItemData toItemData(TmdbKind kind, Map<String, Object> row) {
        return new TrackableItemData(
                kind.mediaType(),
                Source.TMDB,
                kind.externalId(row.get("id")),
                title(kind, row),
                posterUrl(row),
                releaseDate(kind, row),
                itemState(kind, row),
                metadata(kind, row));
    }

    /** TMDB calls a film's name "title" and a show's "name". */
    private String title(TmdbKind kind, Map<String, Object> row) {
        Object title = kind == TmdbKind.MOVIE ? row.get("title") : row.get("name");
        return title == null ? null : title.toString();
    }

    private String posterUrl(Map<String, Object> row) {
        return row.get("poster_path") == null ? null : properties.posterUrl(row.get("poster_path").toString());
    }

    private LocalDate releaseDate(TmdbKind kind, Map<String, Object> row) {
        Object raw = kind == TmdbKind.MOVIE ? row.get("release_date") : row.get("first_air_date");
        // TMDB writes an unknown date as "", not as an absent field.
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.toString());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * What drives cache staleness. A finished show is settled and never refreshed again; a
     * returning one gains episodes, so its episode count has to be re-read.
     */
    private ItemState itemState(TmdbKind kind, Map<String, Object> row) {
        String status = row.get("status") == null ? "" : row.get("status").toString();
        LocalDate released = releaseDate(kind, row);
        boolean out = released != null && !released.isAfter(LocalDate.now());

        if (kind == TmdbKind.MOVIE) {
            return STATUS_RELEASED.equals(status) || out ? ItemState.RELEASED : ItemState.UPCOMING;
        }
        if (FINISHED_SHOW_STATUSES.contains(status)) {
            return ItemState.RELEASED;
        }
        return out ? ItemState.ONGOING : ItemState.UPCOMING;
    }

    private Map<String, Object> metadata(TmdbKind kind, Map<String, Object> row) {
        Map<String, Object> metadata = new HashMap<>();
        putIfPresent(metadata, "summary", row.get("overview"));
        putIfPresent(metadata, "genres", names(row.get("genres")));

        // The library's format facet: films are one thing, a miniseries and a talk show are not.
        putIfPresent(metadata, "format", kind == TmdbKind.MOVIE ? "Movie" : row.get("type"));

        if (kind == TmdbKind.MOVIE) {
            putIfPresent(metadata, "runtimeMinutes", row.get("runtime"));
        } else {
            putIfPresent(metadata, "episodes", row.get("number_of_episodes"));
            putIfPresent(metadata, "seasons", row.get("number_of_seasons"));
        }

        if (row.get("vote_average") instanceof Number rating && rating.doubleValue() > 0) {
            metadata.put("externalRating", Math.round(rating.doubleValue() * RATING_SCALE));
        }
        return metadata;
    }

    private List<String> names(Object raw) {
        if (!(raw instanceof List<?> entries)) {
            return List.of();
        }
        return entries.stream()
                .filter(Map.class::isInstance)
                .map(entry -> ((Map<?, ?>) entry).get("name"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value != null) {
            target.put(key, value);
        }
    }
    /** The first usable url in a list of them, which is where both wide-art keys arrive. */
    private Optional<String> firstUrl(Object raw) {
        if (!(raw instanceof List<?> urls)) {
            return Optional.empty();
        }
        return urls.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(url -> !url.isBlank())
                .findFirst();
    }

}
