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
    SYNCED
}
