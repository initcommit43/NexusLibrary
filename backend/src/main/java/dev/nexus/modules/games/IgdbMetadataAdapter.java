package dev.nexus.modules.games;

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
import java.util.ArrayList;
import java.util.Collection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IgdbMetadataAdapter implements MetadataAdapter {

    /** IGDB serves thumbnails by default; the big variant is what a cover grid needs. */
    private static final String THUMB_SIZE = "t_thumb";
    private static final String COVER_SIZE = "t_cover_big";

    private static final String IMAGE_BASE = "https://images.igdb.com/igdb/image/upload/";

    /*
     * Caps on the lists a detail page reads. IGDB has no upper bound on any of them and the
     * answer is stored on the shared item, so an untrimmed blob is bytes every reader carries
     * to be shown a handful.
     */
    private static final int MAX_COMPANIES = 8;

    private static final int MAX_TAGS = 12;

    private static final int MAX_RELATED = 12;

    private static final int MAX_VIDEOS = 6;

    private static final int MAX_SCREENSHOTS = 8;

    private static final int MAX_WEBSITES = 10;

    static final String SHELF_POPULAR = "popular";
    static final String SHELF_TOP_RATED = "top-rated";
    static final String SHELF_COMING_SOON = "coming-soon";
    static final String SHELF_RECENT = "recent";

    /**
     * Vote floors for the two rating shelves. "Popular" is a low bar because the sort is the
     * vote count itself; "top rated" is a high one because the sort is the score, and without
     * it a single enthusiastic rating wins.
     */
    private static final int POPULAR_VOTE_FLOOR = 20;

    private static final int TOP_RATED_VOTE_FLOOR = 200;

    /** How far back "recently released" reaches. A quarter is enough to stay populated. */
    private static final Duration RECENT_WINDOW = Duration.ofDays(90);

    // IGDB status codes that mean the game is not out yet. Absent status means released.
    private static final int STATUS_ALPHA = 2;
    private static final int STATUS_BETA = 3;
    private static final int STATUS_EARLY_ACCESS = 4;

    private static final Logger log = LoggerFactory.getLogger(IgdbMetadataAdapter.class);

    private final IgdbClient client;

    /** Lookup lists for the filter bar, each fetched at most once per run. */
    private final AtomicReference<List<FilterOption>> genres = new AtomicReference<>();

    private final AtomicReference<List<FilterOption>> platforms = new AtomicReference<>();

    public IgdbMetadataAdapter(IgdbClient client) {
        this.client = client;
    }

    @Override
    public Set<MediaType> mediaTypes() {
        return Set.of(MediaType.GAME);
    }

    @Override
    public Source source() {
        return Source.IGDB;
    }

    @Override
    public List<ItemSearchResult> search(MediaType mediaType, String query, int limit) {
        return client.searchGames(query, limit).stream()
                .map(game -> new ItemSearchResult(
                        MediaType.GAME,
                        Source.IGDB,
                        string(game.get("id")),
                        string(game.get("name")),
                        coverUrl(game),
                        releaseDate(game)))
                .toList();
    }

    @Override
    public Optional<TrackableItemData> fetchById(String externalId) {
        return client.findGameById(externalId).stream().findFirst().map(this::toItemData);
    }

    @Override
    public List<BrowseShelf> browseShelves(MediaType mediaType) {
        return List.of(
                new BrowseShelf(SHELF_POPULAR, "Popular now"),
                new BrowseShelf(SHELF_TOP_RATED, "Top rated"),
                new BrowseShelf(SHELF_COMING_SOON, "Coming soon"),
                new BrowseShelf(SHELF_RECENT, "Recently released"));
    }

    /**
     * IGDB has no popularity endpoint that is free to call, so popularity here is how many
     * people have rated a game and how well — which is a decent proxy and needs no extra
     * permission. Both rating shelves carry a vote floor: without one, a game with a single
     * ten out of ten outranks everything ever made.
     */
    /**
     * Everything a game's own page shows beyond the fields core models.
     *
     * <p>Trimmed on the way in rather than stored whole. IGDB will hand back six hundred
     * screenshots for a large game and the answer is kept on the shared item, so the cost of
     * an untrimmed blob is paid by every reader who opens the page and shows none of them.
     */
    @Override
    public Optional<Map<String, Object>> fetchDetail(String externalId) {
        return client.findGameDetail(externalId).map(this::toDetail);
    }

    /** A game has no key art of banner shape, so its first screenshot stands in for one. */
    @Override
    public Optional<String> bannerFrom(Map<String, Object> detail) {
        return firstUrl(detail.get("screenshots"));
    }

    private Map<String, Object> toDetail(Map<String, Object> game) {
        Map<String, Object> detail = new LinkedHashMap<>();

        putIfPresent(detail, "storyline", string(game.get("storyline")));
        putIfPresent(detail, "rating", game.get("rating"));
        putIfPresent(detail, "ratingCount", game.get("rating_count"));
        putIfPresent(detail, "criticRating", game.get("aggregated_rating"));
        putIfPresent(detail, "criticRatingCount", game.get("aggregated_rating_count"));

        List<Map<String, Object>> companies = new ArrayList<>();
        for (Map<String, Object> involved : nested(game.get("involved_companies"), MAX_COMPANIES)) {
            String name = involved.get("company") instanceof Map<?, ?> company
                    ? string(company.get("name"))
                    : null;
            if (name == null) {
                continue;
            }
            // A company can be both, and is worth naming twice rather than picking one.
            companies.add(Map.of("name", name, "role", roleOf(involved)));
        }
        putIfAny(detail, "companies", companies);

        putIfAny(detail, "engines", names(game.get("game_engines"), MAX_TAGS));
        putIfAny(detail, "modes", names(game.get("game_modes"), MAX_TAGS));
        putIfAny(detail, "perspectives", names(game.get("player_perspectives"), MAX_TAGS));
        putIfAny(detail, "themes", names(game.get("themes"), MAX_TAGS));

        putIfAny(detail, "similar", related(game.get("similar_games"), "Similar", MAX_RELATED));

        List<Map<String, Object>> related = new ArrayList<>();
        related.addAll(related(game.get("dlcs"), "DLC", MAX_RELATED));
        related.addAll(related(game.get("expansions"), "Expansion", MAX_RELATED));
        putIfAny(detail, "related", related);

        List<Map<String, Object>> videos = new ArrayList<>();
        for (Map<String, Object> video : nested(game.get("videos"), MAX_VIDEOS)) {
            String id = string(video.get("video_id"));
            if (id != null) {
                videos.add(Map.of("id", id, "name", String.valueOf(string(video.get("name")))));
            }
        }
        putIfAny(detail, "videos", videos);

        List<String> screenshots = new ArrayList<>();
        for (Map<String, Object> shot : nested(game.get("screenshots"), MAX_SCREENSHOTS)) {
            String id = string(shot.get("image_id"));
            if (id != null) {
                screenshots.add(IMAGE_BASE + "t_screenshot_big/" + id + ".jpg");
            }
        }
        putIfAny(detail, "screenshots", screenshots);

        List<Map<String, Object>> websites = new ArrayList<>();
        for (Map<String, Object> site : nested(game.get("websites"), MAX_WEBSITES)) {
            String url = string(site.get("url"));
            String label = site.get("type") instanceof Map<?, ?> type ? string(type.get("type")) : null;
            if (url != null) {
                websites.add(Map.of("site", label == null ? "Website" : label, "url", url));
            }
        }
        putIfAny(detail, "websites", websites);

        return detail;
    }

    private String roleOf(Map<String, Object> involved) {
        boolean developer = Boolean.TRUE.equals(involved.get("developer"));
        boolean publisher = Boolean.TRUE.equals(involved.get("publisher"));

        if (developer && publisher) {
            return "Developer & Publisher";
        }
        if (developer) {
            return "Developer";
        }
        return publisher ? "Publisher" : "Involved";
    }

    private List<Map<String, Object>> related(Object raw, String relation, int limit) {
        List<Map<String, Object>> games = new ArrayList<>();

        for (Map<String, Object> game : nested(raw, limit)) {
            String name = string(game.get("name"));
            if (name == null) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", string(game.get("id")));
            entry.put("name", name);
            entry.put("relation", relation);
            if (game.get("cover") instanceof Map<?, ?> cover && cover.get("image_id") != null) {
                entry.put("cover", IMAGE_BASE + "t_cover_big/" + cover.get("image_id") + ".jpg");
            }
            games.add(entry);
        }
        return games;
    }

    private List<String> names(Object raw, int limit) {
        return nested(raw, limit).stream()
                .map(entry -> string(entry.get("name")))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> nested(Object raw, int limit) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(entry -> (Map<String, Object>) entry)
                .limit(limit)
                .toList();
    }

    private void putIfAny(Map<String, Object> detail, String key, List<?> values) {
        if (!values.isEmpty()) {
            detail.put(key, values);
        }
    }

    @Override
    public List<FilterField> discoverFilters(MediaType mediaType) {
        return IgdbFilters.fields(named(genres, client::genres), named(platforms, this::fetchPlatforms), LocalDate.now());
    }

    @Override
    public BrowseResults discover(MediaType mediaType, DiscoverFilters filters, int page, int size) {
        List<Map<String, Object>> games = client.discoverGames(
                filters.one("q"),
                IgdbFilters.where(filters, Instant.now().getEpochSecond()),
                (page - 1) * size,
                size);

        // A filtered page is one request, so hasMore is whether it came back full.
        return new BrowseResults(toSearchResults(games), games.size() == size);
    }

    private List<Map<String, Object>> fetchPlatforms() {
        return client.platforms(IgdbFilters.PLATFORM_IDS);
    }

    /**
     * A lookup's options, fetched once and kept. They are the same answer for everyone and
     * change about never, so asking IGDB again on every visit spends a request from a budget
     * of four a second to be told the same two dozen names.
     *
     * <p>A failure leaves the list empty and uncached, which drops that one control from the
     * bar rather than showing an empty one; the next visit tries again.
     */
    private List<FilterOption> named(AtomicReference<List<FilterOption>> held, Supplier<List<Map<String, Object>>> fetch) {
        List<FilterOption> known = held.get();
        if (known != null) {
            return known;
        }

        try {
            List<FilterOption> options = fetch.get().stream()
                    .map(row -> new FilterOption(string(row.get("id")), string(row.get("name"))))
                    .filter(option -> option.value() != null && option.label() != null)
                    .toList();

            if (!options.isEmpty()) {
                held.set(options);
            }
            return options;
        } catch (RuntimeException e) {
            log.warn("Could not fetch an IGDB filter list, leaving that filter out: {}", e.toString());
            return List.of();
        }
    }

    @Override
    public BrowseResults browse(MediaType mediaType, String shelfId, int page, int size) {
        long now = Instant.now().getEpochSecond();
        int offset = (page - 1) * size;

        List<Map<String, Object>> games =
                switch (shelfId) {
                    case SHELF_POPULAR -> client.browseGames(
                            "total_rating_count > %d & first_release_date < %d".formatted(POPULAR_VOTE_FLOOR, now),
                            "total_rating_count desc",
                            offset,
                            size);
                    case SHELF_TOP_RATED -> client.browseGames(
                            "total_rating_count > %d & first_release_date < %d".formatted(TOP_RATED_VOTE_FLOOR, now),
                            "total_rating desc",
                            offset,
                            size);
                    // Ascending, so the shelf opens on what is out next rather than in 2030.
                    case SHELF_COMING_SOON -> client.browseGames(
                            "first_release_date > %d".formatted(now), "first_release_date asc", offset, size);
                    case SHELF_RECENT -> client.browseGames(
                            "first_release_date > %d & first_release_date < %d"
                                    .formatted(now - RECENT_WINDOW.toSeconds(), now),
                            "first_release_date desc",
                            offset,
                            size);
                    default -> List.of();
                };

        // A full page is the only signal IGDB gives that there is another one behind it.
        return new BrowseResults(toSearchResults(games), games.size() == size);
    }

    /**
     * Listing rows as the shared shape, shared by the shelves and the filtered grid.
     *
     * <p>A game IGDB lists with no name is a stub record, and a nameless cover is worse than
     * a shorter shelf.
     */
    private List<ItemSearchResult> toSearchResults(List<Map<String, Object>> games) {
        return games.stream()
                .map(game -> new ItemSearchResult(
                        MediaType.GAME,
                        Source.IGDB,
                        string(game.get("id")),
                        string(game.get("name")),
                        coverUrl(game),
                        releaseDate(game)))
                .filter(result -> result.title() != null && !result.title().isBlank())
                .toList();
    }

    /**
     * Overrides the one-at-a-time default with IGDB's bulk form. Importing a library asks
     * for hundreds of ids at once, and at four requests per second the default would take
     * minutes for what this does in a couple of calls.
     */
    @Override
    public List<TrackableItemData> fetchByIds(Collection<String> externalIds) {
        List<TrackableItemData> items = new ArrayList<>();
        for (List<String> batch : IgdbClient.partition(externalIds)) {
            client.findGamesByIds(batch).stream().map(this::toItemData).forEach(items::add);
        }
        return items;
    }

    private TrackableItemData toItemData(Map<String, Object> game) {
        return new TrackableItemData(
                MediaType.GAME,
                Source.IGDB,
                string(game.get("id")),
                string(game.get("name")),
                coverUrl(game),
                releaseDate(game),
                itemState(game),
                metadata(game));
    }

    private String coverUrl(Map<String, Object> game) {
        if (!(game.get("cover") instanceof Map<?, ?> cover) || cover.get("url") == null) {
            return null;
        }
        String url = cover.get("url").toString().replace(THUMB_SIZE, COVER_SIZE);
        // IGDB returns protocol-relative URLs ("//images.igdb.com/...").
        return url.startsWith("//") ? "https:" + url : url;
    }

    private LocalDate releaseDate(Map<String, Object> game) {
        if (!(game.get("first_release_date") instanceof Number epochSeconds)) {
            return null;
        }
        return Instant.ofEpochSecond(epochSeconds.longValue())
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
    }

    private ItemState itemState(Map<String, Object> game) {
        if (game.get("status") instanceof Number status
                && List.of(STATUS_ALPHA, STATUS_BETA, STATUS_EARLY_ACCESS).contains(status.intValue())) {
            return ItemState.ONGOING;
        }

        LocalDate released = releaseDate(game);
        if (released == null || released.isAfter(LocalDate.now())) {
            return ItemState.UPCOMING;
        }
        return ItemState.RELEASED;
    }

    private Map<String, Object> metadata(Map<String, Object> game) {
        Map<String, Object> metadata = new HashMap<>();
        putIfPresent(metadata, "summary", game.get("summary"));
        putIfPresent(metadata, "platforms", names(game.get("platforms")));
        putIfPresent(metadata, "genres", names(game.get("genres")));

        // IGDB's total_rating is already the 0-100 scale used internally, so it carries
        // across untouched. Sources on a 0-10 scale convert in their own adapter.
        if (game.get("total_rating") instanceof Number rating) {
            metadata.put("externalRating", Math.round(rating.doubleValue()));
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
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .toList();
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        if (value != null) {
            target.put(key, value);
        }
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
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
