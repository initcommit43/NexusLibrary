package dev.nexus.modules.games;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * IGDB authenticates through Twitch client credentials. Tokens last ~60 days, so one is
 * held in memory and only re-fetched when it nears expiry or is rejected.
 */
@Component
public class IgdbAuthClient {

    // Refresh a little early rather than discovering expiry mid-request.
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final IgdbProperties properties;

    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public IgdbAuthClient(RestClient.Builder builder, IgdbProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public synchronized String accessToken() {
        if (token == null || Instant.now().isAfter(expiresAt.minus(EXPIRY_MARGIN))) {
            fetchToken();
        }
        return token;
    }

    /** Called when IGDB rejects the current token, so the next call fetches a fresh one. */
    public synchronized void invalidate() {
        token = null;
        expiresAt = Instant.EPOCH;
    }

    private void fetchToken() {
        Map<?, ?> response = restClient
                .post()
                .uri(
                        properties.tokenUrl()
                                + "?client_id={id}&client_secret={secret}&grant_type=client_credentials",
                        properties.clientId(),
                        properties.clientSecret())
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("access_token") == null) {
            throw new IgdbUnavailableException("IGDB authentication returned no access token");
        }

        token = response.get("access_token").toString();
        long expiresIn = response.get("expires_in") instanceof Number n ? n.longValue() : 0L;
        expiresAt = Instant.now().plusSeconds(expiresIn);
    }
}
