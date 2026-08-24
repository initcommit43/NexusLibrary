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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pulls a reader's MAL lists, anime and manga alike.
 *
 * <p>Unlike AniList's, a MAL entry does not name its canonical item — the resolver has a
 * real join to do. Everything the matching might need travels along as hints: the
 * alternative titles and the episode, chapter and volume counts, so a fallback match
 * never has to call MAL a second time for what the list already said.
 */
@Component
public class MalLibraryImportAdapter implements LibraryImportAdapter {

    /** MAL scores are 1-10; zero is "unscored", which is a rating nobody meant to give. */
    private static final int RATING_MAX = 10;

    static final String HINT_MEDIA_TYPE = "mediaType";
    static final String HINT_TITLE_EN = "titleEn";
    static final String HINT_TITLE_JA = "titleJa";
    static final String HINT_EPISODES = "episodes";
    static final String HINT_CHAPTERS = "chapters";
    static final String HINT_VOLUMES = "volumes";

    private final MalClient client;

    public MalLibraryImportAdapter(MalClient client) {
        this.client = client;
    }

    @Override
    public Provider provider() {
        return Provider.MAL;
    }

    @Override
    public List<ImportedEntry> pullLibrary(ExternalAccount account) {
        List<ImportedEntry> entries = new ArrayList<>();
        client.fetchAnimeList(account).stream()
                .map(row -> toEntry(row, MediaType.ANIME))
                .filter(java.util.Objects::nonNull)
                .forEach(entries::add);
        client.fetchMangaList(account).stream()
                .map(row -> toEntry(row, MediaType.MANGA))
                .filter(java.util.Objects::nonNull)
                .forEach(entries::add);
        return entries;
    }

    private ImportedEntry toEntry(Map<String, Object> row, MediaType mediaType) {
        if (!(row.get("node") instanceof Map<?, ?> rawNode) || rawNode.get("id") == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) rawNode;
        Map<String, Object> status = statusOf(row);
        boolean isManga = mediaType == MediaType.MANGA;

        return new ImportedEntry(
                itemRef(node, mediaType),
                status(string(status.get("status"))),
                number(status.get(isManga ? "num_chapters_read" : "num_episodes_watched")),
                positiveOrNull(number(node.get(isManga ? "num_chapters" : "num_episodes"))),
                isManga ? ProgressUnit.CHAPTERS : ProgressUnit.EPISODES,
                rating(status.get("score")),
                RATING_MAX,
                date(status.get("start_date")),
                date(status.get("finish_date")));
    }

    private ExternalItemRef itemRef(Map<String, Object> node, MediaType mediaType) {
        Map<String, String> hints = new HashMap<>();
        // The resolver joins per type: MAL numbers anime and manga separately, so a bare
        // id means nothing until it knows which list it came from.
        hints.put(HINT_MEDIA_TYPE, mediaType.name());

        if (node.get("alternative_titles") instanceof Map<?, ?> titles) {
            putIfPresent(hints, HINT_TITLE_EN, string(titles.get("en")));
            putIfPresent(hints, HINT_TITLE_JA, string(titles.get("ja")));
        }
        putCount(hints, HINT_EPISODES, node.get("num_episodes"));
        putCount(hints, HINT_CHAPTERS, node.get("num_chapters"));
        putCount(hints, HINT_VOLUMES, node.get("num_volumes"));

        return new ExternalItemRef(
                Provider.MAL, string(node.get("id")), string(node.get("title")), Map.copyOf(hints));
    }

    private Map<String, Object> statusOf(Map<String, Object> row) {
        if (row.get("list_status") instanceof Map<?, ?> status) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) status;
            return cast;
        }
        return Map.of();
    }

    /**
     * Rewatching and rereading are still watching and reading as far as a shelf is
     * concerned; MAL keeps them as flags on the status, which nothing here has a place for.
     */
    private TrackingStatus status(String malStatus) {
        return switch (String.valueOf(malStatus)) {
            case "watching", "reading" -> TrackingStatus.IN_PROGRESS;
            case "completed" -> TrackingStatus.COMPLETED;
            case "on_hold" -> TrackingStatus.PAUSED;
            case "dropped" -> TrackingStatus.DROPPED;
            default -> TrackingStatus.PLANNING;
        };
    }

    private Integer rating(Object score) {
        Integer value = number(score);
        return value == null || value == 0 ? null : value;
    }

    /** MAL reports a count it does not know as zero, which is no count at all. */
    private Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private LocalDate date(Object raw) {
        String value = string(raw);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            // MAL can know a date only in part ("2011" alone); a half-known day is no date.
            return null;
        }
    }

    private void putIfPresent(Map<String, String> hints, String key, String value) {
        if (value != null && !value.isBlank()) {
            hints.put(key, value);
        }
    }

    private void putCount(Map<String, String> hints, String key, Object value) {
        Integer count = number(value);
        if (count != null && count > 0) {
            hints.put(key, String.valueOf(count));
        }
    }

    private Integer number(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
