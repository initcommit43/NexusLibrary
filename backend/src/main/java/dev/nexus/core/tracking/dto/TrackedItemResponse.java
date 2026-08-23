package dev.nexus.core.tracking.dto;

import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.domain.UserEntry;
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
        /** Which service this entry was imported from, if it was not added by hand. */
        Provider importedFrom,
        String notes) {

    public static TrackedItemResponse from(UserEntry entry) {
        TrackableItem item = entry.getItem();
        return new TrackedItemResponse(
                entry.getId(),
                item.getMediaType(),
                item.getSource(),
                item.getExternalId(),
                item.getTitle(),
                item.getCoverUrl(),
                item.getReleaseDate(),
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
                entry.getImportedFrom(),
                entry.getNotes());
    }
}
