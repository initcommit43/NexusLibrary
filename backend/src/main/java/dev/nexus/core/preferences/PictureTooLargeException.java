package dev.nexus.core.preferences;

/** An upload over the profile picture's size limit, refused before any of it is decoded. */
public class PictureTooLargeException extends RuntimeException {

    public PictureTooLargeException() {
        super("That picture is larger than 1 MB. Please choose a smaller one.");
    }
}
