package dev.nexus.core.domain;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Scoped by {@code userId} throughout, like every other user-owned repository here. */
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findByUserIdOrderByCreatedAtDesc(Long userId, Limit limit);
}
