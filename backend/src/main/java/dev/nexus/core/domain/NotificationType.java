package dev.nexus.core.domain;

public enum NotificationType {

    /** An episode of something on the reader's shelf aired. */
    EPISODE_AIRED,

    /** Something related to what they watched appeared on the source — a season, a sequel. */
    TITLE_ADDED,

    /** A title on the reader's shelf went from upcoming to out — a manga starting publication. */
    RELEASE_STARTED
}
