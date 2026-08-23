package dev.nexus.modules.games;

import dev.nexus.core.web.OutboundRateLimiter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads a user's achievements for one game.
 *
 * <p>There is no bulk form of this endpoint: it takes exactly one appid, so syncing a
 * library means one request per game. That is why achievements run as a background job
 * rather than inside a request, and why this client backs off rather than giving up when
 * Steam starts throttling.
 */
@Component
public class SteamAchievementsClient {

    private static final Logger log = LoggerFactory.getLogger(SteamAchievementsClient.class);

    private static final int MAX_ATTEMPTS = 4;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);

    /** Steam's own wording when the profile itself is not public. */
    private static final String PRIVATE_PROFILE = "Profile is not public";

    private final RestClient restClient;
    private final SteamProperties properties;
    private final Duration initialBackoff;
    private final OutboundRateLimiter rateLimiter;

    @Autowired
    public SteamAchievementsClient(RestClient.Builder builder, SteamProperties properties) {
        this(builder, properties, INITIAL_BACKOFF);
    }

    /** Lets tests exercise the retry path without waiting out the real delays. */
    SteamAchievementsClient(RestClient.Builder builder, SteamProperties properties, Duration initialBackoff) {
        this.restClient = builder.build();
        this.properties = properties;
        this.initialBackoff = initialBackoff;
        this.rateLimiter = new OutboundRateLimiter(properties.achievementRequestsPerSecond());
    }

    /**
     * @return the achievement list, or empty when the game simply has no achievements —
     *     which is normal for tools, demos and plenty of games, and is not an error
     * @throws SteamProfileNotPublicException when Steam will not reveal the data at all
     */
    public Optional<List<Map<String, Object>>> fetch(String appId, String steamId) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new SteamUnavailableException("Steam API key is not configured; set STEAM_API_KEY");
        }

        Map<String, Object> stats = requestWithBackoff(appId, steamId);
        if (stats == null) {
            return Optional.empty();
        }

        if (Boolean.FALSE.equals(stats.get("success"))) {
            String error = String.valueOf(stats.getOrDefault("error", ""));
            if (PRIVATE_PROFILE.equalsIgnoreCase(error)) {
                throw new SteamProfileNotPublicException();
            }
            // "Requested app has no stats" and friends: a fact about the game, not a fault.
            log.debug("No achievement stats for appid {}: {}", appId, error);
            return Optional.empty();
        }

        return Optional.ofNullable(achievementsOf(stats));
    }

    /**
     * The game's achievement list, with icons and the hidden flag.
     *
     * <p>Carries no user context, so it is the same for every player and worth caching once
     * per game forever. It is also readable regardless of profile privacy, unlike a player's
     * own unlocks.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchSchema(String appId) {
        rateLimiter.acquire();

        Map<String, Object> body = restClient
                .get()
                .uri(
                        properties.apiBaseUrl() + "/ISteamUserStats/GetSchemaForGame/v2/?key={key}&appid={appid}&l=english",
                        properties.apiKey(),
                        appId)
                .exchange((request, response) -> response.getStatusCode().isError()
                        ? Map.<String, Object>of()
                        : (Map<String, Object>) response.bodyTo(Map.class));

        if (!(body.get("game") instanceof Map<?, ?> game)
                || !(((Map<String, Object>) game).get("availableGameStats") instanceof Map<?, ?> stats)
                || !(((Map<String, Object>) stats).get("achievements") instanceof List<?> achievements)) {
            return List.of();
        }
        return (List<Map<String, Object>>) achievements;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> achievementsOf(Map<String, Object> stats) {
        return stats.get("achievements") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    /**
     * Steam applies undocumented per-method limits and answers 429 when they are hit.
     * Retries with a doubling delay rather than failing a whole sync over one throttled call.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> requestWithBackoff(String appId, String steamId) {
        Duration backoff = initialBackoff;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            // Spaced out before the call, not only after a rejection: reacting to 429s
            // alone means every sync trips the limit before it starts behaving.
            rateLimiter.acquire();
            try {
                Map<String, Object> body = restClient
                        .get()
                        .uri(
                                properties.apiBaseUrl()
                                        + "/ISteamUserStats/GetPlayerAchievements/v1/"
                                        + "?appid={appid}&key={key}&steamid={steamid}&l=english",
                                appId,
                                properties.apiKey(),
                                steamId)
                        .exchange((request, response) -> {
                            HttpStatusCode status = response.getStatusCode();
                            if (status.value() == 429 || status.is5xxServerError()) {
                                return null;
                            }
                            // 400 here means the app has no stats at all, which is expected.
                            if (status.value() == 400 || status.value() == 403) {
                                return Map.<String, Object>of("success", false, "error", "no stats");
                            }
                            return (Map<String, Object>) response.bodyTo(Map.class);
                        });

                if (body != null) {
                    return body.get("playerstats") instanceof Map<?, ?> stats
                            ? (Map<String, Object>) stats
                            : body;
                }
            } catch (RestClientException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new SteamUnavailableException("Could not reach Steam for achievements", e);
                }
            }

            if (attempt < MAX_ATTEMPTS) {
                log.debug("Steam throttled appid {}, retrying in {}", appId, backoff);
                sleep(backoff);
                backoff = backoff.multipliedBy(2);
            }
        }

        throw new SteamThrottledException();
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SteamUnavailableException("Interrupted while backing off from Steam", e);
        }
    }
}
