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
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Limit limit);

    /**
     * The same list, narrowed to the media types a module owns.
     *
     * <p>Narrowed here rather than after the fact: the panel shows one module at a time, and a
     * reader whose last month was all anime would otherwise ask for fifty and be shown none.
     */
    List<Notification> findByUserIdAndItemMediaTypeInOrderByCreatedAtDesc(
            Long userId, Collection<MediaType> mediaTypes, Limit limit);

    long countByUserIdAndReadAtIsNull(Long userId);

    long countByUserIdAndItemMediaTypeInAndReadAtIsNull(Long userId, Collection<MediaType> mediaTypes);

    /**
     * Marks one as seen, by the reader who owns it.
     *
     * <p>Scoped by {@code userId} in the statement itself rather than by reading the row and
     * checking it: an id from anyone is an id for somebody's row, and the only safe answer to
     * one that is not theirs is to change nothing.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now "
            + "WHERE n.id = :id AND n.userId = :userId AND n.readAt IS NULL")
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("now") Instant now);

    /**
     * Marks everything a reader has as seen.
     *
     * <p>Written as one statement rather than a row at a time: someone opening a list of two
     * hundred is telling the app they have looked at the lot, and loading two hundred rows to
     * set one column on each of them is the same answer by a longer route.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    /**
     * The same, for the module the reader is looking at.
     *
     * <p>"Read all" said under one module's heading means the ones under it. Clearing games a
     * reader has never opened because they read their anime is a button that does more than
     * it says.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId "
            + "AND n.readAt IS NULL AND n.item.id IN "
            + "(SELECT i.id FROM TrackableItem i WHERE i.mediaType IN :mediaTypes)")
    int markAllRead(
            @Param("userId") Long userId,
            @Param("mediaTypes") Collection<MediaType> mediaTypes,
            @Param("now") Instant now);

    /**
     * Everything a reader has already been told about a set of titles, in one query.
     *
     * <p>The per-title check below answers for one title at a time, which is what a detector
     * sweeping a handful of aired episodes needs. An import arrives fifty rows at a time and
     * would ask fifty times for the same answer.
     */
    @Query("SELECT n.item.id AS itemId, n.type AS type, n.subject AS subject FROM Notification n "
            + "WHERE n.userId = :userId AND n.item.id IN :itemIds")
    List<Told> toldAbout(@Param("userId") Long userId, @Param("itemIds") Collection<Long> itemIds);

    /** One thing a reader has already been told. */
    interface Told {
        Long getItemId();

        NotificationType getType();

        String getSubject();
    }

    /** Which of a batch about to be written are already there, so a re-run writes nothing. */
    @Query("SELECT n.subject FROM Notification n WHERE n.userId = :userId "
            + "AND n.item.id = :itemId AND n.type = :type")
    List<String> subjectsFor(
            @Param("userId") Long userId,
            @Param("itemId") Long itemId,
            @Param("type") NotificationType type);
}
