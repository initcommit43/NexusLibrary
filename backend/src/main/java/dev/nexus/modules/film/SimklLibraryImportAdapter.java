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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Pulls a reader's Simkl library, films and shows alike.
 *
 * <p>Almost nothing is inferred here, which is the point: Simkl keeps a status per title in
 * the same vocabulary this app does — watching, plan to watch, on hold, completed, dropped —
 * so a shelf comes across as the reader arranged it rather than as a guess assembled from
 * separate watched, watchlist and ratings endpoints. Episode progress is a field too,
 * instead of a count of watched episodes weighed against how many have aired.
 *
 * <p>The one asymmetry is Simkl's own: films have no {@code watching} or {@code hold}
 * state, so a film is only ever planned, completed or dropped.
 */
@Component
public class SimklLibraryImportAdapter implements LibraryImportAdapter {

    /** Simkl rates on 1-10; core converts to its own scale during the upsert. */
    private static final int RATING_MAX = 10;

    private final SimklClient client;

    public SimklLibraryImportAdapter(SimklClient client) {
        this.client = client;
    }

    @Override
    public Provider provider() {
        return Provider.SIMKL;
    }

    @Override
    public List<ImportedEntry> pullLibrary(ExternalAccount account) {
        List<ImportedEntry> entries = new ArrayList<>();

        for (Map<String, Object> row : client.movies(account)) {
            ImportedEntry entry = toEntry(row, TmdbKind.MOVIE, "movie");
            if (entry != null) {
                entries.add(entry);
            }
        }
        for (Map<String, Object> row : client.shows(account)) {
            ImportedEntry entry = toEntry(row, TmdbKind.SHOW, "show");
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private ImportedEntry toEntry(Map<String, Object> row, TmdbKind kind, String nodeKey) {
        Map<String, Object> item = node(row, nodeKey);
        String simklId = id(item, "simkl");
        if (simklId == null) {
            return null;
        }

        TrackingStatus status = status(string(row.get("status")));
        boolean isShow = kind == TmdbKind.SHOW;

        return new ImportedEntry(
                itemRef(kind, item, simklId),
                status,
                isShow ? number(row.get("watched_episodes_count")) : null,
                isShow ? number(row.get("total_episodes_count")) : null,
                isShow ? ProgressUnit.EPISODES : null,
                rating(row.get("user_rating")),
                rating(row.get("user_rating")) == null ? null : RATING_MAX,
                null,
                status == TrackingStatus.COMPLETED ? date(row.get("last_watched_at")) : null);
    }

    /**
     * Simkl's vocabulary is this app's, one word apart. Anything unrecognised is treated as
     * planned rather than dropped: a title nobody can classify belongs on the shelf, not in
     * the bin.
     */
    private TrackingStatus status(String simklStatus) {
        return switch (String.valueOf(simklStatus)) {
            case "watching" -> TrackingStatus.IN_PROGRESS;
            case "completed" -> TrackingStatus.COMPLETED;
            case "hold" -> TrackingStatus.PAUSED;
            case "dropped" -> TrackingStatus.DROPPED;
            default -> TrackingStatus.PLANNING;
        };
    }

    /**
     * The TMDB id travels as a hint, which is what spares this import any matching. The IMDb
     * id goes too: Simkl knows one for almost everything, and it is what the resolver falls
     * back to when a TMDB id is missing.
     */
    private ExternalItemRef itemRef(TmdbKind kind, Map<String, Object> item, String simklId) {
        Map<String, String> hints = new LinkedHashMap<>();
        hints.put(SimklToTmdbResolver.KIND_HINT, kind.path());
        putIfPresent(hints, SimklToTmdbResolver.TMDB_ID_HINT, id(item, "tmdb"));
        putIfPresent(hints, SimklToTmdbResolver.IMDB_ID_HINT, id(item, "imdb"));

        return new ExternalItemRef(Provider.SIMKL, kind.externalId(simklId), title(item, simklId), Map.copyOf(hints));
    }

    private String title(Map<String, Object> item, String simklId) {
        Object title = item.get("title");
        return title == null || title.toString().isBlank() ? simklId : title.toString();
    }

    /** Simkl reports an unrated title as null, and 0 is not a rating anyone gave. */
    private Integer rating(Object raw) {
        Integer value = number(raw);
        return value == null || value == 0 ? null : value;
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

    /** Simkl timestamps are ISO-8601 instants; a shelf only cares about the day. */
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

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private Integer number(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
