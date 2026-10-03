package dev.nexus.core.preferences;

/**
 * An uploaded file that will not become a profile picture. The message is written for the
 * reader and says only what to do differently; what the decoder made of the file stays here.
 */
public class PictureRejectedException extends RuntimeException {

    public PictureRejectedException(String message) {
        super(message);
    }
}
