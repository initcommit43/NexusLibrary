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
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reads an AniList export from a CSV.
 *
 * <p>Stricter than the other three about ids, and it has to be: {@link AniListDirectResolver}
 * treats a provider id as canonical outright, so an id that is wrong — or that is really a
 * MyAnimeList id under an "id" heading — would not fail to match, it would match the wrong
 * title silently. Only a column that names AniList is accepted, and a row without one is
 * skipped rather than guessed at.
 *
 * <p>Scores are the other trap: AniList lets a reader choose a display scale, so exports
 * carry 0-10, 0-100 or five stars depending on whose settings they came from. The scale is
 * decided per file, from the largest score in it, rather than per row.
 */
@Component
public class AniListCsvImportAdapter implements CsvImportAdapter {

    private static final String[] ID_COLUMNS = {"anilist id", "anilist_id", "anilistid", "media id", "media_id"};
    private static final String[] SCORE_COLUMNS = {"score", "rating", "my score", "my_score"};

    /** AniList's own storage scale, and what a 0-100 export is already on. */
    private static final int RATING_MAX_HUNDRED = 100;

    private static final int RATING_MAX_TEN = 10;

    @Override
    public Provider provider() {
        return Provider.ANILIST;
    }

    @Override
    public List<ImportedEntry> parse(CsvTable table) {
        if (!table.has(ID_COLUMNS)) {
            throw new CsvFormatException(
                    "That file has no AniList id column. An AniList export needs one named for AniList — "
                            + "anilist_id or media_id — because an id from anywhere else would match the wrong title.");
        }

        int ratingMax = ratingScaleOf(table);
        List<ImportedEntry> entries = new ArrayList<>();

        for (CsvTable.Row row : table.rows()) {
            String anilistId = row.value(ID_COLUMNS);
            if (anilistId == null) {
                continue;
            }

            boolean isManga = mediaTypeOf(row) == MediaType.MANGA;
            Integer score = score(row);

            entries.add(new ImportedEntry(
                    new ExternalItemRef(Provider.ANILIST, anilistId, title(row, anilistId)),
                    CsvStatuses.of(row.value("status", "list", "shelf"), TrackingStatus.PLANNING),
                    row.number("progress", "episodes watched", "chapters read", "episodes", "chapters"),
                    isManga ? row.number("total chapters", "chapters total") : row.number("total episodes", "episodes total"),
                    isManga ? ProgressUnit.CHAPTERS : ProgressUnit.EPISODES,
                    score,
                    score == null ? null : ratingMax,
                    row.date("started at", "started", "start date"),
                    row.date("completed at", "finished", "finish date")));
        }
        return entries;
    }

    /**
     * The largest score in the file decides the scale for all of it. Judging row by row would
     * read a 7 out of 100 as a 7 out of 10 and turn a poor score into a good one.
     */
    private int ratingScaleOf(CsvTable table) {
        int highest = 0;
        for (CsvTable.Row row : table.rows()) {
            Integer score = row.number(SCORE_COLUMNS);
            if (score != null && score > highest) {
                highest = score;
            }
        }
        return highest > RATING_MAX_TEN ? RATING_MAX_HUNDRED : RATING_MAX_TEN;
    }

    private MediaType mediaTypeOf(CsvTable.Row row) {
        String declared = row.value("type", "format", "media type", "mediatype");
        if (declared != null) {
            String word = declared.toLowerCase();
            if (word.contains("manga") || word.contains("novel") || word.contains("oneshot")) {
                return MediaType.MANGA;
            }
        }
        return row.value("chapters read", "total chapters") != null && row.value("episodes watched") == null
                ? MediaType.MANGA
                : MediaType.ANIME;
    }

    /** An unscored entry is written as 0 by every exporter that writes one at all. */
    private Integer score(CsvTable.Row row) {
        Integer score = row.number(SCORE_COLUMNS);
        return score == null || score == 0 ? null : score;
    }

    private String title(CsvTable.Row row, String fallback) {
        String title = row.value("title", "title romaji", "romaji", "title english", "english", "name");
        return title == null ? fallback : title;
    }
}
