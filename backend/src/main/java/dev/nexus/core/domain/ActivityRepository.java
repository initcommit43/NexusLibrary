package dev.nexus.core.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Scoped by {@code userId} throughout, like every other user-owned repository here. */
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findByUserIdOrderByCreatedAtDesc(Long userId, Limit limit);

    /** Scoped to the owner in the query itself, so an id alone can never reach another's row. */
    long deleteByIdAndUserId(Long id, Long userId);

    /**
     * A reader's history for the named mediums.
     *
     * <p>Activity references the catalogue item rather than the entry, so emptying a shelf
     * leaves its history standing — a feed of things that happened to titles the reader no
     * longer tracks. Clearing is the one operation that has to reach both.
     */
    long deleteByUserIdAndItemMediaTypeIn(Long userId, java.util.Collection<MediaType> mediaTypes);

    /**
     * The last time each of a reader's titles was touched here.
     *
     * <p>Only events about a title count, which is what makes this the answer to "when was I
     * last at this" rather than "when was this row last written": an import writes one event
     * for the run and no event per title, and it is the run that would otherwise stamp a whole
     * library with one moment.
     */
    @Query("SELECT a.item.id AS itemId, max(a.createdAt) AS at FROM Activity a "
            + "WHERE a.userId = :userId AND a.item IS NOT NULL GROUP BY a.item.id")
    List<LastTouched> lastTouchedPerItem(@Param("userId") Long userId);

    /** One title, and the last thing this app recorded about it. */
    interface LastTouched {
        Long getItemId();

        Instant getAt();
    }

    /**
     * A reader's import and sync events for the named providers.
     *
     * <p>These belong to a run rather than a title — "142 arrived from AniList" — so they carry no
     * item and the medium-scoped delete above never reaches them. The provider is the only thing
     * that says which shelves a run was about, and it lives in the payload.
     */
    @Modifying
    @Query(
            value = "DELETE FROM activity WHERE user_id = :userId AND trackable_item_id IS NULL"
                    + " AND payload ->> 'provider' IN (:providers)",
            nativeQuery = true)
    int deleteRunsFor(@Param("userId") Long userId, @Param("providers") Collection<String> providers);
}
