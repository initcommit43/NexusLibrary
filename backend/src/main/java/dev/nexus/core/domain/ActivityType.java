package dev.nexus.core.domain;

public enum ActivityType {
    ADDED,
    STATUS_CHANGE,
    PROGRESS,
    RATED,
    REVIEWED,

    /** A library arriving from a provider: one event for the run, not one per title. */
    IMPORTED,

    /** A later run of the same provider, recorded only when it actually changed something. */
    SYNCED,

    /**
     * Something a provider recorded rather than this app: an episode watched on AniList, read
     * back out of {@link ProviderActivity}. Never written to the activity table — the feed
     * reads the two side by side, and this is what the imported half calls itself.
     */
    EXTERNAL
}
