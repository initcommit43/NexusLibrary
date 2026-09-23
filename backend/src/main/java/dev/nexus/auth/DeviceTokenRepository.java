package dev.nexus.auth;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    /**
     * Records a device for a reader in one statement. The same token again only moves
     * last_seen_at on; a token held by another account moves to this one and counts as
     * registered now. One statement rather than find-then-save, because a phone registering
     * on every cold start will sooner or later race itself into the unique constraint.
     */
    @Modifying
    @Query(
            value = "INSERT INTO device_token (user_id, token, platform, registered_at, last_seen_at)"
                    + " VALUES (:userId, :token, :platform, :now, :now)"
                    + " ON CONFLICT (token) DO UPDATE SET"
                    + " registered_at = CASE WHEN device_token.user_id = EXCLUDED.user_id"
                    + " THEN device_token.registered_at ELSE EXCLUDED.registered_at END,"
                    + " user_id = EXCLUDED.user_id,"
                    + " platform = EXCLUDED.platform,"
                    + " last_seen_at = EXCLUDED.last_seen_at",
            nativeQuery = true)
    void register(
            @Param("userId") Long userId,
            @Param("token") String token,
            @Param("platform") String platform,
            @Param("now") Instant now);

    /** Scoped to the owner, so naming someone else's token removes nothing. */
    @Modifying
    int deleteByTokenAndUserId(String token, Long userId);

    @Modifying
    int deleteByUserId(Long userId);

    List<DeviceToken> findByUserIdOrderByLastSeenAtDesc(Long userId);
}
