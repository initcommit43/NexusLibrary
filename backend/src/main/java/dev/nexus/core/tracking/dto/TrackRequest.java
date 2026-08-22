package dev.nexus.core.tracking.dto;

import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackingStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Starts tracking an item, caching it on first sighting. */
public record TrackRequest(
        @NotNull Source source,
        @NotBlank @Size(max = 64) String externalId,
        @NotNull TrackingStatus status,
        @Min(0) @Max(100) Short rating,
        @PositiveOrZero Integer progressCurrent,
        @PositiveOrZero Integer progressMax,
        ProgressUnit progressUnit,
        LocalDate startedAt,
        LocalDate finishedAt,
        Boolean favorite,
        @Size(max = 5000) String notes) {}
