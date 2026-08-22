package dev.nexus.core.review;

/** No review on that entry, or the entry belongs to someone else. Both answer 404. */
public class ReviewNotFoundException extends RuntimeException {

    public ReviewNotFoundException() {
        super("Review not found.");
    }
}
