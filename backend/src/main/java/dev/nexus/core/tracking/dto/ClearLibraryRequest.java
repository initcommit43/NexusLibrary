package dev.nexus.core.tracking.dto;

import dev.nexus.core.domain.MediaType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Which shelves to empty.
 *
 * <p>Named rather than implied: "everything" is the caller's default, not the server's. A
 * request that arrived with no list — dropped by a proxy, lost by a client bug — would
 * otherwise read as permission to delete a whole library, so the empty list is refused and
 * the reader has to have said which mediums they meant.
 */
public record ClearLibraryRequest(
        @NotEmpty @Size(max = 16) List<@NotNull MediaType> mediaTypes) {}
