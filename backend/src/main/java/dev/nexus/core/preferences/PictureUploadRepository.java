package dev.nexus.core.preferences;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Keyed by the reader's id, like the picture row it belongs to: every read is their own. */
public interface PictureUploadRepository extends JpaRepository<PictureUpload, Long> {

    Optional<PictureUpload> findByUserId(Long userId);
}
