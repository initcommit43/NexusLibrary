package dev.nexus.core.security;

import dev.nexus.core.domain.Provider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The {@code state} half of an OAuth authorization code flow, for the providers whose
 * protocol offers nothing else to bind a callback to the session that started it.
 *
 * <p>MAL needs none of this: PKCE makes its flow stateful anyway, and the verifier it
 * remembers per user already refuses a callback the session never asked for. AniList and
 * Simkl are plain authorization-code grants, so without a minted state anyone could lure a
 * signed-in reader onto a callback URL carrying <em>their</em> code and have the victim's
 * account linked to the attacker's. Same mechanism as MAL's pending verifier — per user,
 * short-lived, single use — around a different secret.
 *
 * <p>Pending states are in memory and therefore per instance, the same trade as the job
 * registry and correct for a single-instance deployment: the worst a restart costs is an
 * approval that has to be started again.
 */
@Component
public class OAuthStateStore {

    /** Longer than anyone needs to approve a screen; shorter than worth keeping state for. */
    private static final Duration DEFAULT_LIFETIME = Duration.ofMinutes(10);

    private record Key(Provider provider, Long userId) {}

    private record Pending(String state, Instant startedAt) {}

    private final Map<Key, Pending> pending = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Duration lifetime;

    public OAuthStateStore() {
        this(DEFAULT_LIFETIME);
    }

    /** Lifetime is a parameter so a test can hold an already-stale attempt without waiting. */
    public OAuthStateStore(Duration lifetime) {
        this.lifetime = lifetime;
    }

    /**
     * Mints the state to send along with an authorization request. Starting again simply
     * replaces the pending one: only the newest attempt can finish.
     */
    public String issue(Provider provider, Long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);

        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(new Key(provider, userId), new Pending(state, Instant.now()));
        return state;
    }

    /**
     * True only for the state this user was handed, still inside its window, and only the
     * first time it comes back. The pending attempt is dropped either way — a code is
     * single-use, so the proof that accompanies it should be too.
     */
    public boolean consume(Provider provider, Long userId, String state) {
        Pending issued = pending.remove(new Key(provider, userId));
        if (issued == null || state == null || issued.startedAt().isBefore(Instant.now().minus(lifetime))) {
            return false;
        }

        // Constant time: the comparison is against a secret this caller may be guessing.
        return MessageDigest.isEqual(
                issued.state().getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8));
    }

    /** Abandoned attempts are dropped, so the map cannot grow with people who never return. */
    @Scheduled(fixedDelay = 300_000)
    public void evictAbandoned() {
        Instant cutoff = Instant.now().minus(lifetime);
        pending.values().removeIf(attempt -> attempt.startedAt().isBefore(cutoff));
    }
}
