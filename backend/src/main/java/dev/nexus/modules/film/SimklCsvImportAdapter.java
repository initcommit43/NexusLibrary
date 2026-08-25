package dev.nexus.modules.film;

import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.importing.CsvFormatException;
import dev.nexus.core.importing.CsvImportAdapter;
import dev.nexus.core.importing.CsvStatuses;
import dev.nexus.core.importing.CsvTable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Reads a Simkl export from a CSV — the route that does not need an account connected at all,
 * which is the whole point of having it.
 *
 * <p>What resolves a row is not Simkl's own id but the TMDB or IMDb id beside it:
 * {@link SimklToTmdbResolver} reads exactly the hints it reads on the API import, TMDB id
 * first and IMDb id as the fallback. A row with neither cannot be placed and goes to the
 * unmatched report rather than being guessed at by title.
 *
 * <p>Films and shows are told apart by a type column where the export has one, and otherwise
 * by whether the row counts episodes. Guessing wrong would file a show under a film's TMDB
 * number, which is the collision {@link TmdbKind} exists to prevent.
 */
@Component
public class SimklCsvImportAdapter implements CsvImportAdapter {

    /** Simkl rates on 1-10, the same as its API. */
    private static final int RATING_MAX = 10;

    private static final String[] TMDB_COLUMNS = {"tmdb", "tmdb id", "tmdb_id", "themoviedb"};
    private static final String[] IMDB_COLUMNS = {"imdb", "imdb id", "imdb_id"};
    private static final String[] SIMKL_COLUMNS = {"simkl id", "simkl_id", "simklid", "simkl"};
    private static final String[] EPISODE_COLUMNS = {
        "last episode watched", "lastepisodewatched", "episodes watched", "watched episodes"
    };

    @Override
    public Provider provider() {
        return Provider.SIMKL;
    }

    @Override
    public List<ImportedEntry> parse(CsvTable table) {
        if (!table.has(TMDB_COLUMNS) && !table.has(IMDB_COLUMNS)) {
            throw new CsvFormatException(
                    "That file has no TMDB or IMDb id column. Simkl's CSV backup carries them, and "
                            + "without one there is nothing to match a title against.");
        }

        List<ImportedEntry> entries = new ArrayList<>();
        for (CsvTable.Row row : table.rows()) {
            String tmdbId = row.value(TMDB_COLUMNS);
            String imdbId = row.value(IMDB_COLUMNS);
            TmdbKind kind = kindOf(row);
            boolean isShow = kind == TmdbKind.SHOW;
            Integer watched = isShow ? row.number(EPISODE_COLUMNS) : null;
            Integer rating = rating(row);

            ExternalItemRef itemRef = itemRef(row, kind, tmdbId, imdbId);
            if (itemRef == null) {
                continue;
            }

            entries.add(new ImportedEntry(
                    itemRef,
                    CsvStatuses.of(row.value("watchlist", "status", "watchlist status", "list"), TrackingStatus.PLANNING),
                    watched,
                    isShow ? row.number("total episodes", "episodes total", "total_episodes_count") : null,
                    watched == null ? null : ProgressUnit.EPISODES,
                    rating,
                    rating == null ? null : RATING_MAX,
                    null,
                    row.date("last watch date", "watcheddate", "last watched", "lastwatchedat")));
        }
        return entries;
    }

    /**
     * The id this row is filed under. Simkl's own id where the export carries one, so the
     * cross-reference recorded against the item matches what the API import records; the
     * TMDB or IMDb id otherwise, which is at least stable and unique.
     *
     * <p>A row carrying none of the three still becomes an entry rather than being dropped
     * here. It cannot resolve, so it lands in the unmatched report under its own title —
     * which is the whole point: a file of thirty titles that quietly imports twenty-eight
     * tells the reader nothing about the two.
     */
    private ExternalItemRef itemRef(CsvTable.Row row, TmdbKind kind, String tmdbId, String imdbId) {
        Map<String, String> hints = new LinkedHashMap<>();
        hints.put(SimklToTmdbResolver.KIND_HINT, kind.path());
        if (tmdbId != null) {
            hints.put(SimklToTmdbResolver.TMDB_ID_HINT, tmdbId);
        }
        if (imdbId != null) {
            hints.put(SimklToTmdbResolver.IMDB_ID_HINT, imdbId);
        }

        String simklId = row.value(SIMKL_COLUMNS);
        String title = row.value("title", "name", "show", "movie");
        String identity = firstOf(simklId, tmdbId, imdbId, title);
        if (identity == null) {
            return null;
        }

        return new ExternalItemRef(
                Provider.SIMKL, kind.externalId(identity), title == null ? identity : title, Map.copyOf(hints));
    }

    private String firstOf(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * A type column where there is one, and otherwise the tell that a row counts episodes.
     * Only a show has an episode to have watched last.
     */
    private TmdbKind kindOf(CsvTable.Row row) {
        String declared = row.value("type", "media type", "mediatype", "kind", "endpoint_type");
        if (declared != null) {
            String word = declared.toLowerCase();
            if (word.contains("show") || word.contains("tv") || word.contains("series") || word.contains("anime")) {
                return TmdbKind.SHOW;
            }
            if (word.contains("movie") || word.contains("film")) {
                return TmdbKind.MOVIE;
            }
        }
        return row.value(EPISODE_COLUMNS) != null ? TmdbKind.SHOW : TmdbKind.MOVIE;
    }

    /** Simkl leaves an unrated title blank, and a zero is not a rating anyone gave. */
    private Integer rating(CsvTable.Row row) {
        Integer rating = row.number("rating", "user rating", "my rating", "score");
        return rating == null || rating == 0 ? null : rating;
    }
}
