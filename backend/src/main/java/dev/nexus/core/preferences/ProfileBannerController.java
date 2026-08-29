package dev.nexus.core.preferences;

import dev.nexus.auth.CurrentUser;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The image at the head of a reader's profile, and which of their titles it came from.
 *
 * <p>Keyed by the authenticated reader like the preferences beside it. The one id in a
 * request is an entry's, and an entry that is not theirs is simply not found.
 */
@Validated
@RestController
@RequestMapping("/settings/profile-banner")
public class ProfileBannerController {

    /** Which of the reader's entries to take the banner from. */
    public record Choice(@NotNull Long entryId) {}

    /**
     * How the image sits in the strip: the point of it to hold in view, as percentages, and
     * how far in. Bounded here rather than trusted, since these go straight into the style
     * the profile is drawn with.
     */
    public record Framing(
            @NotNull @Min(0) @Max(100) Integer focusX,
            @NotNull @Min(0) @Max(100) Integer focusY,
            @NotNull @Min(100) @Max(300) Integer zoom) {}

    /**
     * The image, and enough of the title behind it to name it and link to its page — the
     * profile says where its banner came from rather than leaving it an anonymous backdrop.
     */
    public record BannerResponse(
            String imageUrl,
            String title,
            MediaType mediaType,
            Source source,
            String externalId,
            int focusX,
            int focusY,
            int zoom) {

        static BannerResponse from(ProfileBanner banner) {
            return new BannerResponse(
                    banner.getImageUrl(),
                    banner.getItem().getTitle(),
                    banner.getItem().getMediaType(),
                    banner.getItem().getSource(),
                    banner.getItem().getExternalId(),
                    banner.getFocusX(),
                    banner.getFocusY(),
                    banner.getZoom());
        }
    }

    private final ProfileBannerService banners;

    public ProfileBannerController(ProfileBannerService banners) {
        this.banners = banners;
    }

    /** Answers with nothing at all when none is set, which is what a bare profile head is. */
    @GetMapping
    public BannerResponse current(@AuthenticationPrincipal CurrentUser user) {
        return banners.forUser(user.id()).map(BannerResponse::from).orElse(null);
    }

    @PutMapping
    public BannerResponse choose(
            @AuthenticationPrincipal CurrentUser user, @RequestBody Choice body) {
        return BannerResponse.from(banners.choose(user.id(), body.entryId()));
    }

    @PatchMapping
    public BannerResponse frame(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody Framing body) {
        return BannerResponse.from(
                banners.frame(user.id(), body.focusX(), body.focusY(), body.zoom()));
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@AuthenticationPrincipal CurrentUser user) {
        banners.clear(user.id());
        return ResponseEntity.noContent().build();
    }
}
