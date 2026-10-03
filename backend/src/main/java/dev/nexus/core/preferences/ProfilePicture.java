package dev.nexus.core.preferences;

import dev.nexus.core.domain.TrackableItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The picture standing for a reader, and how it sits in the circle.
 *
 * <p>Keyed by the reader like the banner beside it: this is one choice rather than a
 * collection, and having no row is what a profile with no picture is.
 *
 * <p>Either an upload, named by {@link #uploadId} with its bytes in {@link PictureUpload}, or a
 * character picked from a title before uploads replaced that — the item, character and url
 * columns, kept so those pictures go on showing until their reader uploads one. Never both.
 */
@Entity
@Table(name = "user_profile_picture")
public class ProfilePicture {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "trackable_item_id")
    private TrackableItem item;

    @Column(name = "character_id")
    private String characterId;

    @Column(name = "character_name")
    private String characterName;

    @Column(name = "image_url")
    private String imageUrl;

    /**
     * Which upload this is, new with every one: the image is served from a fixed address, and
     * this is what tells a cached copy of the last upload from the current one.
     */
    @Column(name = "upload_id")
    private UUID uploadId;

    @Column(name = "chosen_at", nullable = false)
    private Instant chosenAt;

    /**
     * Which point of the image to hold in view, as percentages of it, and how far in. A
     * portrait cropped to a circle is the case this exists for: a plain centre crop of one
     * lands on a torso about as often as on a face.
     */
    @Column(name = "focus_x", nullable = false)
    private short focusX;

    @Column(name = "focus_y", nullable = false)
    private short focusY;

    /** Hundredths, so 100 is the image at cover size and 250 is two and a half times it. */
    @Column(nullable = false)
    private short zoom;

    protected ProfilePicture() {
        // JPA
    }

    public ProfilePicture(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public TrackableItem getItem() {
        return item;
    }

    public String getCharacterName() {
        return characterName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public UUID getUploadId() {
        return uploadId;
    }

    public Instant getChosenAt() {
        return chosenAt;
    }

    public short getFocusX() {
        return focusX;
    }

    public short getFocusY() {
        return focusY;
    }

    public short getZoom() {
        return zoom;
    }

    /**
     * Pointed at a fresh upload, framed as the reader cropped it before sending. Whatever it
     * showed before, character or upload, is let go: a reader has one picture, not a history.
     */
    public void uploaded(int x, int y, int magnification) {
        this.item = null;
        this.characterId = null;
        this.characterName = null;
        this.imageUrl = null;
        this.uploadId = UUID.randomUUID();
        this.chosenAt = Instant.now();
        frame(x, y, magnification);
    }

    /** Moved and magnified within the circle, leaving the image it does this to alone. */
    public void frame(int x, int y, int magnification) {
        this.focusX = (short) x;
        this.focusY = (short) y;
        this.zoom = (short) magnification;
    }
}
