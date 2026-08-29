package dev.nexus.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A user's relationship to a cached item: status, rating, progress and notes. */
@Entity
@Table(name = "user_entry")
public class UserEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "trackable_item_id", nullable = false)
    private TrackableItem item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrackingStatus status;

    /** Internal scale is always 0-100; external scales are converted on the way in. */
    private Short rating;

    @Column(name = "progress_current")
    private Integer progressCurrent;

    @Column(name = "progress_max")
    private Integer progressMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "progress_unit")
    private ProgressUnit progressUnit;

    /** Leaky cases only: manga volume+chapter, game main-vs-completionist. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "progress_extra")
    private Map<String, Object> progressExtra;

    @Column(name = "started_at")
    private LocalDate startedAt;

    @Column(name = "finished_at")
    private LocalDate finishedAt;

    @Column(nullable = false)
    private boolean favorite;

    /** Where this sits among the reader's favourites; null until they arrange them. */
    @Column(name = "favorite_rank")
    private Integer favoriteRank;

    /**
     * The provider that put this entry here, if any. A fact about this person's copy — the
     * shared item's Steam appid says the game is on Steam, not that they imported it.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "imported_from")
    private Provider importedFrom;

    private String notes;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    protected UserEntry() {
        // JPA
    }

    public UserEntry(Long userId, TrackableItem item, TrackingStatus status) {
        this.userId = userId;
        this.item = item;
        this.status = status;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public TrackableItem getItem() {
        return item;
    }

    public TrackingStatus getStatus() {
        return status;
    }

    public void setStatus(TrackingStatus status) {
        this.status = status;
    }

    public Short getRating() {
        return rating;
    }

    public void setRating(Short rating) {
        this.rating = rating;
    }

    public Integer getProgressCurrent() {
        return progressCurrent;
    }

    public void setProgressCurrent(Integer progressCurrent) {
        this.progressCurrent = progressCurrent;
    }

    public Integer getProgressMax() {
        return progressMax;
    }

    public void setProgressMax(Integer progressMax) {
        this.progressMax = progressMax;
    }

    public ProgressUnit getProgressUnit() {
        return progressUnit;
    }

    public void setProgressUnit(ProgressUnit progressUnit) {
        this.progressUnit = progressUnit;
    }

    public Map<String, Object> getProgressExtra() {
        return progressExtra;
    }

    public void setProgressExtra(Map<String, Object> progressExtra) {
        this.progressExtra = progressExtra;
    }

    public LocalDate getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDate startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDate getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDate finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Provider getImportedFrom() {
        return importedFrom;
    }

    public void setImportedFrom(Provider importedFrom) {
        this.importedFrom = importedFrom;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public Integer getFavoriteRank() {
        return favoriteRank;
    }

    public void setFavoriteRank(Integer favoriteRank) {
        this.favoriteRank = favoriteRank;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
