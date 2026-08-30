package dev.nexus.core.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Scoped by {@code userId} throughout, like every other user-owned repository here. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Limit limit);

    long countByUserIdAndReadAtIsNull(Long userId);

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

    /** Which of a batch about to be written are already there, so a re-run writes nothing. */
    @Query("SELECT n.subject FROM Notification n WHERE n.userId = :userId "
            + "AND n.item.id = :itemId AND n.type = :type")
    List<String> subjectsFor(
            @Param("userId") Long userId,
            @Param("itemId") Long itemId,
            @Param("type") NotificationType type);
}
