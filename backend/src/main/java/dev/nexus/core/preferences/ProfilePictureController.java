package dev.nexus.core.preferences;

import dev.nexus.auth.CurrentUser;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * The character a reader put at the head of their profile, and which of their titles it is from.
 *
 * <p>Keyed by the authenticated reader like the banner beside it. The ids in a request are an
 * entry's and a character's, and an entry that is not theirs is simply not found.
 */
@Validated
@RestController
@RequestMapping("/settings/profile-picture")
public class ProfilePictureController {

    /** Which of the reader's entries to take the picture from, and which character in it. */
    public record Choice(@NotNull Long entryId, @NotBlank String characterId) {}

    /**
     * The portrait, the character it shows, and enough of the title behind it to name it and
     * link to its page — the profile credits its picture rather than leaving it anonymous.
     */
    public record PictureResponse(
            String imageUrl,
            String characterName,
            String title,
            MediaType mediaType,
            Source source,
            String externalId,
            int focusX,
            int focusY,
            int zoom) {

        static PictureResponse from(ProfilePicture picture) {
            return new PictureResponse(
                    picture.getImageUrl(),
                    picture.getCharacterName(),
                    picture.getItem().getTitle(),
                    picture.getItem().getMediaType(),
                    picture.getItem().getSource(),
                    picture.getItem().getExternalId(),
                    picture.getFocusX(),
                    picture.getFocusY(),
                    picture.getZoom());
        }
    }

    private final ProfilePictureService pictures;

    public ProfilePictureController(ProfilePictureService pictures) {
        this.pictures = pictures;
    }

    /** Answers with nothing at all when none is set, which is what a plain icon is. */
    @GetMapping
    public PictureResponse current(@AuthenticationPrincipal CurrentUser user) {
        return pictures.forUser(user.id()).map(PictureResponse::from).orElse(null);
    }

    @PutMapping
    public PictureResponse choose(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody Choice body) {
        return PictureResponse.from(
                pictures.choose(user.id(), body.entryId(), body.characterId()));
    }

    @PatchMapping
    public PictureResponse frame(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody Framing body) {
        return PictureResponse.from(
                pictures.frame(user.id(), body.focusX(), body.focusY(), body.zoom()));
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@AuthenticationPrincipal CurrentUser user) {
        pictures.clear(user.id());
        return ResponseEntity.noContent().build();
    }
}
