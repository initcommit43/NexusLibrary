package dev.nexus.core.preferences;

/**
 * A character asked for that the chosen title does not offer.
 *
 * <p>Either the title has no characters at all — only AniList carries them, so a game or a
 * book never will — or the one named has no portrait to stand as a picture. Said plainly at
 * the moment of choosing rather than stored as a picture that draws nothing.
 */
public class NoCharacterException extends RuntimeException {

    public NoCharacterException(String title) {
        super("There is no character picture to take from " + title + ". Try another title.");
    }
}
