package dev.nexus.core.tracking;

import dev.nexus.auth.CurrentUser;
import dev.nexus.core.tracking.dto.ReorderFavouritesRequest;
import dev.nexus.core.tracking.dto.TrackRequest;
import dev.nexus.core.tracking.dto.TrackedItemResponse;
import dev.nexus.core.tracking.dto.UpdateEntryRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/entries")
public class TrackingController {

    private final TrackingService tracking;

    public TrackingController(TrackingService tracking) {
        this.tracking = tracking;
    }

    @GetMapping
    public List<TrackedItemResponse> list(@AuthenticationPrincipal CurrentUser user) {
        Map<Long, Instant> lastActivity = tracking.lastActivityByItem(user.id());

        return tracking.listFor(user.id()).stream()
                .map(entry -> TrackedItemResponse.from(entry, lastActivity.get(entry.getItem().getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public TrackedItemResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return TrackedItemResponse.from(tracking.requireOwned(id, user.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrackedItemResponse track(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody TrackRequest request) {
        return TrackedItemResponse.from(tracking.track(user.id(), request));
    }

    /** The favourites, in the order the reader dragged them into. */
    @PutMapping("/favourites/order")
    public List<TrackedItemResponse> reorderFavourites(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ReorderFavouritesRequest request) {
        return tracking.reorderFavourites(user.id(), request.entryIds()).stream()
                .map(TrackedItemResponse::from)
                .toList();
    }

    @PatchMapping("/{id}")
    public TrackedItemResponse update(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long id,
            @Valid @RequestBody UpdateEntryRequest request) {
        return TrackedItemResponse.from(tracking.update(user.id(), id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        tracking.delete(user.id(), id);
    }
}
