package dev.nexus.core.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByEntryId(Long entryId);

    /**
     * Scoped through the entry, so a review can only ever be reached by the user who owns
     * the entry it belongs to.
     */
    @Query("select r from Review r where r.entry.id = :entryId and r.entry.userId = :userId")
    Optional<Review> findByEntryIdAndUserId(Long entryId, Long userId);

    @Query("select r from Review r where r.entry.userId = :userId order by r.updatedAt desc")
    List<Review> findAllForUser(Long userId);
}
