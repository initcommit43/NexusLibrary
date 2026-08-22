package dev.nexus.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A cached canonical item, shared by every user. One row per unique (source, externalId),
 * so an external API is called once per title rather than once per user.
 */
@Entity
@Table(name = "trackable_item")
public class TrackableItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false)
    private String title;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_state", nullable = false)
    private ItemState itemState;

    /** Type-specific fields: platforms for games, authors for books, studio for anime. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "cached_at", nullable = false, insertable = false, updatable = false)
    private Instant cachedAt;

    @Column(name = "refreshed_at")
    private Instant refreshedAt;

    protected TrackableItem() {
        // JPA
    }

    public TrackableItem(
            MediaType mediaType,
            Source source,
            String externalId,
            String title,
            String coverUrl,
            LocalDate releaseDate,
            ItemState itemState,
            Map<String, Object> metadata) {
        this.mediaType = mediaType;
        this.source = source;
        this.externalId = externalId;
        this.title = title;
        this.coverUrl = coverUrl;
        this.releaseDate = releaseDate;
        this.itemState = itemState;
        this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    }

    public Long getId() {
        return id;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public Source getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public ItemState getItemState() {
        return itemState;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getCachedAt() {
        return cachedAt;
    }

    public Instant getRefreshedAt() {
        return refreshedAt;
    }
}
