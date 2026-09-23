package dev.nexus.core.tracking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.domain.UserEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record TrackedItemResponse(
        Long id,
        MediaType mediaType,
        Source source,
        String externalId,
        String title,
        String coverUrl,
        LocalDate releaseDate,
        /** Whether the source files it as adult: what the reader's blur setting applies to. */
        boolean adult,
        Map<String, Object> metadata,
        TrackingStatus status,
        Short rating,
        Integer progressCurrent,
        Integer progressMax,
        ProgressUnit progressUnit,
        // Carries the per-user shape a module needs and core does not model — today the
        // achievements a player has unlocked.
        Map<String, Object> progressExtra,
        LocalDate startedAt,
        LocalDate finishedAt,
        boolean favorite,
        /** Where it sits among the reader's favourites, null while they are in default order. */
        Integer favoriteRank,
        /** Which service this entry was imported from, if it was not added by hand. */
        Provider importedFrom,
        String notes,
        /** Times gone through again after the first: a rewatch, a replay, a reread. */
        int repeatCount,
        /** Stored for the reader to set; nothing is shown to anyone else yet, so nothing reads it. */
        @JsonProperty("private") boolean isPrivate,
        /** Stored for the reader to set; the status lists do not filter on it yet. */
        boolean hiddenFromStatusLists,
        /** When the entry last changed here — an edit, or an import that moved something. */
        Instant updatedAt,
        /**
         * When anything last happened to this title — an episode logged on the service it was
         * imported from, a change made here. Null for a title nothing has happened to yet.
         *
         * <p>Distinct from {@code updatedAt}, which is when the row was last written: an
         * import writes a whole library at one moment and says nothing about when the reader
         * was last at any of it.
         */
        Instant lastActivityAt) {

    public static TrackedItemResponse from(UserEntry entry) {
        return from(entry, null);
    }

    public static TrackedItemResponse from(UserEntry entry, Instant lastActivityAt) {
        TrackableItem item = entry.getItem();
        return new TrackedItemResponse(
                entry.getId(),
                item.getMediaType(),
                item.getSource(),
                item.getExternalId(),
                item.getTitle(),
                item.getCoverUrl(),
                item.getReleaseDate(),
                item.isAdult(),
                item.getMetadata(),
                entry.getStatus(),
                entry.getRating(),
                entry.getProgressCurrent(),
                entry.getProgressMax(),
                entry.getProgressUnit(),
                entry.getProgressExtra(),
                entry.getStartedAt(),
                entry.getFinishedAt(),
                entry.isFavorite(),
                entry.getFavoriteRank(),
                entry.getImportedFrom(),
                entry.getNotes(),
                entry.getRepeatCount(),
                entry.isPrivate(),
                entry.isHiddenFromStatusLists(),
                entry.getUpdatedAt(),
                lastActivityAt);
    }
}
