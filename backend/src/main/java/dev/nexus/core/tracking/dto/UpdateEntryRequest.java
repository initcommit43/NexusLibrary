package dev.nexus.core.tracking.dto;

import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.TrackingStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * A partial update: every field is optional, and null means "leave unchanged". Clearing a
 * value is therefore not expressible here — no screen needs it yet.
 */
public record UpdateEntryRequest(
        TrackingStatus status,
        @Min(0) @Max(100) Short rating,
        @PositiveOrZero Integer progressCurrent,
        @PositiveOrZero Integer progressMax,
        ProgressUnit progressUnit,
        LocalDate startedAt,
        LocalDate finishedAt,
        Boolean favorite,
        @Size(max = 5000) String notes) {}
