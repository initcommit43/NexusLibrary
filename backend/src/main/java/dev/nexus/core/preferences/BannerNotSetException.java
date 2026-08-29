package dev.nexus.core.preferences;

/** Framing asked of a profile that has no banner to frame. */
public class BannerNotSetException extends RuntimeException {

    public BannerNotSetException() {
        super("Choose a banner before adjusting how it sits.");
    }
}
