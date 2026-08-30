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
        client = new AniListClient(builder, new AniListProperties(ENDPOINT, "id", "secret", "https://anilist.test/oauth/authorize", "https://anilist.test/oauth/token", 6000, 1));
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

    /**
     * AniList answers 502 or 504 through Cloudflare often enough that one blip must not lose
     * an import, so a gateway error is tried again before it is reported.
     */
    @Test
    void aGatewayErrorIsRetriedBeforeItIsReported() {
        server.expect(
                        org.springframework.test.web.client.ExpectedCount.times(3),
                        requestTo(ENDPOINT))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        org.assertj.core.api.Assertions.assertThatExceptionOfType(AniListUnavailableException.class)
                .isThrownBy(() -> client.findMediaById("21"));

        server.verify();
    }

    /** A query we got wrong fails identically every time; retrying only spends the budget. */
    @Test
    void aRequestAniListRefusesIsNotRetried() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest());

        org.assertj.core.api.Assertions.assertThatExceptionOfType(AniListUnavailableException.class)
                .isThrownBy(() -> client.findMediaById("21"));

        server.verify();
    }

    /**
     * When AniList refuses on purpose it says why — "temporarily disabled due to severe
     * stability issues", once, for weeks — and those words explain the failure better than
     * anything we could write. They must survive onto the exception.
     */
    @Test
    void aRefusalCarriesAniListsOwnWords() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"message\":\"The AniList API has been temporarily "
                                + "disabled due to severe stability issues.\",\"status\":403}],\"data\":null}"));

        org.assertj.core.api.Assertions.assertThatExceptionOfType(AniListUnavailableException.class)
                .isThrownBy(() -> client.findMediaById("21"))
                .satisfies(e -> assertThat(e.serviceSays())
                        .hasValueSatisfying(words -> assertThat(words).contains("temporarily disabled")));

        server.verify();
    }

    /** A Cloudflare error page is the gateway talking, not AniList: no words worth quoting. */
    @Test
    void aGatewaysErrorPageIsNotMistakenForAniListSpeaking() {
        server.expect(
                        org.springframework.test.web.client.ExpectedCount.times(3),
                        requestTo(ENDPOINT))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.BAD_GATEWAY)
                        .contentType(org.springframework.http.MediaType.TEXT_HTML)
                        .body("<html><body><h1>502 Bad Gateway</h1></body></html>"));

        org.assertj.core.api.Assertions.assertThatExceptionOfType(AniListUnavailableException.class)
                .isThrownBy(() -> client.findMediaById("21"))
                .satisfies(e -> assertThat(e.serviceSays()).isEmpty());

        server.verify();
    }

    /**
     * The regression guard for a year that matched nothing at all on the manga side.
     *
     * <p>AniList files a season, and so a season year, against anime only. Manga carries no
     * season, so {@code seasonYear} there is not a loose filter but an impossible one.
     */
    @Test
    void animeIsNarrowedToAYearBySeasonAndMangaByStartDate() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().string(containsString("\"seasonYear\":2023")))
                .andRespond(withSuccess(page("[]"), org.springframework.http.MediaType.APPLICATION_JSON));

        client.discoverMedia(MediaType.ANIME, null, List.of(), List.of(), 2023, null, null, null, 1, 20);
        server.verify();

        server.reset();
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().string(containsString("\"startDate_greater\":20230000")))
                .andExpect(content().string(containsString("\"startDate_lesser\":20240000")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("seasonYear"))))
                .andRespond(withSuccess(page("[]"), org.springframework.http.MediaType.APPLICATION_JSON));

        client.discoverMedia(MediaType.MANGA, null, List.of(), List.of(), 2023, null, null, null, 1, 20);
        server.verify();
    }

    /** A term ranks by match; without one there is nothing to rank by but popularity. */
    @Test
    void aTermSortsByMatchAndItsAbsenceByPopularity() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().string(containsString("SEARCH_MATCH")))
                .andRespond(withSuccess(page("[]"), org.springframework.http.MediaType.APPLICATION_JSON));

        client.discoverMedia(MediaType.ANIME, "frieren", List.of(), List.of(), null, null, null, null, 1, 20);
        server.verify();

        server.reset();
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().string(containsString("POPULARITY_DESC")))
                .andRespond(withSuccess(page("[]"), org.springframework.http.MediaType.APPLICATION_JSON));

        client.discoverMedia(MediaType.ANIME, "  ", List.of(), List.of(), null, null, null, null, 1, 20);
        server.verify();
    }

    private static String page(String mediaJson) {
        return "{\"data\":{\"Page\":{\"media\":%s}}}".formatted(mediaJson);
    }

    /**
     * The regression guard for the bug that made every non-seasonal shelf come back empty.
     *
     * <p>AniList reads an argument it never received as "no filter", but one it received as
     * null as a filter for null. Declaring {@code season} and passing nothing is therefore not
     * the same as leaving it out, and the difference is invisible until live data is involved:
     * the query stays valid and AniList answers 200 with almost nothing in it.
     */
    @Test
    void leavesAnUnusedBrowseFilterOutOfTheQueryEntirely() {
        server.expect(requestTo(ENDPOINT))
                // Asserted on the argument syntax, not the bare words: "status" and "format"
                // are also fields this query asks for, and always appear in the body.
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("season: $season"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("status: $status"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("format: $format"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("null"))))
                .andExpect(content().string(containsString("TRENDING_DESC")))
                .andRespond(withSuccess(page("[]"), org.springframework.http.MediaType.APPLICATION_JSON));

        client.browseMedia(MediaType.ANIME, "TRENDING_DESC", null, null, null, null, 1, 24);
        server.verify();
    }

    /** A shelf that does use a filter has to declare it and send it. */
    @Test
    void declaresABrowseFilterThatIsActuallyUsed() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().string(containsString("$season: MediaSeason")))
                .andExpect(content().string(containsString("season: $season")))
                .andExpect(content().string(containsString("\"season\":\"SPRING\"")))
                .andExpect(content().string(containsString("\"seasonYear\":2026")))
                .andRespond(withSuccess(page("[]"), org.springframework.http.MediaType.APPLICATION_JSON));

        client.browseMedia(MediaType.ANIME, "POPULARITY_DESC", "SPRING", 2026, null, null, 1, 24);
        server.verify();
    }

    /** AniList reports whether there is another page, so a grid never guesses at a next button. */
    @Test
    void readsWhetherAniListHasAnotherPage() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(
                        "{\"data\":{\"Page\":{\"pageInfo\":{\"hasNextPage\":true},\"media\":[]}}}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(client.browseMedia(MediaType.ANIME, "TRENDING_DESC", null, null, null, null, 1, 24).hasNextPage())
                .isTrue();
    }
}
