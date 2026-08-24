package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MalClientTest {

    private static final String BASE = "https://mal.test/v2";

    private MockRestServiceServer server;
    private MalClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MalClient(builder, new MalProperties(BASE, "test-mal-client", 6000, 1));
    }

    /** The client id is the whole of MAL's auth for public reads; without it nothing works. */
    @Test
    void everyRequestCarriesTheClientIdHeader() {
        server.expect(requestTo(Matchers.startsWith(BASE + "/users/reader/animelist")))
                .andExpect(header("X-MAL-CLIENT-ID", "test-mal-client"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        client.fetchAnimeList("reader");

        server.verify();
    }

    /** By default MAL quietly withholds entries it rates as adult; a list must come whole. */
    @Test
    void listsAreRequestedWithNothingWithheld() {
        server.expect(requestTo(Matchers.startsWith(BASE + "/users/reader/mangalist")))
                .andExpect(queryParam("nsfw", "true"))
                // The braces travel percent-encoded, as URI query characters must.
                .andExpect(queryParam("fields", MalClient.MANGA_FIELDS.replace("{", "%7B").replace("}", "%7D")))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        client.fetchMangaList("reader");

        server.verify();
    }

    /** MAL pages by a hundred; a longer list must be followed to its end, not truncated. */
    @Test
    void pagingIsFollowedUntilMalStopsOfferingMore() {
        server.expect(requestTo(Matchers.containsString("offset=0")))
                .andRespond(withSuccess(
                        "{\"data\":[{\"node\":{\"id\":1}}],\"paging\":{\"next\":\"https://mal.test/v2/whatever\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.containsString("offset=100")))
                .andRespond(withSuccess("{\"data\":[{\"node\":{\"id\":2}}]}", MediaType.APPLICATION_JSON));

        List<Map<String, Object>> rows = client.fetchAnimeList("reader");

        assertThat(rows).hasSize(2);
        server.verify();
    }

    /** A username MAL does not know is the reader's typo, named as such — never retried. */
    @Test
    void anUnknownUsernameSaysSoInsteadOfFailingGenerically() {
        server.expect(requestTo(Matchers.startsWith(BASE + "/users/ghost/animelist")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatExceptionOfType(MalUserNotFoundException.class)
                .isThrownBy(() -> client.probeUser("ghost"))
                .satisfies(e -> assertThat(e.advice()).contains("ghost"));

        server.verify();
    }

    /** A refused list is a visibility setting, which only the reader can change. */
    @Test
    void aPrivateListNamesTheSettingToChange() {
        server.expect(requestTo(Matchers.startsWith(BASE + "/users/hermit/animelist")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatExceptionOfType(MalListPrivateException.class)
                .isThrownBy(() -> client.probeUser("hermit"))
                .satisfies(e -> assertThat(e.advice()).contains("Public"));

        server.verify();
    }

    /** The lesson AniList taught: one gateway blip must not lose a whole import. */
    @Test
    void aGatewayErrorIsRetriedBeforeItIsReported() {
        server.expect(ExpectedCount.times(3), requestTo(Matchers.startsWith(BASE + "/users/reader/animelist")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatExceptionOfType(MalUnavailableException.class)
                .isThrownBy(() -> client.fetchAnimeList("reader"))
                .satisfies(e -> assertThat(e.serviceName()).isEqualTo("MyAnimeList"));

        server.verify();
    }
}
