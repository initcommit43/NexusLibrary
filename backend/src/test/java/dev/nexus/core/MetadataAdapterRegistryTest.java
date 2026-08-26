package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nexus.core.adapter.ItemSearchResult;
import dev.nexus.core.adapter.MetadataAdapter;
import dev.nexus.core.adapter.MetadataAdapterNotAvailableException;
import dev.nexus.core.adapter.MetadataAdapterRegistry;
import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins what happens to a media type nothing serves. Every type now has a module, so this is
 * the only place the gap is still reachable — and it has to stay a "not implemented" rather
 * than becoming a null somewhere further in.
 */
class MetadataAdapterRegistryTest {

    @Test
    void aMediaTypeWithoutAnAdapterIsNotImplementedRatherThanAnError() {
        MetadataAdapterRegistry registry = new MetadataAdapterRegistry(List.of(adapterFor(MediaType.GAME)));

        assertThatThrownBy(() -> registry.requireForMediaType(MediaType.BOOK))
                .isInstanceOf(MetadataAdapterNotAvailableException.class);
    }

    @Test
    void reportsOnlyTheMediaTypesItCanActuallyServe() {
        MetadataAdapterRegistry registry =
                new MetadataAdapterRegistry(List.of(adapterFor(MediaType.GAME), adapterFor(MediaType.BOOK)));

        assertThat(registry.availableMediaTypes()).containsExactlyInAnyOrder(MediaType.GAME, MediaType.BOOK);
        assertThat(registry.forSource(Source.ANILIST)).isEmpty();
    }

    private MetadataAdapter adapterFor(MediaType mediaType) {
        return new MetadataAdapter() {

            @Override
            public Set<MediaType> mediaTypes() {
                return Set.of(mediaType);
            }

            @Override
            public Source source() {
                return mediaType == MediaType.BOOK ? Source.OPEN_LIBRARY : Source.IGDB;
            }

            @Override
            public List<ItemSearchResult> search(MediaType type, String query, int limit) {
                return List.of();
            }

            @Override
            public Optional<TrackableItemData> fetchById(String externalId) {
                return Optional.empty();
            }
        };
    }
}
