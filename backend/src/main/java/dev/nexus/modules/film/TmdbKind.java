package dev.nexus.modules.film;

import dev.nexus.core.domain.MediaType;
import java.util.Optional;

/**
 * TMDB's two id spaces, and the prefix that keeps them apart in ours.
 *
 * <p>Films and shows are numbered independently: {@code /movie/550} is Fight Club and {@code
 * /tv/550} is something else entirely. A bare TMDB id is therefore not an identity — it
 * collides with the other kind under {@code trackable_item}'s unique {@code (source,
 * external_id)}, which would file a show over a film of the same number.
 *
 * <p>So every id this module stores carries its kind: {@code movie:550}, {@code tv:1396}.
 * That is also what makes {@link dev.nexus.core.adapter.MetadataAdapter#fetchById} possible
 * at all here — it is handed an id and no media type, and TMDB has no one endpoint that
 * answers for both.
 */
public enum TmdbKind {
    MOVIE(MediaType.MOVIE, "movie"),
    SHOW(MediaType.SHOW, "tv");

    private static final String SEPARATOR = ":";

    private final MediaType mediaType;
    /** TMDB's own word for this kind, used in both the path and the id prefix. */
    private final String path;

    TmdbKind(MediaType mediaType, String path) {
        this.mediaType = mediaType;
        this.path = path;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    public String path() {
        return path;
    }

    /** The id core stores and hands back to {@code fetchById}. */
    public String externalId(Object tmdbId) {
        return path + SEPARATOR + tmdbId;
    }

    public static TmdbKind of(MediaType mediaType) {
        return switch (mediaType) {
            case MOVIE -> MOVIE;
            case SHOW -> SHOW;
            default -> throw new IllegalArgumentException("TMDB does not serve " + mediaType);
        };
    }

    /** Empty for anything not shaped like one of ours, so a stray id reads as "not found". */
    public static Optional<TmdbKind> ofExternalId(String externalId) {
        if (externalId == null) {
            return Optional.empty();
        }
        int separator = externalId.indexOf(SEPARATOR);
        if (separator < 0) {
            return Optional.empty();
        }
        String prefix = externalId.substring(0, separator);
        for (TmdbKind kind : values()) {
            if (kind.path.equals(prefix)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /** The TMDB-side id, with our prefix taken back off. */
    public static String tmdbId(String externalId) {
        return externalId.substring(externalId.indexOf(SEPARATOR) + 1);
    }
}
