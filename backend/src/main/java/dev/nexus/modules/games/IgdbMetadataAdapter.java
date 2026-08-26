package dev.nexus.modules.games;

import dev.nexus.core.adapter.BrowseShelf;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class IgdbMetadataAdapter implements MetadataAdapter {

    /** IGDB serves thumbnails by default; the big variant is what a cover grid needs. */
    private static final String THUMB_SIZE = "t_thumb";
    private static final String COVER_SIZE = "t_cover_big";

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

    private final IgdbClient client;

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
    @Override
    public List<ItemSearchResult> browse(MediaType mediaType, String shelfId, int limit) {
        long now = Instant.now().getEpochSecond();

        List<Map<String, Object>> games =
                switch (shelfId) {
                    case SHELF_POPULAR -> client.browseGames(
                            "total_rating_count > %d & first_release_date < %d".formatted(POPULAR_VOTE_FLOOR, now),
                            "total_rating_count desc",
                            limit);
                    case SHELF_TOP_RATED -> client.browseGames(
                            "total_rating_count > %d & first_release_date < %d".formatted(TOP_RATED_VOTE_FLOOR, now),
                            "total_rating desc",
                            limit);
                    // Ascending, so the shelf opens on what is out next rather than in 2030.
                    case SHELF_COMING_SOON -> client.browseGames(
                            "first_release_date > %d".formatted(now), "first_release_date asc", limit);
                    case SHELF_RECENT -> client.browseGames(
                            "first_release_date > %d & first_release_date < %d"
                                    .formatted(now - RECENT_WINDOW.toSeconds(), now),
                            "first_release_date desc",
                            limit);
                    default -> List.of();
                };

        return games.stream()
                .map(game -> new ItemSearchResult(
                        MediaType.GAME,
                        Source.IGDB,
                        string(game.get("id")),
                        string(game.get("name")),
                        coverUrl(game),
                        releaseDate(game)))
                // A game IGDB lists with no name is a stub record, and a nameless cover is
                // worse than a shorter shelf.
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
}
