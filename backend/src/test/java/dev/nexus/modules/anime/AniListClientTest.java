package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.nexus.core.domain.MediaType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Pins what is actually sent to AniList.
 *
 * <p>These exist because a wrong-but-valid GraphQL query fails quietly: AniList answers with
 * a null media and a 200, so a lookup simply finds nothing and the fault surfaces as an
 * unmatched title far from its cause.
 */
class AniListClientTest {

    private static final String ENDPOINT = "https://anilist.test/graphql";

    private MockRestServiceServer server;
    private AniListClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AniListClient(builder, new AniListProperties(ENDPOINT, "id", "secret", "https://anilist.test/oauth/authorize", "https://anilist.test/oauth/token", 6000));
    }

    @Test
    void searchAsksForTheMediaTypeItWasGiven() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().string(containsString("\"type\":\"MANGA\"")))
                .andExpect(content().string(containsString("\"search\":\"berserk\"")))
                .andRespond(withSuccess(page("[]"), org.springframework.http.MediaType.APPLICATION_JSON));

        client.searchMedia(MediaType.MANGA, "berserk", 10);

        server.verify();
    }

    /** Anime is the default only because it is the larger half; manga must never fall into it. */
    @Test
    void animeAndMangaMapToAniListsOwnTypeNames() {
        assertThat(AniListClient.anilistType(MediaType.ANIME)).isEqualTo("ANIME");
        assertThat(AniListClient.anilistType(MediaType.MANGA)).isEqualTo("MANGA");
    }

    @Test
    void aMediaLookupReturnsTheSingleRecordAsAList() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().string(containsString("\"id\":21")))
                .andRespond(withSuccess(
                        "{\"data\":{\"Media\":{\"id\":21,\"type\":\"ANIME\"}}}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        List<Map<String, Object>> media = client.findMediaById("21");

        assertThat(media).hasSize(1);
        assertThat(media.getFirst()).containsEntry("id", 21);
        server.verify();
    }

    /** A title AniList does not have is a miss, not a failure: the caller wants an empty list. */
    @Test
    void aMissingMediaComesBackEmptyRatherThanThrowing() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(
                        "{\"data\":{\"Media\":null}}", org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(client.findMediaById("999999")).isEmpty();
    }

    @Test
    void aMalIdLookupIsScopedToOneMediaType() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().string(containsString("\"idMal\":11061")))
                .andExpect(content().string(containsString("\"type\":\"ANIME\"")))
                .andRespond(withSuccess(
                        "{\"data\":{\"Media\":{\"id\":11061,\"type\":\"ANIME\"}}}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(client.findMediaByMalId(MediaType.ANIME, "11061")).hasSize(1);
        server.verify();
    }

    @Test
    void bulkLookupsSendEveryIdInOneRequest() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().string(containsString("\"ids\":[1,5,21]")))
                .andRespond(withSuccess(page("[]"), org.springframework.http.MediaType.APPLICATION_JSON));

        client.findMediaByIds(List.of("1", "5", "21"));

        server.verify();
    }

    /** AniList caps a page at 50, so a longer list has to arrive as several requests. */
    @Test
    void moreIdsThanAPageHoldsArePartitioned() {
        List<String> ids = java.util.stream.IntStream.rangeClosed(1, 120)
                .mapToObj(String::valueOf)
                .toList();

        List<List<String>> batches = AniListClient.partition(ids);

        assertThat(batches).hasSize(3);
        assertThat(batches.getFirst()).hasSize(50);
        assertThat(batches.getLast()).hasSize(20);
    }

    @Test
    void anErrorStatusIsReportedRatherThanReturnedAsNoResults() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        org.assertj.core.api.Assertions.assertThatExceptionOfType(AniListUnavailableException.class)
                .isThrownBy(() -> client.findMediaById("21"));
    }

    private static String page(String mediaJson) {
        return "{\"data\":{\"Page\":{\"media\":%s}}}".formatted(mediaJson);
    }
}
