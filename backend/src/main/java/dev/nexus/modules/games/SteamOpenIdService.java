package dev.nexus.modules.games;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Steam sign-in over OpenID 2.0.
 *
 * <p>Worth being precise about what this provides: it proves the visitor controls a
 * SteamID, and nothing else. There is no token and no granted scope, which is why a
 * private profile stays unreadable even after a successful sign-in.
 */
@Service
public class SteamOpenIdService {

    private static final String CLAIMED_ID_PREFIX = "https://steamcommunity.com/openid/id/";
    private static final int STEAM_ID_LENGTH = 17;

    private final RestClient restClient;
    private final SteamProperties properties;

    public SteamOpenIdService(RestClient.Builder builder, SteamProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    /** The URL to send the browser to, which returns the user to {@code returnTo}. */
    public URI authenticationUrl(String returnTo, String realm) {
        return UriComponentsBuilder.fromUriString(properties.openIdEndpoint())
                .queryParam("openid.ns", "http://specs.openid.net/auth/2.0")
                .queryParam("openid.mode", "checkid_setup")
                .queryParam("openid.return_to", returnTo)
                .queryParam("openid.realm", realm)
                .queryParam("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select")
                .queryParam("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select")
                .build()
                .toUri();
    }

    /**
     * Asks Steam to confirm the callback it supposedly sent, and returns the SteamID it
     * vouches for.
     *
     * <p>The parameters have to be echoed back for confirmation rather than trusted as they
     * arrive. Without that round trip anyone could call the callback with a hand-written
     * claimed_id and be signed in as whichever account they named.
     */
    public Optional<String> verifyCallback(Map<String, String> params) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        new LinkedHashMap<>(params).forEach(form::add);
        form.set("openid.mode", "check_authentication");

        String response = restClient
                .post()
                .uri(properties.openIdEndpoint())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        if (response == null || !response.contains("is_valid:true")) {
            return Optional.empty();
        }

        return Optional.ofNullable(params.get("openid.claimed_id"))
                .map(claimed -> URLDecoder.decode(claimed, StandardCharsets.UTF_8))
                .filter(claimed -> claimed.startsWith(CLAIMED_ID_PREFIX))
                .map(claimed -> claimed.substring(CLAIMED_ID_PREFIX.length()))
                .filter(SteamOpenIdService::isSteamId);
    }

    private static boolean isSteamId(String value) {
        return value.length() == STEAM_ID_LENGTH && value.chars().allMatch(Character::isDigit);
    }
}
