package dev.nexus.core.preferences;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The picture standing at the head of a reader's profile.
 *
 * <p>Every call is keyed by the authenticated reader and nothing else: there is no picture id
 * in any request, so there is no other reader's picture a request could name.
 *
 * <p>What reaches {@link #store} has already been through {@link PictureSanitizer}, outside
 * any transaction — decoding holds a thread for a moment, and a database connection should
 * not be held for it too.
 */
@Service
public class ProfilePictureService {

    private final ProfilePictureRepository pictures;
    private final PictureUploadRepository uploads;

    public ProfilePictureService(ProfilePictureRepository pictures, PictureUploadRepository uploads) {
        this.pictures = pictures;
        this.uploads = uploads;
    }

    @Transactional(readOnly = true)
    public Optional<ProfilePicture> forUser(long userId) {
        return pictures.findByUserId(userId);
    }

    /** The bytes of this reader's own upload, if what they have is one. */
    @Transactional(readOnly = true)
    public Optional<PictureUpload> uploadOf(long userId) {
        return uploads.findByUserId(userId);
    }

    /** Puts an upload at the head of the profile, framed as it was cropped before sending. */
    @Transactional
    public ProfilePicture store(long userId, PictureSanitizer.Sanitized picture, int focusX, int focusY, int zoom) {
        // Pointed at the upload before a new row is saved, not after: the insert is written as
        // the row stood when saved, and a row naming no image breaks the table's own check.
        ProfilePicture held = pictures.findByUserId(userId).orElseGet(() -> new ProfilePicture(userId));
        held.uploaded(focusX, focusY, zoom);
        held = pictures.save(held);
        // Flushed before the bytes, whose row hangs off this one by foreign key.
        pictures.flush();

        uploads.findByUserId(userId)
                .ifPresentOrElse(
                        existing -> existing.replaceWith(picture),
                        () -> uploads.save(new PictureUpload(userId, picture)));
        return held;
    }

    /**
     * Where the picture sits inside the circle, which is a change to the framing and not to
     * the picture: the same image, held differently.
     */
    @Transactional
    public ProfilePicture frame(long userId, int focusX, int focusY, int zoom) {
        ProfilePicture picture =
                pictures.findByUserId(userId).orElseThrow(PictureNotSetException::new);
        picture.frame(focusX, focusY, zoom);
        return picture;
    }

    /** The uploaded bytes go with it, by the cascade on their foreign key. */
    @Transactional
    public void clear(long userId) {
        pictures.deleteByUserId(userId);
    }
}
