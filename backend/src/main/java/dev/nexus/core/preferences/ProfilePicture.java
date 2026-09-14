package dev.nexus.core.preferences;

import dev.nexus.core.adapter.CharacterPortrait;
import dev.nexus.core.domain.TrackableItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The character a reader chose to stand for them, and the title that character came from.
 *
 * <p>Keyed by the reader like the banner beside it: this is one choice rather than a
 * collection, and having no row is what a profile with no picture is.
 *
 * <p>The character's id and name are kept alongside the resolved url because all three come
 * out of the same walk through a source-shaped detail. Resolving once at the moment of
 * choosing is what lets the profile draw its head from one row.
 */
@Entity
@Table(name = "user_profile_picture")
public class ProfilePicture {

    /** The middle of the image, and the size a cover crop is: what an untouched picture wears. */
    private static final short CENTRE = 50;

    private static final short COVER = 100;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "trackable_item_id", nullable = false)
    private TrackableItem item;

    @Column(name = "character_id", nullable = false)
    private String characterId;

    @Column(name = "character_name", nullable = false)
    private String characterName;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

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

    public ProfilePicture(Long userId, TrackableItem item, CharacterPortrait character) {
        this.userId = userId;
        this.item = item;
        this.characterId = character.id();
        this.characterName = character.name();
        this.imageUrl = character.imageUrl();
        this.chosenAt = Instant.now();
        resetFraming();
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
     * Re-pointed at another character, since a reader has one picture rather than a history of
     * them. The framing goes back to a plain cover crop: it was a set of offsets into a
     * different portrait, and carrying them over would open the new one already askew.
     */
    public void moveTo(TrackableItem chosen, CharacterPortrait character) {
        this.item = chosen;
        this.characterId = character.id();
        this.characterName = character.name();
        this.imageUrl = character.imageUrl();
        this.chosenAt = Instant.now();
        resetFraming();
    }

    /** Moved and magnified within the circle, leaving the image it does this to alone. */
    public void frame(int x, int y, int magnification) {
        this.focusX = (short) x;
        this.focusY = (short) y;
        this.zoom = (short) magnification;
    }

    private void resetFraming() {
        this.focusX = CENTRE;
        this.focusY = CENTRE;
        this.zoom = COVER;
    }
}
