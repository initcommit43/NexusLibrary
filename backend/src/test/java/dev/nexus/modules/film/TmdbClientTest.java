package dev.nexus.modules.film;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Pins the requests actually sent to TMDB, and what its refusals turn into on this side. */
class TmdbClientTest {

    private MockRestServiceServer server;
    private TmdbClient client;

    @BeforeEach
    void setUp() {
        client = build("token");
    }

    private TmdbClient build(String accessToken) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new TmdbClient(
                builder,
                new TmdbProperties("https://tmdb.test/3", "https://img.tmdb.test/t/p/", "w500", accessToken, 100));
    }

    /** The v4 token is a bearer, not a query parameter — sending it as one authenticates nothing. */
    @Test
    void sendsTheReadTokenAsABearer() {
        server.expect(requestTo("https://tmdb.test/3/movie/550"))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("{\"id\":550,\"title\":\"Fight Club\"}", MediaType.APPLICATION_JSON));

        assertThat(client.findById(TmdbKind.MOVIE, "550")).isPresent();
        server.verify();
    }

    @Test
    void searchesTheEndpointForTheKindAndEncodesTheQuery() {
        server.expect(requestTo("https://tmdb.test/3/search/tv?query=avatar%20%26%20co&include_adult=false&page=1"))
                .andRespond(withSuccess("{\"results\":[{\"id\":1,\"name\":\"Avatar\"}]}", MediaType.APPLICATION_JSON));

        List<Map<String, Object>> results = client.search(TmdbKind.SHOW, "avatar & co", 10);

        assertThat(results).hasSize(1);
        server.verify();
    }

    /** TMDB pages at 20; a search box asking for five should not be handed twenty. */
    @Test
    void trimsResultsToTheRequestedLimit() {
        String rows = String.join(",", java.util.Collections.nCopies(20, "{\"id\":1,\"title\":\"x\"}"));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://tmdb.test/3/search/movie")))
                .andRespond(withSuccess("{\"results\":[" + rows + "]}", MediaType.APPLICATION_JSON));

        assertThat(client.search(TmdbKind.MOVIE, "x", 5)).hasSize(5);
    }

    /** A deleted or unknown id is a miss, not an outage: the caller wants an empty. */
    @Test
    void aMissingTitleIsEmptyRatherThanAnOutage() {
        server.expect(requestTo("https://tmdb.test/3/movie/999999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"status_message\":\"The resource you requested could not be found.\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        Optional<Map<String, Object>> found = client.findById(TmdbKind.MOVIE, "999999");

        assertThat(found).isEmpty();
    }

    /** TMDB explains its refusals, and that sentence is what the reader should be shown. */
    @Test
    void carriesTmdbsOwnWordsOutOfAnError() {
        server.expect(requestTo("https://tmdb.test/3/movie/550"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"status_message\":\"Invalid API key: You must be granted a valid key.\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findById(TmdbKind.MOVIE, "550"))
                .isInstanceOf(TmdbUnavailableException.class)
                .satisfies(thrown -> assertThat(((TmdbUnavailableException) thrown).serviceSays())
                        .contains("Invalid API key: You must be granted a valid key."));
    }

    /** A gateway's HTML error page is not TMDB speaking, so nothing is quoted to the reader. */
    @Test
    void quotesNothingWhenTheBodyIsNotTmdbs() {
        server.expect(requestTo("https://tmdb.test/3/movie/550"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>502</html>").contentType(MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.findById(TmdbKind.MOVIE, "550"))
                .isInstanceOf(TmdbUnavailableException.class)
                .satisfies(thrown -> assertThat(((TmdbUnavailableException) thrown).serviceSays()).isEmpty());
    }

    /** The IMDb index is the resolver fallback, so the kind decides which result list to read. */
    @Test
    void findsATmdbIdThroughTheImdbIndex() {
        server.expect(requestTo("https://tmdb.test/3/find/tt0903747?external_source=imdb_id"))
                .andRespond(withSuccess(
                        "{\"movie_results\":[],\"tv_results\":[{\"id\":1396,\"name\":\"Breaking Bad\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.findIdByImdbId(TmdbKind.SHOW, "tt0903747")).contains("1396");
        server.verify();
    }

    /** A film's IMDb id must not be answered with a show of the same name, or the reverse. */
    @Test
    void readsOnlyTheResultListForTheKindAskedFor() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://tmdb.test/3/find/tt0137523")))
                .andRespond(withSuccess(
                        "{\"movie_results\":[{\"id\":550}],\"tv_results\":[{\"id\":999}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.findIdByImdbId(TmdbKind.MOVIE, "tt0137523")).contains("550");
    }

    @Test
    void anImdbIdTmdbDoesNotKnowFindsNothing() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://tmdb.test/3/find/tt9999999")))
                .andRespond(withSuccess("{\"movie_results\":[],\"tv_results\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.findIdByImdbId(TmdbKind.MOVIE, "tt9999999")).isEmpty();
    }

    /** No token configured disables film search; it must not look like TMDB being down. */
    @Test
    void saysSoWhenNoTokenIsConfigured() {
        TmdbClient unconfigured = build("");

        assertThatThrownBy(() -> unconfigured.search(TmdbKind.MOVIE, "x", 5))
                .isInstanceOf(TmdbUnavailableException.class)
                .hasMessageContaining("TMDB_ACCESS_TOKEN");
    }
}
