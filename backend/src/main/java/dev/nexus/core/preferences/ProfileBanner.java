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

/**
 * The title a reader chose to head their profile with, and the image it yielded.
 *
 * <p>Keyed by the reader rather than by a row id of its own: this is one choice, not a
 * collection, and having no row is what a profile with no banner is.
 *
 * <p>The url is kept alongside the item because it lives inside that item's cached detail,
 * in whatever shape its source uses, and only that source's adapter can read it. Resolving
 * once at the moment of choosing is what lets the profile draw its head from one row.
 */
@Entity
@Table(name = "user_profile_banner")
public class ProfileBanner {

    /** The middle of the image, and the size a cover crop is: what an untouched banner wears. */
    private static final short CENTRE = 50;

    private static final short COVER = 100;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "trackable_item_id", nullable = false)
    private TrackableItem item;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "chosen_at", nullable = false)
    private Instant chosenAt;

    /**
     * Which point of the image to hold in view, as percentages of it, and how far in. A
     * plain cover crop is the middle at no magnification, which is what every banner starts
     * as and what a reader who never touches the framing keeps.
     */
    @Column(name = "focus_x", nullable = false)
    private short focusX;

    @Column(name = "focus_y", nullable = false)
    private short focusY;

    /** Hundredths, so 100 is the image at cover size and 250 is two and a half times it. */
    @Column(nullable = false)
    private short zoom;

    protected ProfileBanner() {
        // JPA
    }

    public ProfileBanner(Long userId, TrackableItem item, String imageUrl) {
        this.userId = userId;
        this.item = item;
        this.imageUrl = imageUrl;
        this.chosenAt = Instant.now();
        resetFraming();
    }

    public Long getUserId() {
        return userId;
    }

    public TrackableItem getItem() {
        return item;
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
     * Re-pointed at another title, since a reader has one banner rather than a history of
     * them. The framing goes back to a plain cover crop: it was a set of offsets into a
     * different picture, and carrying them over would open the new one already askew.
     */
    public void moveTo(TrackableItem chosen, String url) {
        this.item = chosen;
        this.imageUrl = url;
        this.chosenAt = Instant.now();
        resetFraming();
    }

    /** Moved and magnified within the strip, leaving the image it does this to alone. */
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
