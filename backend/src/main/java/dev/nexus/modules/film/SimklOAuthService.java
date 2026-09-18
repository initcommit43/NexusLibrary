package dev.nexus.modules.film;

import static dev.nexus.core.adapter.Payloads.string;

import dev.nexus.core.domain.Provider;
import dev.nexus.core.security.OAuthStateMismatchException;
import dev.nexus.core.security.OAuthStateStore;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Simkl's authorization code grant — the least demanding of the four.
 *
 * <p>A plain code exchange with a client secret: no PKCE, so the only thing remembered
 * between sending the reader away and their coming back is the {@code state} that ties the
 * callback to the session that started it.
 * What sets it apart is the other end — a Simkl token has no expiry to speak of and comes
 * with no refresh token, because it lives until the reader revokes the app in their Simkl
 * settings. So there is no {@code refresh} here at all: a token that stops working has been
 * revoked, and only approving again can mend that.
 *
 * <p>The token endpoint takes JSON, and — like every other Simkl call — wants the
 * application identified in the query string as well.
 */
@Service
public class SimklOAuthService {

    /**
     * Where Simkl returns the browser. Named here rather than at each call site because the
     * exchange has to present the very same value the authorization did.
     */
    public static final String CALLBACK_PATH = "/settings/simkl/callback";

    /** Simkl issues no refresh token and states no expiry, so an account stores neither. */
    public record Connection(String externalUserId, String accessToken) {}

    private final RestClient restClient;
    private final SimklProperties properties;
    private final OAuthStateStore states;

    public SimklOAuthService(RestClient.Builder builder, SimklProperties properties, OAuthStateStore states) {
        this.restClient = builder.build();
        this.properties = properties;
        this.states = states;
    }

    /**
     * The address to send the reader to. Simkl asks for no challenge, but it does carry back
     * the state minted here, which is what binds the callback to this session.
     */
    public String authorizationUrl(Long userId, String redirectUri) {
        requireConfigured();

        return "%s?response_type=code&client_id=%s&redirect_uri=%s&app-name=%s&app-version=%s&state=%s"
                .formatted(
                        properties.authorizeUrl(),
                        encode(properties.clientId()),
                        encode(redirectUri),
                        encode(properties.appName()),
                        encode(properties.appVersion()),
                        encode(states.issue(Provider.SIMKL, userId)));
    }

    /**
     * Swaps the code for a token and asks Simkl whose account it is — but only for a
     * callback carrying the state this session minted, since a code lured in from elsewhere
     * would otherwise link the sender's account to the reader's.
     */
    public Connection exchangeCode(Long userId, String code, String state, String redirectUri) {
        requireConfigured();

        if (!states.consume(Provider.SIMKL, userId, state)) {
            throw new OAuthStateMismatchException("Simkl");
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("client_id", properties.clientId());
        body.put("client_secret", properties.clientSecret());
        body.put("redirect_uri", redirectUri);
        body.put("grant_type", "authorization_code");

        Map<String, Object> token = postJson(body);
        String accessToken = string(token.get("access_token"));
        if (accessToken == null) {
            throw new SimklUnavailableException("Simkl returned no access token");
        }

        return new Connection(username(accessToken), accessToken);
    }

    /**
     * Who the token belongs to: the name a person recognises. The settings endpoint is a
     * POST rather than a GET, which is Simkl's own convention and not a mistake here.
     */
    @SuppressWarnings("unchecked")
    private String username(String accessToken) {
        try {
            Map<String, Object> settings = restClient
                    .post()
                    .uri(properties.apiBaseUrl() + "/users/settings?" + properties.identifyingParams())
                    .headers(headers -> {
                        headers.set("Authorization", "Bearer " + accessToken);
                        headers.set("User-Agent", properties.userAgent());
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new SimklUnavailableException(
                                    "Simkl responded with " + response.getStatusCode().value());
                        }
                        return (Map<String, Object>) response.bodyTo(Map.class);
                    });

            String name = settings != null && settings.get("user") instanceof Map<?, ?> user
                    ? string(user.get("name"))
                    : null;
            if (name == null) {
                throw new SimklUnavailableException("Simkl did not identify the account");
            }
            return name;
        } catch (RestClientException e) {
            throw new SimklUnavailableException("Simkl request failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postJson(Map<String, String> body) {
        try {
            Map<String, Object> response = restClient
                    .post()
                    .uri(properties.tokenUrl() + "?" + properties.identifyingParams())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("User-Agent", properties.userAgent())
                    .body(body)
                    .exchange((request, res) -> {
                        HttpStatusCode status = res.getStatusCode();
                        if (status.isError()) {
                            throw new SimklUnavailableException("Simkl responded with " + status.value());
                        }
                        return (Map<String, Object>) res.bodyTo(Map.class);
                    });

            return response == null ? Map.of() : response;
        } catch (RestClientException e) {
            throw new SimklUnavailableException("Simkl request failed", e);
        }
    }

    private void requireConfigured() {
        if (!properties.canConnectAccounts()) {
            throw new SimklNotConfiguredException();
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
