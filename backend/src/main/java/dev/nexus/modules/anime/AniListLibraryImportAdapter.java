package dev.nexus.modules.anime;

import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.adapter.LibraryImportAdapter;
import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pulls a reader's AniList lists, anime and manga alike.
 *
 * <p>The simplest import there is: an AniList entry already names the canonical item, so
 * nothing has to be matched. The MyAnimeList import is where the guessing lives.
 */
@Component
public class AniListLibraryImportAdapter implements LibraryImportAdapter {

    /** AniList's own 0-100 scale, requested explicitly so a user's display format cannot change it. */
    private static final int RATING_MAX = 100;

    private final AniListClient client;

    public AniListLibraryImportAdapter(AniListClient client) {
        this.client = client;
    }

    @Override
    public Provider provider() {
        return Provider.ANILIST;
    }

    @Override
    public List<ImportedEntry> pullLibrary(ExternalAccount account) {
        List<ImportedEntry> entries = new ArrayList<>();
        for (MediaType mediaType : List.of(MediaType.ANIME, MediaType.MANGA)) {
            client.fetchList(account.getExternalUserId(), mediaType, account.getAccessToken()).stream()
                    .map(row -> toEntry(row, mediaType))
                    .filter(java.util.Objects::nonNull)
                    .forEach(entries::add);
        }
        return entries;
    }

    private ImportedEntry toEntry(Map<String, Object> row, MediaType mediaType) {
        if (!(row.get("media") instanceof Map<?, ?> media) || media.get("id") == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) media;
        boolean isManga = mediaType == MediaType.MANGA;

        return new ImportedEntry(
                itemRef(item),
                status(string(row.get("status"))),
                number(row.get("progress")),
                number(item.get(isManga ? "chapters" : "episodes")),
                isManga ? ProgressUnit.CHAPTERS : ProgressUnit.EPISODES,
                rating(row.get("score")),
                RATING_MAX,
                fuzzyDate(row.get("startedAt")),
                fuzzyDate(row.get("completedAt")));
    }

    /**
     * The MAL id travels along as a hint. It costs nothing here and saves the MyAnimeList
     * import a lookup for anything already on the shelf.
     */
    private ExternalItemRef itemRef(Map<String, Object> media) {
        Map<String, String> hints = media.get("idMal") instanceof Number malId
                ? Map.of("malId", String.valueOf(malId.intValue()))
                : Map.<String, String>of();

        return new ExternalItemRef(Provider.ANILIST, string(media.get("id")), title(media), hints);
    }

    private String title(Map<String, Object> media) {
        if (!(media.get("title") instanceof Map<?, ?> titles)) {
            return string(media.get("id"));
        }
        return List.of("english", "romaji", "native").stream()
                .map(key -> string(titles.get(key)))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseGet(() -> string(media.get("id")));
    }

    /**
     * REPEATING is a rewatch, which is still watching as far as a shelf is concerned —
     * the distinction is AniList's, and nothing here has a place to keep it.
     */
    private TrackingStatus status(String anilistStatus) {
        return switch (String.valueOf(anilistStatus)) {
            case "CURRENT", "REPEATING" -> TrackingStatus.IN_PROGRESS;
            case "COMPLETED" -> TrackingStatus.COMPLETED;
            case "PAUSED" -> TrackingStatus.PAUSED;
            case "DROPPED" -> TrackingStatus.DROPPED;
            default -> TrackingStatus.PLANNING;
        };
    }

    /** AniList reports an unscored entry as zero, which is a rating nobody meant to give. */
    private Integer rating(Object score) {
        Integer value = number(score);
        return value == null || value == 0 ? null : value;
    }

    /** A date AniList knows only in part is no date: a half-known day would be invented. */
    private LocalDate fuzzyDate(Object raw) {
        if (!(raw instanceof Map<?, ?> date)) {
            return null;
        }
        Integer year = number(date.get("year"));
        Integer month = number(date.get("month"));
        Integer day = number(date.get("day"));
        return year == null || month == null || day == null ? null : LocalDate.of(year, month, day);
    }

    private Integer number(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
