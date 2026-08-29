package dev.nexus.core.tracking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The reader's favourites in the order they arranged them, first to last.
 *
 * <p>The whole arrangement rather than one card's new position: a drop moves everything
 * after it, and sending the list that resulted is what makes the write idempotent — replay
 * it and the shelf looks the same.
 */
public record ReorderFavouritesRequest(@NotEmpty @Size(max = 500) List<Long> entryIds) {}
