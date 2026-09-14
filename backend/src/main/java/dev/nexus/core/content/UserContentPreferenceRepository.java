package dev.nexus.core.content;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserContentPreferenceRepository extends JpaRepository<UserContentPreference, Long> {

    Optional<UserContentPreference> findByUserId(Long userId);
}
