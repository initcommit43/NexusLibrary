package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.BrowseShelf;
import dev.nexus.core.adapter.ItemSearchResult;
import dev.nexus.core.domain.MediaType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The shelves the anime and manga browse pages offer, and the AniList queries behind them. */
class AniListBrowseTest {

    private AniListClient client;
    private AniListMetadataAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(AniListClient.class);
        adapter = new AniListMetadataAdapter(client);
        when(client.browseMedia(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new AniListClient.MediaPage(List.of(), false));
    }

    private String sortFor(MediaType mediaType, String shelfId) {
        ArgumentCaptor<String> sort = ArgumentCaptor.forClass(String.class);
        adapter.browse(mediaType, shelfId, 1, 24);
        verify(client).browseMedia(any(), sort.capture(), any(), any(), any(), any(), anyInt(), anyInt());
        return sort.getValue();
    }

    /**
     * Anime is scheduled in broadcast seasons and read that way; manga is not, so it gets a
     * different set of rows rather than the same set with two of them empty.
     */
    @Test
    void offersSeasonalShelvesForAnimeAndNotForManga() {
        assertThat(adapter.browseShelves(MediaType.ANIME))
                .extracting(BrowseShelf::id)
                .containsExactly("trending", "this-season", "next-season", "popular", "top");

        assertThat(adapter.browseShelves(MediaType.MANGA))
                .extracting(BrowseShelf::id)
                .containsExactly("trending", "popular", "light-novels", "top");
    }

    /** AniList files light novels under MANGA, and they are a different thing to read. */
    @Test
    void givesLightNovelsARowOfTheirOwnOnTheMangaSide() {
        ArgumentCaptor<String> format = ArgumentCaptor.forClass(String.class);
        adapter.browse(MediaType.MANGA, "light-novels", 1, 24);
        verify(client).browseMedia(any(), any(), any(), any(), any(), format.capture(), anyInt(), anyInt());

        assertThat(format.getValue()).isEqualTo("NOVEL");
    }

    @Test
    void sortsEachShelfByWhatItIsFor() {
        assertThat(sortFor(MediaType.ANIME, "trending")).isEqualTo("TRENDING_DESC");
        setUp();
        assertThat(sortFor(MediaType.ANIME, "popular")).isEqualTo("POPULARITY_DESC");
        setUp();
        assertThat(sortFor(MediaType.ANIME, "top")).isEqualTo("SCORE_DESC");
    }

    /** A non-seasonal shelf must send no season at all, not the current one. */
    @Test
    void leavesTheSeasonUnsetOnShelvesThatAreNotSeasonal() {
        adapter.browse(MediaType.ANIME, "popular", 1, 24);

        verify(client).browseMedia(any(), any(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt());
    }

    @Test
    void asksForTheCurrentSeasonOnThisSeason() {
        ArgumentCaptor<String> season = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> year = ArgumentCaptor.forClass(Integer.class);
        adapter.browse(MediaType.ANIME, "this-season", 1, 24);
        verify(client).browseMedia(any(), any(), season.capture(), year.capture(), any(), any(), anyInt(), anyInt());

        LocalDate today = LocalDate.now();
        assertThat(season.getValue()).isEqualTo(AniListSeason.of(today).name());
        assertThat(year.getValue()).isEqualTo(today.getYear());
    }

    /** The season after this one, and next year's when this one is autumn. */
    @Test
    void asksForTheFollowingSeasonOnNextSeason() {
        ArgumentCaptor<String> season = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> year = ArgumentCaptor.forClass(Integer.class);
        adapter.browse(MediaType.ANIME, "next-season", 1, 24);
        verify(client).browseMedia(any(), any(), season.capture(), year.capture(), any(), any(), anyInt(), anyInt());

        LocalDate today = LocalDate.now();
        AniListSeason now = AniListSeason.of(today);
        assertThat(season.getValue()).isEqualTo(now.next().name());
        assertThat(year.getValue()).isEqualTo(now.nextYear(today.getYear()));
    }

    /** A shelf of what has not aired yet should not be full of things that already have. */
    @Test
    void limitsNextSeasonToWhatHasNotAiredYet() {
        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        adapter.browse(MediaType.ANIME, "next-season", 1, 24);
        verify(client).browseMedia(any(), any(), any(), any(), status.capture(), any(), anyInt(), anyInt());

        assertThat(status.getValue()).isEqualTo("NOT_YET_RELEASED");
    }

    /** A shelf anime has and manga does not must not be queryable through the manga page. */
    @Test
    void refusesASeasonalShelfAskedForAsManga() {
        assertThat(adapter.browse(MediaType.MANGA, "next-season", 1, 24).items()).isEmpty();

        verify(client, never()).browseMedia(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void asksForNothingWhenTheShelfIsUnknown() {
        assertThat(adapter.browse(MediaType.ANIME, "not-a-shelf", 1, 24).items()).isEmpty();

        verify(client, never()).browseMedia(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    /**
     * A ranked shelf shows a score and a format beside the title, and re-fetching each title to
     * find them out would cost a request per row.
     */
    @Test
    void carriesTheFactsARankedShelfShowsBesideTheTitle() {
        when(client.browseMedia(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new AniListClient.MediaPage(
                        List.of(Map.of(
                                "id", 1,
                                "type", "ANIME",
                                "title", Map.of("romaji", "Frieren"),
                                "format", "TV",
                                "episodes", 28,
                                "averageScore", 91,
                                "genres", List.of("Adventure", "Drama", "Fantasy"))),
                        false));

        ItemSearchResult result = adapter.browse(MediaType.ANIME, "top", 1, 24).items().getFirst();

        assertThat(result.facets())
                .containsEntry("score", 91)
                .containsEntry("format", "TV")
                .containsEntry("episodes", 28);
        assertThat(result.facets().get("genres")).isEqualTo(List.of("Adventure", "Drama", "Fantasy"));
    }

    /** A plain search hit needs none of that, and should not start carrying it. */
    @Test
    void leavesASearchHitWithoutFacets() {
        when(client.searchMedia(any(), any(), anyInt()))
                .thenReturn(List.of(Map.of("id", 1, "type", "ANIME", "title", Map.of("romaji", "Frieren"))));

        assertThat(adapter.search(MediaType.ANIME, "frieren", 5).getFirst().facets())
                .isEmpty();
    }

    @Test
    void passesThroughWhetherAniListHasAnotherPage() {
        when(client.browseMedia(any(), any(), any(), any(), any(), any(), eq(2), anyInt()))
                .thenReturn(new AniListClient.MediaPage(List.of(), true));

        assertThat(adapter.browse(MediaType.ANIME, "trending", 2, 24).hasMore()).isTrue();
    }
}
