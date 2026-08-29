package dev.nexus.modules.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What a game's own page is given, and what is left behind on the way in. */
class IgdbDetailTest {

    private IgdbClient client;
    private IgdbMetadataAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(IgdbClient.class);
        adapter = new IgdbMetadataAdapter(client);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detailOf(Map<String, Object> game) {
        when(client.findGameDetail("1942")).thenReturn(Optional.of(game));
        return adapter.fetchDetail("1942").orElseThrow();
    }

    @Test
    void aCompanyIsNamedWithWhatItDid() {
        Map<String, Object> detail = detailOf(Map.of(
                "involved_companies",
                List.of(
                        Map.of("company", Map.of("name", "CD PROJEKT RED"), "developer", true, "publisher", true),
                        Map.of("company", Map.of("name", "WB Games"), "developer", false, "publisher", true))));

        assertThat((List<Map<String, Object>>) detail.get("companies"))
                .containsExactly(
                        Map.of("name", "CD PROJEKT RED", "role", "Developer & Publisher"),
                        Map.of("name", "WB Games", "role", "Publisher"));
    }

    @Test
    void expansionsAndDlcsAreOneListThatSaysWhichIsWhich() {
        Map<String, Object> detail = detailOf(Map.of(
                "dlcs", List.of(Map.of("id", 5, "name", "Hearts of Stone", "cover", Map.of("image_id", "abc"))),
                "expansions", List.of(Map.of("id", 6, "name", "Blood and Wine"))));

        List<Map<String, Object>> related = (List<Map<String, Object>>) detail.get("related");

        assertThat(related).hasSize(2);
        assertThat(related.get(0))
                .containsEntry("name", "Hearts of Stone")
                .containsEntry("relation", "DLC")
                .containsEntry("cover", "https://images.igdb.com/igdb/image/upload/t_cover_big/abc.jpg");
        assertThat(related.get(1)).containsEntry("relation", "Expansion").doesNotContainKey("cover");
    }

    @Test
    void aWebsiteKeepsTheNameItsKindGoesBy() {
        Map<String, Object> detail = detailOf(Map.of(
                "websites",
                List.of(Map.of("url", "https://thewitcher.com", "type", Map.of("type", "Official")))));

        assertThat((List<Map<String, Object>>) detail.get("websites"))
                .containsExactly(Map.of("site", "Official", "url", "https://thewitcher.com"));
    }

    /**
     * IGDB has no upper bound on these and the answer is stored on the shared item, so an
     * untrimmed blob is bytes every reader carries to be shown a handful.
     */
    @Test
    void longListsAreCutToWhatAPageShows() {
        Map<String, Object> many = new LinkedHashMap<>();
        many.put(
                "screenshots",
                IntStream.range(0, 600).mapToObj(i -> Map.<String, Object>of("image_id", "s" + i)).toList());
        many.put(
                "themes", IntStream.range(0, 50).mapToObj(i -> Map.<String, Object>of("name", "t" + i)).toList());

        Map<String, Object> detail = detailOf(many);

        assertThat((List<?>) detail.get("screenshots")).hasSize(8);
        assertThat((List<?>) detail.get("themes")).hasSize(12);
    }

    /** A key with nothing behind it is left out, so the page can ask whether a panel has data. */
    @Test
    void whatTheSourceDoesNotHaveIsNotStored() {
        Map<String, Object> detail = detailOf(Map.of("storyline", "A witcher hunts."));

        assertThat(detail).containsOnlyKeys("storyline");
    }

    @Test
    void bothRatingsAreKeptWithTheirCounts() {
        Map<String, Object> detail = detailOf(Map.of(
                "rating", 93.78,
                "rating_count", 5435,
                "aggregated_rating", 91.73,
                "aggregated_rating_count", 26));

        assertThat(detail)
                .containsEntry("ratingCount", 5435)
                .containsEntry("criticRatingCount", 26)
                .containsKeys("rating", "criticRating");
    }
}
