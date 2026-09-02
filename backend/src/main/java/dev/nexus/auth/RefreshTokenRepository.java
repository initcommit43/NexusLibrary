package dev.nexus.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByJti(UUID jti);

    /**
     * Ends every live session a user has, in one statement rather than by loading them:
     * signing out everywhere is one intent, and a partly applied one is a session left in.
     */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :at where t.userId = :userId and t.revokedAt is null")
    int revokeEveryLiveToken(@Param("userId") Long userId, @Param("at") Instant at);

    /** Drops what can no longer admit anything, revoked or not: the token has expired too. */
    @Modifying
    int deleteByExpiresAtBefore(Instant cutoff);
}
