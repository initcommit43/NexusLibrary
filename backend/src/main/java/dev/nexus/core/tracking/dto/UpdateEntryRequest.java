package dev.nexus.core.tracking.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.TrackingStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

/**
 * A partial update: every field is optional, and null means "leave unchanged".
 *
 * <p>Emptying a field is said separately, in {@code clear}, rather than by sending null: null
 * already means "leave it" to every client written against this, and a client that sends the
 * whole form back with the blanks as null must not start wiping dates it never touched.
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
        @Size(max = 5000) String notes,
        @PositiveOrZero @Max(9999) Integer repeatCount,
        // "private" as the field is named on the wire; a Java component cannot be called that.
        @JsonProperty("private") Boolean isPrivate,
        Boolean hiddenFromStatusLists,
        /** Fields to empty. A field named here and also given a value is refused. */
        @Size(max = 8) Set<@NotNull ClearableField> clear) {

    @JsonIgnore
    @AssertTrue(message = "A field cannot be both set and cleared.")
    public boolean isClearingOnlyWhatIsNotSet() {
        if (clear == null) {
            return true;
        }
        return !(clear.contains(ClearableField.STARTED_AT) && startedAt != null)
                && !(clear.contains(ClearableField.FINISHED_AT) && finishedAt != null);
    }
}
