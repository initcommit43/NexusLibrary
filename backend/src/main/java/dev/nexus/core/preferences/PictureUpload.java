package dev.nexus.core.preferences;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The bytes of an uploaded profile picture, as the server encoded them.
 *
 * <p>Kept apart from {@link ProfilePicture} so that reading where a picture sits never loads
 * the picture: the profile asks for the one on every paint and for the other only to draw it.
 */
@Entity
@Table(name = "user_profile_picture_upload")
public class PictureUpload {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private byte[] content;

    @Column(nullable = false)
    private short width;

    @Column(nullable = false)
    private short height;

    protected PictureUpload() {
        // JPA
    }

    public PictureUpload(Long userId, PictureSanitizer.Sanitized picture) {
        this.userId = userId;
        replaceWith(picture);
    }

    public void replaceWith(PictureSanitizer.Sanitized picture) {
        this.content = picture.jpeg();
        this.width = (short) picture.width();
        this.height = (short) picture.height();
    }

    public byte[] getContent() {
        return content;
    }
}
