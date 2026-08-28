package dev.nexus.core.preferences;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisabledModuleRepository extends JpaRepository<DisabledModule, Long> {

    List<DisabledModule> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
