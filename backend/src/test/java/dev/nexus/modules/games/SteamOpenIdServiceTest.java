package dev.nexus.modules.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** What the callback has to prove before a SteamID is believed. */
class SteamOpenIdServiceTest {

    private static final String OPENID_ENDPOINT = "https://steamcommunity.test/openid/login";
    private static final String RETURN_TO = "http://localhost:5173/settings/steam/callback";
    private static final String STEAM_ID = "76561198000000001";

    private MockRestServiceServer server;
    private SteamOpenIdService steam;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        steam = new SteamOpenIdService(builder, new SteamProperties("key", "https://api.steam.test", OPENID_ENDPOINT, 1));
    }

    @Test
    void aSignedAssertionForOurOwnCallbackYieldsTheSteamId() {
        server.expect(requestTo(OPENID_ENDPOINT))
                .andRespond(withSuccess("ns:http://specs.openid.net/auth/2.0\nis_valid:true\n", MediaType.TEXT_PLAIN));

        assertThat(steam.verifyCallback(callback(RETURN_TO), RETURN_TO)).contains(STEAM_ID);
        server.verify();
    }

    /**
     * An assertion Steam signed for somebody else's site can be lured into this callback, so
     * it is refused on its return_to alone — and without wasting the round trip on it.
     */
    @Test
    void anAssertionAddressedToAnotherSiteIsRefusedWithoutAskingSteam() {
        assertThat(steam.verifyCallback(callback("https://attacker.test/steam/callback"), RETURN_TO))
                .isEmpty();
        // No expectation was set: check_authentication must never have been sent.
        server.verify();
    }

    @Test
    void anAssertionSteamDoesNotVouchForIsRefused() {
        server.expect(requestTo(OPENID_ENDPOINT))
                .andRespond(withSuccess("is_valid:false\n", MediaType.TEXT_PLAIN));

        assertThat(steam.verifyCallback(callback(RETURN_TO), RETURN_TO)).isEmpty();
    }

    private static Map<String, String> callback(String returnTo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("openid.ns", "http://specs.openid.net/auth/2.0");
        params.put("openid.mode", "id_res");
        params.put("openid.return_to", returnTo);
        params.put("openid.claimed_id", "https://steamcommunity.com/openid/id/" + STEAM_ID);
        params.put("openid.sig", "a-signature");
        return params;
    }
}
