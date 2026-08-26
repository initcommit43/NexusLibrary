package dev.nexus.modules.books;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Pins the requests actually sent to Open Library, and what its refusals turn into here. */
class OpenLibraryClientTest {

    private static final String FIELDS =
            "key,title,author_name,first_publish_year,cover_i,number_of_pages_median,subject,ratings_average";

    private MockRestServiceServer server;
    private OpenLibraryClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenLibraryClient(
                builder,
                new OpenLibraryProperties(
                        "https://ol.test", "https://covers.test", "L", "NexusTest/1.0 (test@example.com)", 100));
    }

    /**
     * Open Library throttles callers who do not identify themselves, so this header is a
     * condition of use rather than a courtesy.
     */
    @Test
    void identifiesItselfOnEveryRequest() {
        server.expect(requestTo("https://ol.test/search.json?q=isbn:9780441013593&fields=" + FIELDS + "&limit=1"))
                .andExpect(header("User-Agent", "NexusTest/1.0 (test@example.com)"))
                .andRespond(withSuccess("{\"docs\":[{\"key\":\"/works/OL893414W\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.findByIsbn("9780441013593")).isPresent();
        server.verify();
    }

    @Test
    void looksUpAWorkByItsGoodreadsId() {
        server.expect(requestTo("https://ol.test/search.json?q=id_goodreads:104&fields=" + FIELDS + "&limit=1"))
                .andRespond(withSuccess("{\"docs\":[{\"key\":\"/works/OL893414W\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.findByGoodreadsId("104")).isPresent();
        server.verify();
    }

    /** Title and author are separate parameters here, not operators inside the query. */
    @Test
    void searchesTitleAndAuthorSeparatelyAndEncodesBoth() {
        server.expect(requestTo(
                        "https://ol.test/search.json?title=dune%20%26%20co&author=herbert&fields=" + FIELDS + "&limit=5"))
                .andRespond(withSuccess("{\"docs\":[]}", MediaType.APPLICATION_JSON));

        client.findByTitleAndAuthor("dune & co", "herbert", 5);
        server.verify();
    }

    /**
     * The reason an import is not one request per book. Twenty keys go into one OR query, so a
     * five-hundred-book library costs twenty-five calls rather than five hundred.
     */
    @Test
    void batchesWorkLookupsIntoOneRequestPerTwenty() {
        List<String> ids = new java.util.ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            ids.add("OL" + i + "W");
        }

        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("key:/works/OL1W"),
                        org.hamcrest.Matchers.containsString("key:/works/OL20W"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("key:/works/OL21W")))))
                .andRespond(withSuccess("{\"docs\":[{\"key\":\"/works/OL1W\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("key:/works/OL21W")))
                .andRespond(withSuccess("{\"docs\":[{\"key\":\"/works/OL21W\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.findByWorkIds(ids)).hasSize(2);
        server.verify();
    }

    /** A repeated id costs nothing extra, which matters when a library holds two editions. */
    @Test
    void asksForEachWorkOnlyOnce() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("key:/works/OL1W")))
                .andRespond(withSuccess("{\"docs\":[]}", MediaType.APPLICATION_JSON));

        client.findByWorkIds(List.of("OL1W", "OL1W", "OL1W"));
        server.verify();
    }

    /** A withdrawn work is an empty answer, not an outage — the caller wants to carry on. */
    @Test
    void treatsAMissingWorkAsEmptyRatherThanAFailure() {
        server.expect(requestTo("https://ol.test/works/OL404W.json")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.fetchWork("OL404W")).isEmpty();
        server.verify();
    }

    @Test
    void turnsAServerFailureIntoAnOutageCarryingWhatItSaid() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search.json")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"error\":\"upstream is down\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search("dune", 5))
                .isInstanceOf(OpenLibraryUnavailableException.class)
                .satisfies(thrown -> assertThat(((OpenLibraryUnavailableException) thrown).serviceSays())
                        .contains("upstream is down"));
    }

    /** An HTML error page is the gateway talking, and is not worth repeating to a reader. */
    @Test
    void keepsGatewayNoiseOutOfTheOutageMessage() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search.json")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .body("<html>502 Bad Gateway</html>")
                        .contentType(MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.search("dune", 5))
                .isInstanceOf(OpenLibraryUnavailableException.class)
                .satisfies(thrown -> assertThat(((OpenLibraryUnavailableException) thrown).serviceSays())
                        .isEmpty());
    }

    @Test
    void readsTheDocsOutOfASearchResponse() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("q=dune")))
                .andRespond(withSuccess(
                        "{\"docs\":[{\"key\":\"/works/OL893414W\",\"title\":\"Dune\"}]}", MediaType.APPLICATION_JSON));

        List<Map<String, Object>> docs = client.search("dune", 5);

        assertThat(docs).hasSize(1);
        assertThat(docs.getFirst()).containsEntry("title", "Dune");
    }

    /** A search that matched nothing omits "docs" rather than sending an empty list. */
    @Test
    void treatsAResponseWithoutDocsAsNoResults() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("q=nothing")))
                .andRespond(withSuccess("{\"numFound\":0}", MediaType.APPLICATION_JSON));

        assertThat(client.search("nothing", 5)).isEmpty();
    }
}
