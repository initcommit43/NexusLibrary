package dev.nexus.core.review;

import dev.nexus.core.activity.ActivityRecorder;
import dev.nexus.core.domain.Review;
import dev.nexus.core.domain.ReviewRepository;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.tracking.TrackingService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reviews are reached through the entry they belong to, and every entry lookup is already
 * scoped to its owner, so a review cannot be read or written by anyone else.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviews;
    private final TrackingService tracking;
    private final ActivityRecorder activity;

    public ReviewService(ReviewRepository reviews, TrackingService tracking, ActivityRecorder activity) {
        this.reviews = reviews;
        this.tracking = tracking;
        this.activity = activity;
    }

    @Transactional(readOnly = true)
    public Optional<Review> findFor(Long userId, Long entryId) {
        return reviews.findByEntryIdAndUserId(entryId, userId);
    }

    @Transactional(readOnly = true)
    public List<Review> listFor(Long userId) {
        return reviews.findAllForUser(userId);
    }

    /** Writing again replaces the existing review; the schema allows only one per entry. */
    @Transactional
    public Review write(Long userId, Long entryId, String body, boolean containsSpoilers) {
        UserEntry entry = tracking.requireOwned(entryId, userId);

        // Checked here rather than only on the page: a rule the client alone keeps is a
        // suggestion, and this one decides what the review feed is worth reading for.
        if (entry.getStatus() == TrackingStatus.PLANNING) {
            throw new ReviewNotStartedException();
        }

        Review review = reviews.findByEntryId(entry.getId())
                .map(existing -> {
                    existing.setBody(body);
                    existing.setContainsSpoilers(containsSpoilers);
                    return existing;
                })
                .orElseGet(() -> new Review(entry, body, containsSpoilers));

        boolean isNew = review.getId() == null;
        Review saved = reviews.save(review);

        // Only a first review is news; edits would otherwise flood the feed.
        if (isNew) {
            activity.reviewed(entry);
        }
        return saved;
    }

    @Transactional
    public void delete(Long userId, Long entryId) {
        reviews.findByEntryIdAndUserId(entryId, userId).ifPresentOrElse(reviews::delete, () -> {
            throw new ReviewNotFoundException();
        });
    }
}
