package dev.nexus.core.domain;

/** Drives cache staleness: a released item never changes, an ongoing one does. */
public enum ItemState {
    RELEASED,
    ONGOING,
    UPCOMING
}
