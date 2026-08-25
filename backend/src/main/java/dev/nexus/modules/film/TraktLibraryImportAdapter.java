package dev.nexus.modules.film;

import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.adapter.LibraryImportAdapter;
import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pulls a reader's Trakt library — watched, watchlisted and rated, films and shows alike.
 *
 * <p>Trakt has no single list with a status column on it. What a person's shelf looks like
 * has to be assembled from separate endpoints that each say one thing: watched history says
 * completed, the watchlist says planned, ratings say only a number. So the six responses are
 * merged into one draft per title, in that order of authority — having watched something
 * outranks having meant to.
 *
 * <p>Two things Trakt does not report at all: pausing and dropping. Neither exists in its
 * model, so no entry is ever imported as {@code PAUSED} or {@code DROPPED} — inventing
 * either from an unfinished show would put a judgement on the reader's shelf that they
 * never made.
 */
@Component
public class TraktLibraryImportAdapter implements LibraryImportAdapter {

    /** Trakt rates on 1-10; core converts to its own scale during the upsert. */
    private static final int RATING_MAX = 10;

    /** Specials live in season 0 and are excluded from {@code aired_episodes}. */
    private static final int SPECIALS_SEASON = 0;

    private final TraktClient client;

    public TraktLibraryImportAdapter(TraktClient client) {
        this.client = client;
    }

    @Override
    public Provider provider() {
        return Provider.TRAKT;
    }

    @Override
    public List<ImportedEntry> pullLibrary(ExternalAccount account) {
        Map<String, Draft> drafts = new LinkedHashMap<>();

        for (Map<String, Object> row : client.watchedMovies(account)) {
            watchedMovie(drafts, row);
        }
        for (Map<String, Object> row : client.watchedShows(account)) {
            watchedShow(drafts, row);
        }
        for (Map<String, Object> row : client.watchlistMovies(account)) {
            planned(drafts, TmdbKind.MOVIE, node(row, "movie"));
        }
        for (Map<String, Object> row : client.watchlistShows(account)) {
            planned(drafts, TmdbKind.SHOW, node(row, "show"));
        }
        for (Map<String, Object> row : client.ratedMovies(account)) {
            rated(drafts, TmdbKind.MOVIE, node(row, "movie"), row.get("rating"));
        }
        for (Map<String, Object> row : client.ratedShows(account)) {
            rated(drafts, TmdbKind.SHOW, node(row, "show"), row.get("rating"));
        }

        return drafts.values().stream().map(Draft::toEntry).toList();
    }

    /** A film in the watched history has been seen; Trakt keeps no half-watched films. */
    private void watchedMovie(Map<String, Draft> drafts, Map<String, Object> row) {
        Draft draft = draftFor(drafts, TmdbKind.MOVIE, node(row, "movie"));
        if (draft == null) {
            return;
        }
        draft.status = TrackingStatus.COMPLETED;
        draft.finishedAt = date(row.get("last_watched_at"));
    }

    /**
     * A show is as far along as the episodes watched of the ones that have aired. Specials
     * are left out of the count on purpose: they are not in {@code aired_episodes} either,
     * and counting them would push a viewer past the end of a series they have not finished.
     */
    private void watchedShow(Map<String, Draft> drafts, Map<String, Object> row) {
        Map<String, Object> show = node(row, "show");
        Draft draft = draftFor(drafts, TmdbKind.SHOW, show);
        if (draft == null) {
            return;
        }

        int watched = watchedEpisodes(row.get("seasons"));
        Integer aired = number(show.get("aired_episodes"));

        draft.progressCurrent = watched;
        draft.progressMax = aired;
        draft.progressUnit = ProgressUnit.EPISODES;

        boolean finished = aired != null && aired > 0 && watched >= aired;
        draft.status = finished ? TrackingStatus.COMPLETED : TrackingStatus.IN_PROGRESS;
        draft.finishedAt = finished ? date(row.get("last_watched_at")) : null;
    }

