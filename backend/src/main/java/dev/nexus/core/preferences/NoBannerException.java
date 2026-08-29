package dev.nexus.core.preferences;

/**
 * A title chosen as a banner that has no wide art to give.
 *
 * <p>Not every source has one for every title: AniList leaves the field null often enough,
 * and a book has no such image at all. Said plainly at the moment of choosing rather than
 * stored as a banner that draws nothing.
 */
public class NoBannerException extends RuntimeException {

    public NoBannerException(String title) {
        super("There is no banner image for " + title + ". Try another title.");
    }
}
