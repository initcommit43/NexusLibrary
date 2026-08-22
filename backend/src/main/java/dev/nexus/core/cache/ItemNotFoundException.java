package dev.nexus.core.cache;

/** The external source has no item with that id, so nothing can be cached or tracked. */
public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(String message) {
        super(message);
    }
}
