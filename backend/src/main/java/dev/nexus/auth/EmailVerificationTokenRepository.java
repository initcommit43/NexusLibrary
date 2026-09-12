package dev.nexus.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Retires every link an account still has outstanding, which asking for another one does.
     * Only the newest link works, so a mail forwarded on or found later is already dead.
     */
    @Modifying
    @Query("update EmailVerificationToken t set t.usedAt = :at where t.userId = :userId and t.usedAt is null")
    int spendEveryOutstandingLink(@Param("userId") Long userId, @Param("at") Instant at);

    /** How many links this account has been sent recently, so resending cannot be a mail cannon. */
    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

    /** Housekeeping only: an expired row is already refused on its own expiry. */
    @Modifying
    int deleteByExpiresAtBefore(Instant cutoff);
}
