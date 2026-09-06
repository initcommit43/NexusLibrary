package dev.nexus.core.preferences;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * How a chosen image sits in the frame that shows it: the point of it to hold in view, as
 * percentages, and how far in.
 *
 * <p>Bounded here rather than trusted, since these go straight into the style the profile is
 * drawn with. Shared by the banner and the picture — a strip and a circle crop the same way,
 * and two copies of these bounds would be two places to get them wrong.
 */
public record Framing(
        @NotNull @Min(0) @Max(100) Integer focusX,
        @NotNull @Min(0) @Max(100) Integer focusY,
        @NotNull @Min(100) @Max(300) Integer zoom) {}
