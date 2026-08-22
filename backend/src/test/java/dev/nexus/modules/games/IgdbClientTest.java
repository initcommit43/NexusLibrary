package dev.nexus.modules.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Pins the queries actually sent to IGDB.
 *
 * <p>These exist because a wrong-but-valid query fails silently: IGDB answers an empty list
 * rather than an error, so every lookup simply finds nothing and the fault surfaces as
 * "no matches" far from its cause.
 */
class IgdbClientTest {

    private MockRestServiceServer server;
    private IgdbClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        IgdbAuthClient auth = mock(IgdbAuthClient.class);
        when(auth.accessToken()).thenReturn("test-token");

        client = new IgdbClient(
                builder,
                auth,
                new IgdbProperties("test-client-id", "test-secret", "https://igdb.test/v4", "https://twitch.test", 1000));
    }

    /**
     * IGDB retired {@code category} on these rows in favour of {@code external_game_source};
     * filtering on the old field matches nothing at all.
     */
    @Test
    void steamCrossReferenceFiltersOnExternalGameSource() {
        server.expect(requestTo("https://igdb.test/v4/external_games"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("external_game_source = 1")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("category"))))
                .andRespond(withSuccess("[{\"game\":231,\"uid\":\"70\"}]", MediaType.APPLICATION_JSON));

        List<Map<String, Object>> rows = client.findGamesBySteamAppIds(List.of("70"));

        assertThat(rows).hasSize(1);
        server.verify();
    }

    @Test
    void steamAppIdsAreQuotedBecauseIgdbStoresThemAsStrings() {
        server.expect(requestTo("https://igdb.test/v4/external_games"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("uid = (\"70\",\"440\")")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.findGamesBySteamAppIds(List.of("70", "440"));

        server.verify();
    }

    @Test
    void bulkGameLookupUsesUnquotedNumericIds() {
        server.expect(requestTo("https://igdb.test/v4/games"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("where id = (7346,113112)")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.findGamesByIds(List.of("7346", "113112"));

        server.verify();
    }

    @Test
    void searchQueriesTheGamesEndpoint() {
        server.expect(requestTo("https://igdb.test/v4/games"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("search \"zelda\";")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.searchGames("zelda", 10);

        server.verify();
    }

    @Test
    void partitionSplitsAtIgdbsRowCap() {
        List<String> ids =
                java.util.stream.IntStream.rangeClosed(1, 1001).mapToObj(String::valueOf).toList();

        assertThat(IgdbClient.partition(ids)).hasSize(3);
        assertThat(IgdbClient.partition(List.of("1"))).hasSize(1);
        assertThat(IgdbClient.partition(List.of())).isEmpty();
    }
}
