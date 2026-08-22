package dev.nexus.core.adapter;

import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.TrackingStatus;
import java.time.LocalDate;

/**
 * One row of a user's external library, as the provider reports it. Ratings stay on the
 * provider's own scale here; core converts to 0-100 once, during the upsert.
 */
public record ImportedEntry(
        ExternalItemRef itemRef,
        TrackingStatus status,
        Integer progressCurrent,
        Integer progressMax,
        ProgressUnit progressUnit,
        Integer rawRating,
        Integer rawRatingMax,
        LocalDate startedAt,
        LocalDate finishedAt) {}
