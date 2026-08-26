package dev.nexus.core.importing;

import dev.nexus.core.domain.TrackingStatus;
import java.util.Map;

/**
 * The words exports use for a shelf, mapped to the five this app keeps.
 *
 * <p>One table rather than one per module: every service picks from the same small pool of
 * English, and a games export saying "completed" means what an anime one saying "completed"
 * means. A module with a word of its own maps it before calling here.
 */
public final class CsvStatuses {

    private static final Map<String, TrackingStatus> BY_WORD = Map.ofEntries(
            Map.entry("watching", TrackingStatus.IN_PROGRESS),
            Map.entry("watched", TrackingStatus.COMPLETED),
            Map.entry("reading", TrackingStatus.IN_PROGRESS),
            Map.entry("currentlyreading", TrackingStatus.IN_PROGRESS),
            Map.entry("playing", TrackingStatus.IN_PROGRESS),
            Map.entry("current", TrackingStatus.IN_PROGRESS),
            Map.entry("inprogress", TrackingStatus.IN_PROGRESS),
            Map.entry("repeating", TrackingStatus.IN_PROGRESS),
            Map.entry("rewatching", TrackingStatus.IN_PROGRESS),
            Map.entry("completed", TrackingStatus.COMPLETED),
            Map.entry("complete", TrackingStatus.COMPLETED),
            Map.entry("finished", TrackingStatus.COMPLETED),
            Map.entry("read", TrackingStatus.COMPLETED),
            Map.entry("played", TrackingStatus.COMPLETED),
            Map.entry("beaten", TrackingStatus.COMPLETED),
            Map.entry("onhold", TrackingStatus.PAUSED),
            Map.entry("hold", TrackingStatus.PAUSED),
            Map.entry("paused", TrackingStatus.PAUSED),
            Map.entry("dropped", TrackingStatus.DROPPED),
            Map.entry("abandoned", TrackingStatus.DROPPED),
            Map.entry("quit", TrackingStatus.DROPPED),
            Map.entry("plantowatch", TrackingStatus.PLANNING),
            Map.entry("plantoread", TrackingStatus.PLANNING),
            Map.entry("plantoplay", TrackingStatus.PLANNING),
            Map.entry("planning", TrackingStatus.PLANNING),
            Map.entry("planned", TrackingStatus.PLANNING),
            Map.entry("watchlist", TrackingStatus.PLANNING),
            Map.entry("wanttoread", TrackingStatus.PLANNING),
            Map.entry("toread", TrackingStatus.PLANNING),
            Map.entry("backlog", TrackingStatus.PLANNING));

    private CsvStatuses() {}

    /**
     * The status this word means, or the fallback when the column is missing or says
     * something nobody here knows.
     *
     * <p>The fallback is always the caller's most harmless option rather than DROPPED: a
     * title whose state cannot be read belongs on the shelf, not in the bin.
     */
    public static TrackingStatus of(String raw, TrackingStatus fallback) {
        if (raw == null) {
            return fallback;
        }
        String word = raw.toLowerCase().replaceAll("[^a-z]", "");
        return BY_WORD.getOrDefault(word, fallback);
    }
}
