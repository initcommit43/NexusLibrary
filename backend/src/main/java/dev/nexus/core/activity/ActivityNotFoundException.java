package dev.nexus.core.activity;

/**
 * Raised both when an event does not exist and when it belongs to someone else. Answering
 * 404 either way means the API never confirms the existence of another user's rows.
 */
public class ActivityNotFoundException extends RuntimeException {

    public ActivityNotFoundException() {
        super("Activity not found.");
    }
}
