package dev.nexus.core.jobs;

import dev.nexus.core.domain.Provider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncProgressRepository extends JpaRepository<SyncProgress, Long> {

    Optional<SyncProgress> findByUserIdAndProviderAndKind(Long userId, Provider provider, SyncJob.Kind kind);
}
