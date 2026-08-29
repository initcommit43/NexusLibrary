package dev.nexus.modules.film;

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

/** What a film's own page is given, and what is left behind on the way in. */
class TmdbDetailTest {

    private static final String MOVIE_ID = "movie:603";

    private TmdbClient client;
    private TmdbMetadataAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(TmdbClient.class);
        adapter = new TmdbMetadataAdapter(
                client,
                new TmdbProperties(
                        "https://api.themoviedb.org/3",
                        "https://image.tmdb.org/t/p/",
                        "w500",
                        "w1280",
                        "w185",
                        "token",
                        20));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detailOf(TmdbKind kind, Map<String, Object> row) {
        String externalId = kind.externalId(603);
        when(client.findDetail(kind, "603")).thenReturn(Optional.of(row));
        return adapter.fetchDetail(externalId).orElseThrow();
    }

    private Map<String, Object> movie(Map<String, Object> extra) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 603);
        row.put("title", "The Matrix");
        row.putAll(extra);
        return row;
    }

    @SuppressWarnings("unchecked")
    @Test
    void anActorIsNamedWithThePartTheyPlayed() {
        Map<String, Object> detail = detailOf(
                TmdbKind.MOVIE,
                movie(Map.of(
                        "credits",
                        Map.of(
                                "cast",
                                List.of(Map.of("name", "Keanu Reeves", "character", "Neo", "profile_path", "/k.jpg"))))));

        assertThat((List<Map<String, Object>>) detail.get("cast"))
                .containsExactly(Map.of(
                        "name", "Keanu Reeves",
                        "role", "Neo",
                        "image", "https://image.tmdb.org/t/p/w185/k.jpg"));
    }

    /** Crew is most of what TMDB knows and almost none of it belongs on a page. */
    @SuppressWarnings("unchecked")
    @Test
    void onlyTheCreditsAPageNamesSurviveTheCrew() {
        Map<String, Object> detail = detailOf(
                TmdbKind.MOVIE,
                movie(Map.of(
                        "credits",
                        Map.of(
                                "crew",
                                List.of(
                                        Map.of("name", "Lana Wachowski", "job", "Director"),
                                        Map.of("name", "Bill Pope", "job", "Director of Photography"),
                                        Map.of("name", "Someone Else", "job", "Best Boy Electric"))))));

        assertThat((List<Map<String, Object>>) detail.get("crew"))
                .extracting(member -> member.get("name"))
                .containsExactly("Lana Wachowski", "Bill Pope");
    }

    /** Someone who directed and also wrote it is one tile, not three. */
    @SuppressWarnings("unchecked")
    @Test
    void aPersonCreditedTwiceGetsOneTile() {
        Map<String, Object> detail = detailOf(
                TmdbKind.MOVIE,
                movie(Map.of(
                        "credits",
                        Map.of(
                                "crew",
                                List.of(
                                        Map.of("name", "Lana Wachowski", "job", "Director"),
                                        Map.of("name", "Lana Wachowski", "job", "Writer"))))));

        assertThat((List<Map<String, Object>>) detail.get("crew"))
                .containsExactly(newTile("Lana Wachowski", "Director"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void aVideoThePageCannotEmbedIsLeftOut() {
        Map<String, Object> detail = detailOf(
                TmdbKind.MOVIE,
                movie(Map.of(
                        "videos",
                        Map.of(
                                "results",
                                List.of(
                                        Map.of("site", "Vimeo", "key", "vvv", "type", "Trailer", "name", "Elsewhere"),
                                        Map.of("site", "YouTube", "key", "yyy", "type", "Teaser", "name", "Teaser"),
                                        Map.of("site", "YouTube", "key", "zzz", "type", "Trailer", "name", "Trailer"))))));

        // The trailer leads, whatever order TMDB listed them in.
        assertThat((List<Map<String, Object>>) detail.get("videos"))
                .extracting(video -> video.get("id"))
                .containsExactly("zzz", "yyy");
    }

    @SuppressWarnings("unchecked")
    @Test
    void anImagePathBecomesSomethingThePageCanLoad() {
        Map<String, Object> detail = detailOf(
                TmdbKind.MOVIE,
                movie(Map.of("images", Map.of("backdrops", List.of(Map.of("file_path", "/wide.jpg"))))));

        assertThat((List<String>) detail.get("backdrops"))
                .containsExactly("https://image.tmdb.org/t/p/w1280/wide.jpg");
    }

    /**
     * TMDB numbers films and shows separately, so a bare id names two different titles. A
     * recommendation carrying one would open a page that cannot resolve.
     */
    @SuppressWarnings("unchecked")
    @Test
    void aRecommendationCarriesTheIdThisAppAddressesItBy() {
        Map<String, Object> detail = detailOf(
                TmdbKind.MOVIE,
                movie(Map.of(
                        "recommendations",
                        Map.of(
                                "results",
                                List.of(Map.of(
                                        "id", 604,
                                        "title", "The Matrix Reloaded",
                                        "poster_path", "/r.jpg",
                                        "release_date", "2003-05-15"))))));

        Map<String, Object> first = ((List<Map<String, Object>>) detail.get("recommendations")).getFirst();

        assertThat(first).containsEntry("id", "movie:604").containsEntry("year", "2003");
        assertThat(first).containsEntry("cover", "https://image.tmdb.org/t/p/w500/r.jpg");
    }

    @SuppressWarnings("unchecked")
    @Test
    void aShowIsRecommendedAsAShow() {
        Map<String, Object> detail = detailOf(
                TmdbKind.SHOW,
                Map.of(
                        "id",
                        603,
                        "name",
                        "The Wire",
                        "recommendations",
                        Map.of("results", List.of(Map.of("id", 1438, "name", "The Sopranos")))));

        assertThat(((List<Map<String, Object>>) detail.get("recommendations")).getFirst())
                .containsEntry("id", "tv:1438");
    }

    @SuppressWarnings("unchecked")
    @Test
    void aShowsKeywordsAreReadFromWhereShowsKeepThem() {
        Map<String, Object> detail = detailOf(
                TmdbKind.SHOW,
                Map.of(
                        "id",
                        603,
                        "name",
                        "The Wire",
                        "keywords",
                        Map.of("results", List.of(Map.of("name", "baltimore"), Map.of("name", "police")))));

        assertThat((List<String>) detail.get("keywords")).containsExactly("baltimore", "police");
    }

    @SuppressWarnings("unchecked")
    @Test
    void theOfficialSiteAndImdbAreOfferedAsLinks() {
        Map<String, Object> detail = detailOf(
                TmdbKind.MOVIE,
                movie(Map.of(
                        "homepage", "https://thematrix.com",
                        "external_ids", Map.of("imdb_id", "tt0133093"))));

        assertThat((List<Map<String, Object>>) detail.get("links"))
                .containsExactly(
                        Map.of("site", "Official site", "url", "https://thematrix.com"),
                        Map.of("site", "IMDb", "url", "https://www.imdb.com/title/tt0133093"));
    }

    /** Zero is how TMDB writes "nobody told us", and an unknown budget is not a fact. */
    @Test
    void moneyNobodyReportedIsNotStoredAsZero() {
        Map<String, Object> detail =
                detailOf(TmdbKind.MOVIE, movie(Map.of("budget", 0, "revenue", 463517383L)));

        assertThat(detail).doesNotContainKey("budget");
        assertThat(detail).containsEntry("revenue", 463517383L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void aLongCastIsCutToWhatThePageShows() {
        List<Map<String, Object>> cast = IntStream.range(0, 40)
                .mapToObj(index -> Map.<String, Object>of("name", "Actor " + index, "character", "Part " + index))
                .toList();

        Map<String, Object> detail = detailOf(TmdbKind.MOVIE, movie(Map.of("credits", Map.of("cast", cast))));

        assertThat((List<Map<String, Object>>) detail.get("cast")).hasSize(18);
    }

    /** A title TMDB knows nothing extra about stores nothing rather than an empty shell. */
    @Test
    void aTitleWithNoExtrasHasNoDetailWorthKeeping() {
        assertThat(detailOf(TmdbKind.MOVIE, movie(Map.of()))).isEmpty();
    }

    @Test
    void anIdWithoutAKindIsNotFetchedAtAll() {
        assertThat(adapter.fetchDetail("603")).isEmpty();
    }

    private Map<String, Object> newTile(String name, String role) {
        Map<String, Object> tile = new LinkedHashMap<>();
        tile.put("name", name);
        tile.put("role", role);
        tile.put("image", null);
        return tile;
    }
}
