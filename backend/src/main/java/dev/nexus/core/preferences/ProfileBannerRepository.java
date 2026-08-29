package dev.nexus.core.preferences;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every lookup here is already the reader's own: the primary key is the reader's id, so
 * there is no unscoped read to guard against.
 */
public interface ProfileBannerRepository extends JpaRepository<ProfileBanner, Long> {

    Optional<ProfileBanner> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
