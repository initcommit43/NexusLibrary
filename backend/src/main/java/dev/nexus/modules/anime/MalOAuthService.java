package dev.nexus.modules.anime;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * MAL's authorization code grant — deliberately not a copy of {@link AniListOAuthService},
 * because MAL's flow differs where it counts.
 *
 * <p>PKCE is mandatory and only the {@code plain} method is supported, so the challenge is
 * the verifier itself — and the verifier minted when the reader is sent away must still be
 * here when they come back, which makes authorization stateful in a way AniList's never
 * was. The token endpoint takes form-encoded bodies, not JSON. And a MAL token lives about
 * a month, so {@link #refresh} is a real path, with MAL's quirk handled: a refresh answer
 * may omit the refresh token, which means keep the old one, not lose it.
 *
 * <p>Pending verifiers are in memory and therefore per instance — the same trade as the
 * job registry, and correct for a single-instance deployment. A verifier is held briefly,
 * for one user, and consumed on first use.
 */
@Service
public class MalOAuthService {

    /** Longer than anyone needs to approve a screen; shorter than worth keeping state for. */
    private static final Duration PENDING_LIFETIME = Duration.ofMinutes(10);

    /** Fallback when MAL omits expires_in; its documented token lifetime is 31 days. */
    private static final Duration DEFAULT_LIFETIME = Duration.ofDays(31);

    public record Connection(String externalUserId, String accessToken, String refreshToken, Instant expiresAt) {}

    /** What a refresh yields; the caller persists it onto the account. */
    public record Tokens(String accessToken, String refreshToken, Instant expiresAt) {}

    private record PendingAuthorization(String verifier, Instant startedAt) {}

    private final Map<Long, PendingAuthorization> pending = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final RestClient restClient;
    private final MalProperties properties;

    public MalOAuthService(RestClient.Builder builder, MalProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    /**
     * The address to send the reader to. Mints and remembers the PKCE verifier for this
     * user; with the {@code plain} method the challenge in the URL is the verifier itself.
     * Starting again simply replaces the pending one — only the newest attempt can finish.
     */
    public String authorizationUrl(Long userId, String redirectUri) {
        requireConfigured();

        String verifier = newVerifier();
        pending.put(userId, new PendingAuthorization(verifier, Instant.now()));

        return "%s?response_type=code&client_id=%s&redirect_uri=%s&code_challenge=%s&code_challenge_method=plain"
                .formatted(
                        properties.authorizeUrl(),
                        encode(properties.clientId()),
                        encode(redirectUri),
                        encode(verifier));
    }

    /**
     * Swaps the code for tokens and asks MAL who they belong to. Consumes the verifier:
     * a code is single-use, so the proof that accompanies it should be too.
     */
    public Connection exchangeCode(Long userId, String code, String redirectUri) {
        requireConfigured();

        PendingAuthorization started = pending.remove(userId);
        if (started == null || started.startedAt().isBefore(Instant.now().minus(PENDING_LIFETIME))) {
            throw new MalAuthorizationExpiredException();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("redirect_uri", redirectUri);
        form.add("code_verifier", started.verifier());
        addSecret(form);

        Map<String, Object> token = postForm(form);
        Tokens tokens = tokens(token, null);
        return new Connection(username(tokens.accessToken()), tokens.accessToken(), tokens.refreshToken(), tokens.expiresAt());
    }

    /**
     * Trades the refresh token for a fresh pair. MAL may answer without a new refresh
     * token; per its behaviour that means the old one is still good, so it is kept rather
     * than overwritten with nothing.
     */
    public Tokens refresh(String refreshToken) {
        requireConfigured();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", properties.clientId());
        addSecret(form);

        return tokens(postForm(form), refreshToken);
    }

    private Tokens tokens(Map<String, Object> token, String previousRefreshToken) {
        String accessToken = string(token.get("access_token"));
        if (accessToken == null) {
            throw new MalUnavailableException("MyAnimeList returned no access token");
        }

        Instant expiresAt = token.get("expires_in") instanceof Number seconds
                ? Instant.now().plusSeconds(seconds.longValue())
                : Instant.now().plus(DEFAULT_LIFETIME);

        String refreshToken = string(token.get("refresh_token"));
        return new Tokens(accessToken, refreshToken != null ? refreshToken : previousRefreshToken, expiresAt);
    }

    /** Who the token belongs to: the name a person recognises, from {@code users/@me}. */
    @SuppressWarnings("unchecked")
    private String username(String accessToken) {
        try {
            Map<String, Object> me = restClient
                    .get()
                    .uri(properties.apiUrl() + "/users/@me?fields=name")
                    .header("Authorization", "Bearer " + accessToken)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new MalUnavailableException(
                                    "MyAnimeList responded with " + response.getStatusCode().value());
                        }
                        return (Map<String, Object>) response.bodyTo(Map.class);
                    });

            String name = me == null ? null : string(me.get("name"));
            if (name == null) {
                throw new MalUnavailableException("MyAnimeList did not identify the account");
            }
            return name;
        } catch (RestClientException e) {
            throw new MalUnavailableException("MyAnimeList request failed", e);
        }
    }

    /** MAL's token endpoint speaks forms, not JSON — send it anything else and it refuses. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> postForm(MultiValueMap<String, String> form) {
        try {
            Map<String, Object> response = restClient
                    .post()
                    .uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Accept", "application/json")
                    .body(form)
                    .exchange((request, res) -> {
                        HttpStatusCode status = res.getStatusCode();
                        if (status.isError()) {
                            throw new MalUnavailableException("MyAnimeList responded with " + status.value());
                        }
                        return (Map<String, Object>) res.bodyTo(Map.class);
                    });

            return response == null ? Map.of() : response;
        } catch (RestClientException e) {
            throw new MalUnavailableException("MyAnimeList request failed", e);
        }
    }

    /** A public client has no secret; PKCE is its whole proof. */
    private void addSecret(MultiValueMap<String, String> form) {
        if (properties.clientSecret() != null && !properties.clientSecret().isBlank()) {
            form.add("client_secret", properties.clientSecret());
        }
    }

    /**
     * RFC 7636 wants 43–128 characters of [A-Za-z0-9-._~]; 64 random bytes in base64url
     * give 86 of them.
     */
    private String newVerifier() {
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Abandoned attempts are dropped, so the map cannot grow with people who never return. */
    @Scheduled(fixedDelay = 300_000)
    public void evictAbandoned() {
        Instant cutoff = Instant.now().minus(PENDING_LIFETIME);
        pending.values().removeIf(attempt -> attempt.startedAt().isBefore(cutoff));
    }

    private void requireConfigured() {
        if (!properties.canConnectAccounts()) {
            throw new MalNotConfiguredException();
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
