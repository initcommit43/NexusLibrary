package dev.nexus.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * One thing a reader did somewhere else, as that service recorded it.
 *
 * <p>Distinct from {@link Activity}, which is what was done in this app and is read as a
 * feed. This is history that came in with a library: AniList knows the day every episode was
 * watched, and without it a reader's map has two days per title — the day they started and
 * the day they finished — instead of the hundreds they actually had.
 *
 * <p>Immutable once written. A second import of the same account writes nothing new, since
 * the provider's own id for the event is unique per reader.
 */
@Entity
@Table(name = "provider_activity")
public class ProviderActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    /** The provider's own id for the event, which is what makes a re-import add nothing. */
    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "trackable_item_id", nullable = false)
    private Long itemId;

    /**
     * The day it happened, in the reader's own reckoning rather than in UTC: a square on the
     * map is the day someone had, not the day the server was having.
     */
    @Column(name = "happened_on", nullable = false)
    private LocalDate happenedOn;

    /** What the event said it was — watching, completed, dropped. Null when it said nothing. */
    @Column private String status;

    /**
     * How far it got, as the provider wrote it: "12", or "5 - 7" for a run of episodes in one
     * sitting. Kept as text because it is a label, not a number this app does sums with.
     */
    @Column private String progress;

    protected ProviderActivity() {
        // JPA
    }

    public ProviderActivity(
            Long userId,
            Provider provider,
            String externalId,
            Long itemId,
            LocalDate happenedOn,
            String status,
            String progress) {
        this.userId = userId;
        this.provider = provider;
        this.externalId = externalId;
        this.itemId = itemId;
        this.happenedOn = happenedOn;
        this.status = status;
        this.progress = progress;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Provider getProvider() {
        return provider;
    }

    public String getExternalId() {
        return externalId;
    }

    public Long getItemId() {
        return itemId;
    }

    public LocalDate getHappenedOn() {
        return happenedOn;
    }

    public String getStatus() {
        return status;
    }

    public String getProgress() {
        return progress;
    }
}
