package dev.nexus.core.jobs;

import dev.nexus.core.domain.Provider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * How far a walk of somebody else's stream has got.
 *
 * <p>One run is capped, so what makes the cap bearable is this: the next run starts where the
 * last one stopped. A history that takes three presses to bring in takes three presses, and
 * not three presses' worth of fetching the same first thousand rows.
 */
@Entity
@Table(name = "sync_progress")
public class SyncProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncJob.Kind kind;

    @Column(name = "next_page", nullable = false)
    private int nextPage = 1;

    @Column(nullable = false)
    private boolean complete;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SyncProgress() {
        // JPA
    }

    public SyncProgress(Long userId, Provider provider, SyncJob.Kind kind) {
        this.userId = userId;
        this.provider = provider;
        this.kind = kind;
    }

    /** Where the next run should pick up. Page 1 for a walk that has never run. */
    public int getNextPage() {
        return nextPage;
    }

    public boolean isComplete() {
        return complete;
    }

    /**
     * Stopped with the stream unfinished — at the cap, or on an error.
     *
     * <p>The page recorded is the one that has not been done, so the next run repeats nothing
     * and skips nothing.
     */
    public void stoppedAt(int page) {
        this.nextPage = Math.max(1, page);
        this.complete = false;
        this.updatedAt = Instant.now();
    }

    /**
     * Reached the end of the stream.
     *
     * <p>Back to page 1, because the run after this one is looking for what is new rather
     * than for what is old, and what is new is at the top.
     */
    public void reachedTheEnd() {
        this.nextPage = 1;
        this.complete = true;
        this.updatedAt = Instant.now();
    }
}
