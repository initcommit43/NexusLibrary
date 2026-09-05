package dev.nexus.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Retires every link an account still has outstanding, which asking for a new one does:
     * two live links are two chances for one to be read out of an inbox later.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :at where t.userId = :userId and t.usedAt is null")
    int spendEveryOutstandingLink(@Param("userId") Long userId, @Param("at") Instant at);

    /** Housekeeping only: an expired row is already refused on its own expiry. */
    @Modifying
    int deleteByExpiresAtBefore(Instant cutoff);
}