    /** The watchlist only speaks for titles nothing else has spoken for. */
    private void planned(Map<String, Draft> drafts, TmdbKind kind, Map<String, Object> item) {
        Draft draft = draftFor(drafts, kind, item);
        if (draft != null && draft.status == null) {
            draft.status = TrackingStatus.PLANNING;
        }
    }

    /**
     * A rating is a number, not a claim to have watched anything: someone can rate a film
     * off a friend's recommendation. So it enriches an entry the watched history or the
     * watchlist already put on the shelf, and never creates one on its own.
     */
    private void rated(Map<String, Draft> drafts, TmdbKind kind, Map<String, Object> item, Object rating) {
        String key = keyFor(kind, item);
        Draft draft = key == null ? null : drafts.get(key);
        if (draft != null) {
            draft.rawRating = number(rating);
        }
    }

    private int watchedEpisodes(Object raw) {
        if (!(raw instanceof List<?> seasons)) {
            return 0;
        }
        int watched = 0;
        for (Object entry : seasons) {
            if (!(entry instanceof Map<?, ?> season)) {
                continue;
            }
            Integer number = number(season.get("number"));
            if (number != null && number == SPECIALS_SEASON) {
                continue;
            }
            if (season.get("episodes") instanceof List<?> episodes) {
                watched += episodes.size();
            }
        }
        return watched;
    }

    private Draft draftFor(Map<String, Draft> drafts, TmdbKind kind, Map<String, Object> item) {
        String key = keyFor(kind, item);
        return key == null ? null : drafts.computeIfAbsent(key, ignored -> new Draft(kind, item));
    }

    /**
     * Trakt numbers films and shows separately, exactly as TMDB does, so a bare Trakt id is
     * not an identity either — the kind travels with it.
     */
    private String keyFor(TmdbKind kind, Map<String, Object> item) {
        String traktId = id(item, "trakt");
        return traktId == null ? null : kind.externalId(traktId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> node(Map<String, Object> row, String key) {
        return row.get(key) instanceof Map<?, ?> node ? (Map<String, Object>) node : Map.of();
    }

    private String id(Map<String, Object> item, String key) {
        if (!(item.get("ids") instanceof Map<?, ?> ids) || ids.get(key) == null) {
            return null;
        }
        String value = ids.get(key).toString();
        return value.isBlank() ? null : value;
    }

    /** Trakt timestamps are ISO-8601 instants; a shelf only cares about the day. */
    private LocalDate date(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw.toString()).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Integer number(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    /** One title's shelf state, assembled from however many of the six responses named it. */
    private final class Draft {

        private final TmdbKind kind;
        private final Map<String, Object> item;

        private TrackingStatus status;
        private Integer progressCurrent;
        private Integer progressMax;
        private ProgressUnit progressUnit;
        private Integer rawRating;
        private LocalDate finishedAt;

        private Draft(TmdbKind kind, Map<String, Object> item) {
            this.kind = kind;
            this.item = item;
        }

        private ImportedEntry toEntry() {
            return new ImportedEntry(
                    itemRef(),
                    status == null ? TrackingStatus.PLANNING : status,
                    progressCurrent,
                    progressMax,
                    progressUnit,
                    rawRating,
                    rawRating == null ? null : RATING_MAX,
                    null,
                    finishedAt);
        }

        /**
         * The TMDB id travels as a hint, which is the whole reason this import needs no
         * matching: Trakt already records which TMDB title each of its own is.
         */
        private ExternalItemRef itemRef() {
            String tmdbId = id(item, "tmdb");
            Map<String, String> hints = tmdbId == null
                    ? Map.of()
                    : Map.of(TraktToTmdbResolver.TMDB_ID_HINT, tmdbId, TraktToTmdbResolver.KIND_HINT, kind.path());

            return new ExternalItemRef(Provider.TRAKT, keyFor(kind, item), title(), hints);
        }

        private String title() {
            Object title = item.get("title");
            return title == null ? keyFor(kind, item) : title.toString();
        }
    }
}
