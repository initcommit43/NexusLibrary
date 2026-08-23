package dev.nexus.core.adapter;

import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Indexes whatever adapters the modules contribute. Adding a module means adding a bean;
 * nothing in core changes.
 */
@Component
public class MetadataAdapterRegistry {

    private final Map<MediaType, MetadataAdapter> byMediaType;
    private final Map<Source, MetadataAdapter> bySource;

    public MetadataAdapterRegistry(List<MetadataAdapter> adapters) {
        this.byMediaType = adapters.stream()
                .flatMap(adapter -> adapter.mediaTypes().stream().map(type -> Map.entry(type, adapter)))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        this.bySource = adapters.stream()
                .collect(Collectors.toUnmodifiableMap(MetadataAdapter::source, Function.identity()));
    }

    public MetadataAdapter requireForMediaType(MediaType mediaType) {
        return Optional.ofNullable(byMediaType.get(mediaType))
                .orElseThrow(() -> new MetadataAdapterNotAvailableException(mediaType));
    }

    public Optional<MetadataAdapter> forSource(Source source) {
        return Optional.ofNullable(bySource.get(source));
    }

    public List<MediaType> availableMediaTypes() {
        return byMediaType.keySet().stream().sorted().toList();
    }
}
