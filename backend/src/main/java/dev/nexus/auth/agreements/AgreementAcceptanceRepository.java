package dev.nexus.auth.agreements;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every method here takes the owner's id as its first argument. There is deliberately no
 * finder that reads a row by its own id alone: one would answer about somebody else's
 * consent, and no caller in this application has a use for that.
 */
public interface AgreementAcceptanceRepository extends JpaRepository<AgreementAcceptance, Long> {

    /** What this account has accepted on one platform — the read every sign-in makes. */
    List<AgreementAcceptance> findByUserIdAndPlatform(Long userId, AgreementPlatform platform);

    boolean existsByUserIdAndDocumentAndPlatformAndVersion(
            Long userId, Agreement document, AgreementPlatform platform, String version);

    /** For the tests and for answering "what did they agree to", newest first. */
    List<AgreementAcceptance> findByUserIdOrderByAcceptedAtDesc(Long userId);
}
