package dev.nexus.modules.anime;

import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.importing.CsvFormatException;
import dev.nexus.core.importing.CsvImportAdapter;
import dev.nexus.core.importing.CsvStatuses;
import dev.nexus.core.importing.CsvTable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Reads a MyAnimeList export from a CSV.
 *
 * <p>MAL's own export is XML, so this reads the CSV the common converters produce — which
 * keep MAL's column names, {@code series_animedb_id} and {@code my_status} among them. The
 * MAL id is the one column that matters: {@link MalToAniListResolver} joins on it against
 * AniList exactly as it does for the API import, and falls back to title matching for what
 * the join misses. A row with neither an id nor a title is skipped.
 *
 * <p>Anime and manga are numbered separately at MAL, so every row says which it is — by its
 * type column where there is one, and otherwise by whether it counts chapters or episodes.
 * Getting that wrong would join an anime id against the manga list and match nothing.
 */
@Component
public class MalCsvImportAdapter implements CsvImportAdapter {

    /** MAL scores are 1-10; zero is "unscored", which is a rating nobody meant to give. */
    private static final int RATING_MAX = 10;

    @Override
    public Provider provider() {
        return Provider.MAL;
    }

    @Override
    public List<ImportedEntry> parse(CsvTable table) {
        if (!table.has(
                "series_animedb_id", "series_mangadb_id", "mal id", "mal_id", "malid", "anime_id", "manga_id", "id")) {
            throw new CsvFormatException(
                    "That file has no MyAnimeList id column. An export needs one — series_animedb_id, "
                            + "mal_id or id — alongside the title and status.");
        }

        List<ImportedEntry> entries = new ArrayList<>();
        for (CsvTable.Row row : table.rows()) {
            String malId = row.value(
                    "series_animedb_id", "series_mangadb_id", "mal id", "mal_id", "malid", "anime_id", "manga_id", "id");
            if (malId == null) {
                continue;
            }

            MediaType mediaType = mediaTypeOf(row);
            boolean isManga = mediaType == MediaType.MANGA;
            Integer progress = isManga
                    ? row.number("my_read_chapters", "chapters read", "chapters", "progress")
                    : row.number("my_watched_episodes", "episodes watched", "episodes", "progress");

            entries.add(new ImportedEntry(
                    itemRef(row, malId, mediaType),
                    CsvStatuses.of(row.value("my_status", "status", "shelf"), TrackingStatus.PLANNING),
                    progress,
                    isManga
                            ? row.number("series_chapters", "total chapters")
                            : row.number("series_episodes", "total episodes"),
                    isManga ? ProgressUnit.CHAPTERS : ProgressUnit.EPISODES,
                    score(row),
                    score(row) == null ? null : RATING_MAX,
                    row.date("my_start_date", "start date", "started"),
                    row.date("my_finish_date", "finish date", "finished", "completed at")));
        }
        return entries;
    }

    /**
     * Everything the resolver's fallback might need travels along, exactly as it does on the
     * API import — the type it belongs to, the titles, and the counts a title match weighs.
     */
    private ExternalItemRef itemRef(CsvTable.Row row, String malId, MediaType mediaType) {
        Map<String, String> hints = new HashMap<>();
        hints.put(MalLibraryImportAdapter.HINT_MEDIA_TYPE, mediaType.name());
        putIfPresent(hints, MalLibraryImportAdapter.HINT_TITLE_EN, row.value("series_title_english", "english title"));
        putIfPresent(hints, MalLibraryImportAdapter.HINT_TITLE_JA, row.value("series_title_japanese", "japanese title"));
        putIfPresent(hints, MalLibraryImportAdapter.HINT_EPISODES, row.value("series_episodes", "total episodes"));
        putIfPresent(hints, MalLibraryImportAdapter.HINT_CHAPTERS, row.value("series_chapters", "total chapters"));
        putIfPresent(hints, MalLibraryImportAdapter.HINT_VOLUMES, row.value("series_volumes", "total volumes"));

        String title = row.value("series_title", "title", "name");
        return new ExternalItemRef(Provider.MAL, malId, title == null ? malId : title, Map.copyOf(hints));
    }

    /**
     * The type column where the export has one; otherwise what the row counts. A file that
     * only ever mentions chapters is a manga list whatever it calls itself.
     */
    private MediaType mediaTypeOf(CsvTable.Row row) {
        String declared = row.value("type", "series_type", "media type", "mediatype");
        if (declared != null) {
            String word = declared.toLowerCase();
            if (word.contains("manga") || word.contains("novel") || word.contains("manhwa")) {
                return MediaType.MANGA;
            }
            if (word.contains("anime") || word.contains("tv") || word.contains("movie") || word.contains("ova")) {
                return MediaType.ANIME;
            }
        }
        boolean countsChapters = row.value("my_read_chapters", "chapters read", "chapters", "series_chapters") != null;
        boolean countsEpisodes = row.value("my_watched_episodes", "episodes watched", "episodes", "series_episodes") != null;
        return countsChapters && !countsEpisodes ? MediaType.MANGA : MediaType.ANIME;
    }

    /** MAL writes an unscored entry as 0, which is not an opinion anyone expressed. */
    private Integer score(CsvTable.Row row) {
        Integer score = row.number("my_score", "score", "rating", "my rating");
        return score == null || score == 0 ? null : score;
    }

    private void putIfPresent(Map<String, String> hints, String key, String value) {
        if (value != null && !value.isBlank()) {
            hints.put(key, value);
        }
    }
}
