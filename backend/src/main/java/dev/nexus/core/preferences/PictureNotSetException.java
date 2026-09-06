package dev.nexus.core.preferences;

/** Framing asked of a profile that has no picture to frame. */
public class PictureNotSetException extends RuntimeException {

    public PictureNotSetException() {
        super("Choose a profile picture before adjusting how it sits.");
    }
}
