package dev.nexus.modules.anime;

/** No AniList client id or secret is configured, so accounts cannot be connected. */
public class AniListNotConfiguredException extends RuntimeException {

    public AniListNotConfiguredException() {
        super("AniList is not configured");
    }
}
