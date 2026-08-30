package dev.nexus.core.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Scoped by {@code userId} throughout, like every other user-owned repository here. */
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findByUserIdOrderByCreatedAtDesc(Long userId, Limit limit);

    /** Scoped to the owner in the query itself, so an id alone can never reach another's row. */
    long deleteByIdAndUserId(Long id, Long userId);

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
}
