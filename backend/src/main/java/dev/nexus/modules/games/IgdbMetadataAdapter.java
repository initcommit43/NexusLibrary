package dev.nexus.modules.games;

import dev.nexus.core.adapter.ItemSearchResult;
import dev.nexus.core.adapter.MetadataAdapter;
import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.util.ArrayList;
import java.util.Collection;
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
    public List<ItemSearchResult> search(String query, int limit) {
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
