package dev.nexus.core.review;

import dev.nexus.auth.CurrentUser;
import dev.nexus.core.domain.Review;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/entries/{entryId}/review")
public class ReviewController {

    public record WriteReviewRequest(@NotBlank @Size(max = 20_000) String body, boolean containsSpoilers) {}

    public record ReviewResponse(
            Long id, Long entryId, String body, boolean containsSpoilers, Instant createdAt, Instant updatedAt) {

        static ReviewResponse from(Review review) {
            return new ReviewResponse(
                    review.getId(),
                    review.getEntry().getId(),
                    review.getBody(),
                    review.isContainsSpoilers(),
                    review.getCreatedAt(),
                    review.getUpdatedAt());
        }
    }

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public ReviewResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long entryId) {
        return reviews.findFor(user.id(), entryId)
                .map(ReviewResponse::from)
                .orElseThrow(ReviewNotFoundException::new);
    }

    /** Idempotent by design: one review per entry, so writing again replaces it. */
    @PutMapping
    public ReviewResponse write(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long entryId,
            @Valid @RequestBody WriteReviewRequest request) {

        return ReviewResponse.from(
                reviews.write(user.id(), entryId, request.body(), request.containsSpoilers()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long entryId) {
        reviews.delete(user.id(), entryId);
    }
}
