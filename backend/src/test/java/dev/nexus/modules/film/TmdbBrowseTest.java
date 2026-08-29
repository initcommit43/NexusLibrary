package dev.nexus.modules.film;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.BrowseShelf;
import dev.nexus.core.domain.MediaType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The TMDB lists behind the film and TV browse pages. */
class TmdbBrowseTest {

    private TmdbClient client;
    private TmdbMetadataAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(TmdbClient.class);
        adapter = new TmdbMetadataAdapter(
                client, new TmdbProperties(
                        "https://tmdb.test/3", "https://img.test/", "w500", "w1280", "w185", "token", 100));
        when(client.browse(any(), anyString(), anyInt())).thenReturn(Map.of());
        when(client.trending(any(), anyString(), anyInt())).thenReturn(Map.of());
        when(client.resultsOf(any())).thenReturn(List.of());
    }

    /**
     * The same rows in different words. TMDB models a film as being "in cinemas" and a show as
     * being "on the air", and names its endpoints accordingly.
     */
    @Test
    void offersTheSameShelvesToBothKindsUnderTheirOwnNames() {
        assertThat(adapter.browseShelves(MediaType.MOVIE))
                .extracting(BrowseShelf::id)
                .containsExactly("trending", "popular", "in-cinemas", "coming-soon", "top");

        assertThat(adapter.browseShelves(MediaType.SHOW))
                .extracting(BrowseShelf::id)
                .containsExactly("trending", "popular", "on-the-air", "airing-today", "top");
    }

    @Test
    void asksTheMovieEndpointsForFilmsAndTheTvOnesForShows() {
        adapter.browse(MediaType.MOVIE, "in-cinemas", 1, 24);
        verify(client).browse(TmdbKind.MOVIE, "now_playing", 1);

        adapter.browse(MediaType.SHOW, "on-the-air", 1, 24);
        verify(client).browse(TmdbKind.SHOW, "on_the_air", 1);
    }

    /** Trending is its own endpoint, not a list under the kind. */
    @Test
    void readsTrendingFromItsOwnEndpoint() {
        adapter.browse(MediaType.MOVIE, "trending", 1, 24);

        verify(client).trending(TmdbKind.MOVIE, "week", 1);
        verify(client, never()).browse(any(), anyString(), anyInt());
    }

    /** A shelf one kind has and the other does not must not be queryable through it. */
    @Test
    void refusesAFilmShelfAskedForAsAShow() {
        assertThat(adapter.browse(MediaType.SHOW, "in-cinemas", 1, 24).items()).isEmpty();

        verify(client, never()).browse(any(), anyString(), anyInt());
        verify(client, never()).trending(any(), anyString(), anyInt());
    }

    @Test
    void asksForNothingWhenTheShelfIsUnknown() {
        assertThat(adapter.browse(MediaType.MOVIE, "not-a-shelf", 1, 24).items()).isEmpty();

        verify(client, never()).browse(any(), anyString(), anyInt());
    }

    /** Films and shows are numbered separately, so every id carries which kind it is. */
    @Test
    void prefixesEveryIdWithItsKind() {
        when(client.resultsOf(any())).thenReturn(List.of(Map.of("id", 550, "title", "Fight Club")));

        assertThat(adapter.browse(MediaType.MOVIE, "popular", 1, 24).items().getFirst().externalId())
                .isEqualTo("movie:550");
    }

    /** TMDB rates on 0-10 and core stores 0-100, so a shelf's score scales like everything else. */
    @Test
    void scalesTheScoreOntoTheHundredPointScale() {
        when(client.resultsOf(any()))
                .thenReturn(List.of(Map.of("id", 550, "title", "Fight Club", "vote_average", 8.4)));

        assertThat(adapter.browse(MediaType.MOVIE, "top", 1, 24).items().getFirst().facets())
                .containsEntry("score", 84L);
    }

    /** A zero score means unrated rather than terrible, and should not read as 0%. */
    @Test
    void leavesAnUnratedTitleWithoutAScore() {
        when(client.resultsOf(any()))
                .thenReturn(List.of(Map.of("id", 1, "title", "Unreleased", "vote_average", 0.0)));

        assertThat(adapter.browse(MediaType.MOVIE, "coming-soon", 1, 24).items().getFirst().facets())
                .doesNotContainKey("score");
    }

    @Test
    void passesThroughWhetherTmdbHasAnotherPage() {
        when(client.hasMorePages(any(), anyInt())).thenReturn(true);

        assertThat(adapter.browse(MediaType.MOVIE, "popular", 1, 24).hasMore()).isTrue();
    }

    /** Books and games are not TMDB's, and asking for them is a bug rather than an empty shelf. */
    @Test
    void refusesAMediaTypeItDoesNotServe() {
        assertThatThrownBy(() -> adapter.browseShelves(MediaType.BOOK))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
