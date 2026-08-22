package dev.nexus.core.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every method is scoped by {@code userId} by construction. There is deliberately no
 * plain {@code findById} in use: an unscoped lookup followed by an ownership check is
 * the pattern that leaks other users' rows when someone forgets the check.
 */
public interface UserEntryRepository extends JpaRepository<UserEntry, Long> {

    List<UserEntry> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<UserEntry> findByIdAndUserId(Long id, Long userId);

    Optional<UserEntry> findByUserIdAndItemId(Long userId, Long itemId);

    long deleteByIdAndUserId(Long id, Long userId);
}
