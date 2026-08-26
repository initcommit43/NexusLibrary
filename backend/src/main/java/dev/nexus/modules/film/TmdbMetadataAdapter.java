package dev.nexus.modules.film;

import dev.nexus.core.adapter.BrowseResults;
import dev.nexus.core.adapter.BrowseShelf;
import dev.nexus.core.adapter.ItemSearchResult;
import dev.nexus.core.adapter.MetadataAdapter;
import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
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
    private static final Set<String> FINISHED_SHOW_STATUSES = Set.of("Ended", "Canceled", "Cancelled");

    private final TmdbClient client;
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


    @Override
    public List<BrowseShelf> browseShelves(MediaType mediaType) {
        return TmdbShelves.shelvesFor(TmdbKind.of(mediaType));
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

        List<ItemSearchResult> items = client.resultsOf(body).stream()
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
}
