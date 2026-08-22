package dev.nexus.core.adapter;

import dev.nexus.core.domain.MediaType;

/** No module is registered for the requested media type — e.g. asking for books today. */
public class MetadataAdapterNotAvailableException extends RuntimeException {

    public MetadataAdapterNotAvailableException(MediaType mediaType) {
        super("No metadata adapter registered for " + mediaType);
    }
}
