package dev.nexus.core.tracking;

/**
 * Raised both when an entry does not exist and when it belongs to someone else. Answering
 * 404 either way means the API never confirms the existence of another user's rows.
 */
public class EntryNotFoundException extends RuntimeException {

    public EntryNotFoundException() {
        super("Entry not found.");
    }
}
