package dev.nexus.modules.film;

import dev.nexus.config.NexusProperties;
import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.importing.ExternalAccountService;
import dev.nexus.core.web.OutboundRateLimiter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Speaks Trakt's API — the read-only slice of it, as the reader themselves.
 *
 * <p>The {@code /sync} endpoints answer with a whole library in one response, so an import
 * costs six calls no matter how many films someone has watched. That is why nothing here
 * pages or batches, and why the interesting work is in what each response means rather
 * than in how much of it there is.
 *
 * <p>Tokens live three months, so a token near its end is refreshed before it is used and
 * the new pair is persisted — the same handling MAL forced, for the same reason.
 */
@Component
public class TraktClient {

    private static final Logger log = LoggerFactory.getLogger(TraktClient.class);

    /** Refresh this long before actual expiry, not exactly at it: clocks disagree. */
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(60);

    /**
     * {@code extended=full} is what carries {@code aired_episodes} on a show, which is the
     * only way to tell a finished series from one someone is halfway through.
     */
    private static final String WATCHED_SHOWS = "/sync/watched/shows?extended=full";
    private static final String WATCHED_MOVIES = "/sync/watched/movies";
    private static final String WATCHLIST_MOVIES = "/sync/watchlist/movies";
    private static final String WATCHLIST_SHOWS = "/sync/watchlist/shows";
    private static final String RATINGS_MOVIES = "/sync/ratings/movies";
    private static final String RATINGS_SHOWS = "/sync/ratings/shows";

    private final RestClient restClient;
    private final TraktProperties properties;
    private final TraktOAuthService oauth;
    private final ExternalAccountService accounts;
    private final OutboundRateLimiter rateLimiter;
    private final String redirectUri;

    public TraktClient(
            RestClient.Builder builder,
            TraktProperties properties,
            TraktOAuthService oauth,
            ExternalAccountService accounts,
            NexusProperties nexus) {
        this.restClient = builder.build();
        this.properties = properties;
        this.oauth = oauth;
        this.accounts = accounts;
        this.rateLimiter = new OutboundRateLimiter(properties.requestsPerSecond());
        this.redirectUri = nexus.security().frontendUrl() + TraktOAuthService.CALLBACK_PATH;
    }

    public List<Map<String, Object>> watchedMovies(ExternalAccount account) {
        return get(account, WATCHED_MOVIES);
    }

    public List<Map<String, Object>> watchedShows(ExternalAccount account) {
        return get(account, WATCHED_SHOWS);
    }

    public List<Map<String, Object>> watchlistMovies(ExternalAccount account) {
        return get(account, WATCHLIST_MOVIES);
    }

    public List<Map<String, Object>> watchlistShows(ExternalAccount account) {
        return get(account, WATCHLIST_SHOWS);
    }

    public List<Map<String, Object>> ratedMovies(ExternalAccount account) {
        return get(account, RATINGS_MOVIES);
    }

    public List<Map<String, Object>> ratedShows(ExternalAccount account) {
        return get(account, RATINGS_SHOWS);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> get(ExternalAccount account, String path) {
        if (!properties.canConnectAccounts()) {
            throw new TraktNotConfiguredException();
        }
        String token = usableToken(account);
        rateLimiter.acquire();

        try {
            List<Map<String, Object>> rows = restClient
                    .get()
                    .uri(properties.apiBaseUrl() + path)
                    .headers(headers -> {
                        headers.set("Authorization", "Bearer " + token);
                        headers.set("trakt-api-version", "2");
                        headers.set("trakt-api-key", properties.clientId());
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.value() == 401) {
                            // The token was refused outright; refreshing it is the reader's job now.
                            throw new TraktReconnectRequiredException();
                        }
                        if (status.isError()) {
                            throw new TraktUnavailableException("Trakt responded with " + status.value());
                        }
                        return (List<Map<String, Object>>) response.bodyTo(List.class);
                    });

            return rows == null ? List.of() : rows;
        } catch (RestClientException e) {
            throw new TraktUnavailableException("Trakt request failed", e);
        }
    }

    /**
     * The account's token, refreshed first when it is at or near its end. The new pair is
     * persisted immediately: a refresh that worked but was not written down is a token
     * spent for nothing — and Trakt rotates the refresh token, so the old one is already
     * dead by then.
     */
    private String usableToken(ExternalAccount account) {
        Instant expiresAt = account.getTokenExpiresAt();
        boolean nearEnd = expiresAt != null && expiresAt.minus(EXPIRY_SKEW).isBefore(Instant.now());
        if (!nearEnd) {
            return account.getAccessToken();
        }

        if (account.getRefreshToken() == null) {
            throw new TraktReconnectRequiredException();
        }

        log.debug("Trakt token for account {} at its end, refreshing", account.getExternalUserId());
        TraktOAuthService.Tokens fresh;
        try {
            fresh = oauth.refresh(account.getRefreshToken(), redirectUri);
        } catch (TraktUnavailableException e) {
            // A refused refresh is a dead link; only going through approval again mends it.
            throw new TraktReconnectRequiredException();
        }

        accounts.connect(
                account.getUserId(),
                account.getProvider(),
                account.getExternalUserId(),
                fresh.accessToken(),
                fresh.refreshToken(),
                fresh.expiresAt());

        return fresh.accessToken();
    }
}
