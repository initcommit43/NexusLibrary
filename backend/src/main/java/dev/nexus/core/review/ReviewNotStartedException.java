package dev.nexus.core.review;

/**
 * A review of something the reader has not begun.
 *
 * <p>Planning to read a book is not an opinion of it. Every other shelf — reading, finished,
 * paused, dropped — means they started, and someone who dropped a series halfway has as much
 * to say as someone who finished it.
 */
public class ReviewNotStartedException extends RuntimeException {

    public ReviewNotStartedException() {
        super("Add this to a shelf other than planning before reviewing it.");
    }
}
