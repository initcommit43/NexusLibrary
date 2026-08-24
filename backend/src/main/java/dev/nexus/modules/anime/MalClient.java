package dev.nexus.modules.anime;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.importing.ExternalAccountService;
import dev.nexus.core.web.OutboundRateLimiter;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Speaks MAL's v2 REST API — the read-only slice of it.
 *
 * <p>Lists are read as {@code users/@me} with the reader's own token, so a private list
 * reads exactly like a public one. MAL tokens live about a month, which AniList's
 * year-long tokens never made anyone think about: a token close to its end is refreshed
 * before it is used, and the new pair is persisted, so a re-import months later works or
 * says plainly that the link needs reconnecting.
 *
 * <p>The field sets ask for exactly what the resolver's matching needs — alternative
 * titles and the episode, chapter and volume counts — since id, title and picture are all
 * MAL volunteers unprompted.
 */
@Component
public class MalClient {

    private static final Logger log = LoggerFactory.getLogger(MalClient.class);

    /** MAL caps list pages at 100 rows. */
    static final int PAGE_SIZE = 100;

    private static final int MAX_ATTEMPTS = 3;

    /** Refresh this long before actual expiry, not exactly at it: clocks disagree. */
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(60);

    static final String ANIME_FIELDS = "alternative_titles,num_episodes,list_status{start_date,finish_date}";
    static final String MANGA_FIELDS =
            "alternative_titles,num_chapters,num_volumes,list_status{start_date,finish_date}";

    private final RestClient restClient;
    private final MalProperties properties;
    private final MalOAuthService oauth;
    private final ExternalAccountService accounts;
    private final OutboundRateLimiter rateLimiter;

    public MalClient(
            RestClient.Builder builder,
            MalProperties properties,
            MalOAuthService oauth,
            ExternalAccountService accounts) {
        this.restClient = builder.build();
        this.properties = properties;
        this.oauth = oauth;
        this.accounts = accounts;
        this.rateLimiter = new OutboundRateLimiter(properties.requestsPerSecond());
    }

    /** Every row of the reader's anime list, page after page until MAL stops offering more. */
    public List<Map<String, Object>> fetchAnimeList(ExternalAccount account) {
        return fetchWholeList(account, "animelist", ANIME_FIELDS);
    }

    public List<Map<String, Object>> fetchMangaList(ExternalAccount account) {
        return fetchWholeList(account, "mangalist", MANGA_FIELDS);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchWholeList(ExternalAccount account, String list, String fields) {
        String token = usableToken(account);

        List<Map<String, Object>> rows = new ArrayList<>();
        int offset = 0;

        while (true) {
            Map<String, Object> page = get(listUri(list, fields, offset), token);
            if (page.get("data") instanceof List<?> data) {
                data.stream().filter(Map.class::isInstance).forEach(row -> rows.add((Map<String, Object>) row));
            }

            // MAL paginates by handing back a "next" URL; its presence is the only signal.
            if (!(page.get("paging") instanceof Map<?, ?> paging) || paging.get("next") == null) {
                return rows;
            }
            offset += PAGE_SIZE;
        }
    }

    /**
     * The account's token, refreshed first when it is at or near its end. The new pair is
     * persisted immediately: a refresh that worked but was not written down is a token
     * spent for nothing. No refresh token, or a refusal, means only the reader can mend
     * the link — said as advice, not as a failure to retry.
     */
    private String usableToken(ExternalAccount account) {
        Instant expiresAt = account.getTokenExpiresAt();
        boolean nearEnd = expiresAt != null && expiresAt.minus(EXPIRY_SKEW).isBefore(Instant.now());
        if (!nearEnd) {
            return account.getAccessToken();
        }

        if (account.getRefreshToken() == null) {
            throw new MalReconnectRequiredException();
        }

        log.debug("MAL token for account {} at its end, refreshing", account.getExternalUserId());
        MalOAuthService.Tokens fresh;
        try {
            fresh = oauth.refresh(account.getRefreshToken());
        } catch (MalUnavailableException e) {
            // A refused refresh is a dead link; only going through approval again mends it.
            throw new MalReconnectRequiredException();
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

    /**
     * Built as a {@link URI} rather than a string: the field list carries literal braces —
     * {@code list_status{start_date}} — which a string URI would be template-expanded on.
     */
    private URI listUri(String list, String fields, int offset) {
        return UriComponentsBuilder.fromUriString(properties.apiUrl())
                .pathSegment("users", "@me", list)
                .queryParam("limit", PAGE_SIZE)
                .queryParam("offset", offset)
                // A reader's own list must come back whole; by default MAL quietly
                // withholds entries it rates as adult, which would silently shrink imports.
                .queryParam("nsfw", "true")
                .queryParam("fields", fields)
                .build()
                .encode()
                .toUri();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(URI uri, String accessToken) {
        if (!properties.canConnectAccounts()) {
            throw new MalNotConfiguredException();
        }

        MalUnavailableException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            rateLimiter.acquire();
            try {
                Map<String, Object> body = restClient
                        .get()
                        .uri(uri)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "application/json")
                        .exchange((request, response) -> {
                            int status = response.getStatusCode().value();
                            // A rejected token mid-run means it was revoked or died under
                            // us; retrying with the same token cannot end differently.
                            if (status == 401) {
                                throw new MalReconnectRequiredException();
                            }
                            if (response.getStatusCode().isError()) {
                                throw new MalUnavailableException("MyAnimeList responded with " + status);
                            }
                            return (Map<String, Object>) response.bodyTo(Map.class);
                        });
                return body == null ? Map.of() : body;
            } catch (MalUnavailableException e) {
                lastFailure = e;
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                log.debug("MAL attempt {} failed ({}), retrying", attempt, e.getMessage());
                pause(properties.retryBackoffMs() * attempt);
            } catch (RestClientException e) {
                // A dropped connection is as transient as a gateway error.
                lastFailure = new MalUnavailableException("MyAnimeList request failed", e);
                if (attempt == MAX_ATTEMPTS) {
                    throw lastFailure;
                }
                log.debug("MAL attempt {} failed ({}), retrying", attempt, e.getMessage());
                pause(properties.retryBackoffMs() * attempt);
            }
        }

        throw lastFailure;
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MalUnavailableException("Interrupted while waiting to retry MyAnimeList", e);
        }
    }
}
