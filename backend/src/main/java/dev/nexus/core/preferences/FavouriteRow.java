package dev.nexus.core.preferences;

import dev.nexus.core.domain.MediaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Where one of a reader's favourite rows sits among the others.
 *
 * <p>A row is the exception rather than the rule, as with {@link DisabledModule}: nothing
 * written means the app's own order, so a reader who never rearranges anything costs no rows.
 */
@Entity
@Table(name = "user_favourite_row")
public class FavouriteRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Whether this row shares its band with the one before it, which is what a pair of rows
     * side by side is. Never true of the first row, which has nothing to sit beside.
     */
    @Column(name = "shares_lane", nullable = false)
    private boolean sharesLane;

    protected FavouriteRow() {
        // JPA
    }

    public FavouriteRow(Long userId, MediaType mediaType, int sortOrder, boolean sharesLane) {
        this.userId = userId;
        this.mediaType = mediaType;
        this.sortOrder = sortOrder;
        this.sharesLane = sharesLane;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean sharesLane() {
        return sharesLane;
    }
}
