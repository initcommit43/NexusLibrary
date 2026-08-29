package dev.nexus.core.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every method is scoped by {@code userId} by construction. There is deliberately no
 * plain {@code findById} in use: an unscoped lookup followed by an ownership check is
 * the pattern that leaks other users' rows when someone forgets the check.
 */
public interface UserEntryRepository extends JpaRepository<UserEntry, Long> {

    List<UserEntry> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /** One shelf, in the order a reader would sort a spreadsheet of it. */
    List<UserEntry> findByUserIdAndItemMediaTypeOrderByItemTitleAsc(Long userId, MediaType mediaType);

    Optional<UserEntry> findByIdAndUserId(Long id, Long userId);

    Optional<UserEntry> findByUserIdAndItemId(Long userId, Long itemId);

    long deleteByIdAndUserId(Long id, Long userId);

    /** A day a reader did something, and how much of it. */
    interface DayTally {
        LocalDate getDay();

        long getAmount();
    }

    /**
     * What a reader started and finished, by the day it happened.
     *
     * <p>Starting and finishing are counted separately and summed: a day that finished three
     * series and began two saw five things happen, and collapsing that to "a day with
     * activity" throws away the only number the square has to show.
     *
     * <p>Read off the entries rather than the activity log because the log holds what was
     * done in this app, while these dates come in with an import and stretch back years —
     * which is the history the map is for. Media types the caller has no dates for are
     * excluded by the caller, since a shelf that never records a date would otherwise read
     * as a shelf nobody touched.
     */
    @Query(
            value =
                    """
                    SELECT day, count(*) AS amount FROM (
                        SELECT e.started_at AS day
                          FROM user_entry e JOIN trackable_item i ON i.id = e.trackable_item_id
                         WHERE e.user_id = :userId AND e.started_at >= :from
                           AND i.media_type NOT IN (:excluded)
                        UNION ALL
                        SELECT e.finished_at
                          FROM user_entry e JOIN trackable_item i ON i.id = e.trackable_item_id
                         WHERE e.user_id = :userId AND e.finished_at >= :from
                           AND i.media_type NOT IN (:excluded)
                    ) days
                    GROUP BY day ORDER BY day
                    """,
            nativeQuery = true)
    List<DayTally> tallyDaysSince(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("excluded") Collection<String> excluded);
}
