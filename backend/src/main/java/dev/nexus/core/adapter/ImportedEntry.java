package dev.nexus.core.adapter;

import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.TrackingStatus;
import java.time.LocalDate;

/**
 * One row of a user's external library, as the provider reports it. Ratings stay on the
 * provider's own scale here; core converts to 0-100 once, during the upsert.
 *
 * <p>The repeat count and the two flags are null wherever a provider has no such notion,
 * which leaves the entry's own values alone.
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
        LocalDate finishedAt,
        Integer repeatCount,
        Boolean isPrivate,
        Boolean hiddenFromStatusLists) {

    public ImportedEntry(
            ExternalItemRef itemRef,
            TrackingStatus status,
            Integer progressCurrent,
            Integer progressMax,
            ProgressUnit progressUnit,
            Integer rawRating,
            Integer rawRatingMax,
            LocalDate startedAt,
            LocalDate finishedAt) {
        this(itemRef, status, progressCurrent, progressMax, progressUnit, rawRating, rawRatingMax,
                startedAt, finishedAt, null, null, null);
    }
}
