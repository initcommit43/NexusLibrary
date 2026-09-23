package dev.nexus.auth;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The devices a reader can be reached on. Storage only: nothing here sends, and nothing reads
 * these rows yet but the reader's own registrations.
 */
@Service
public class DeviceTokenService {

    /**
     * More than anyone has phones and tablets, and a ceiling on what one account can make this
     * table hold. Past it the device seen longest ago goes, which is the one most likely gone.
     */
    static final int MAX_DEVICES_PER_USER = 20;

    private final DeviceTokenRepository tokens;

    public DeviceTokenService(DeviceTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Transactional
    public void register(Long userId, String token, DevicePlatform platform) {
        tokens.register(userId, token, platform.name(), Instant.now());

        List<DeviceToken> devices = tokens.findByUserIdOrderByLastSeenAtDesc(userId);
        if (devices.size() > MAX_DEVICES_PER_USER) {
            tokens.deleteAll(devices.subList(MAX_DEVICES_PER_USER, devices.size()));
        }
    }

    /** Answers the same whether or not the token was this reader's, so it confirms nothing. */
    @Transactional
    public void unregister(Long userId, String token) {
        tokens.deleteByTokenAndUserId(token, userId);
    }
}
