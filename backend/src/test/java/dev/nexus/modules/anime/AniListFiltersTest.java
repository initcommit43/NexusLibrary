package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.BrowseResults;
import dev.nexus.core.adapter.DiscoverFilters;
import dev.nexus.core.adapter.FilterField;
import dev.nexus.core.domain.MediaType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The filter bar anime and manga offer, and the query a set of values turns into. */
class AniListFiltersTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);

    private AniListClient client;
    private AniListMetadataAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(AniListClient.class);
        adapter = new AniListMetadataAdapter(client);
    }

    private static List<String> idsOf(List<FilterField> fields) {
        return fields.stream().map(FilterField::id).toList();
    }

    private static FilterField byId(List<FilterField> fields, String id) {
        return fields.stream().filter(field -> field.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void animeIsFilteredBySeasonAndMangaIsNot() {
        List<FilterField> anime = AniListFilters.forMediaType(MediaType.ANIME, List.of("Action"), List.of("Isekai"), TODAY);
        List<FilterField> manga = AniListFilters.forMediaType(MediaType.MANGA, List.of("Action"), List.of("Isekai"), TODAY);

        assertThat(idsOf(anime)).containsExactly("q", "genres", "year", "season", "format", "status");
        assertThat(idsOf(manga)).containsExactly("q", "genres", "year", "format", "status");
    }

    @Test
    void aStatusIsWordedForTheMediumItDescribes() {
        assertThat(byId(AniListFilters.forMediaType(MediaType.ANIME, List.of(), List.of(), TODAY), "status")
                        .label())
                .isEqualTo("Airing Status");
        assertThat(byId(AniListFilters.forMediaType(MediaType.MANGA, List.of(), List.of(), TODAY), "status")
                        .label())
                .isEqualTo("Publishing Status");
    }

    @Test
    void formatsDifferByMediaType() {
        List<String> anime = byId(AniListFilters.forMediaType(MediaType.ANIME, List.of(), List.of(), TODAY), "format")
                .options()
                .stream()
                .map(FilterField.FilterOption::value)
                .toList();
        List<String> manga = byId(AniListFilters.forMediaType(MediaType.MANGA, List.of(), List.of(), TODAY), "format")
                .options()
                .stream()
                .map(FilterField.FilterOption::value)
                .toList();

        assertThat(anime).contains("TV", "OVA").doesNotContain("NOVEL");
        assertThat(manga).containsExactly("MANGA", "NOVEL", "ONE_SHOT");
    }

    /** Anime is announced a season ahead, so next year has to be choosable before it arrives. */
    @Test
    void yearsRunFromNextYearBackwards() {
        List<String> years = byId(AniListFilters.forMediaType(MediaType.ANIME, List.of(), List.of(), TODAY), "year")
                .options()
                .stream()
                .map(FilterField.FilterOption::value)
                .toList();

        assertThat(years).startsWith("2027", "2026", "2025");
        assertThat(years).endsWith("1940");
    }

    /** The word is what a reader picks; the mark on it is how the two lists stay apart. */
    @Test
    void genresAndTagsShareOneBoxAndSayWhichTheyAre() {
        FilterField box = byId(
                AniListFilters.forMediaType(
                        MediaType.ANIME, List.of("Slice of Life"), List.of("Iyashikei"), TODAY),
                "genres");

        assertThat(box.kind()).isEqualTo(FilterField.Kind.MULTI);
        assertThat(box.label()).isEqualTo("Genres & Tags");
        assertThat(box.options())
                .extracting(FilterField.FilterOption::value, FilterField.FilterOption::label)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("genre:Slice of Life", "Slice of Life"),
                        org.assertj.core.groups.Tuple.tuple("tag:Iyashikei", "Iyashikei"));
    }

    /** Hundreds of them, and they move: a failure costs the tags, not the whole box. */
    @Test
    void aFailedTagCallLeavesTheGenresStanding() {
        when(client.genres()).thenReturn(List.of("Action"));
        when(client.tags()).thenThrow(new AniListUnavailableException("down"));

        assertThat(byId(adapter.discoverFilters(MediaType.ANIME), "genres").options())
                .extracting(FilterField.FilterOption::value)
                .containsExactly("genre:Action");
    }

    @Test
    void theTagListIsFetchedOnceAndThenReused() {
        when(client.tags()).thenReturn(List.of("Isekai"));

        adapter.discoverFilters(MediaType.ANIME);
        adapter.discoverFilters(MediaType.MANGA);

        verify(client, times(1)).tags();
    }

    @Test
    void theGenreListIsFetchedOnceAndThenReused() {
        when(client.genres()).thenReturn(List.of("Action", "Comedy"));

        adapter.discoverFilters(MediaType.ANIME);
        adapter.discoverFilters(MediaType.MANGA);

        verify(client, times(1)).genres();
    }

    /** An empty control makes the bar look broken, so a failure falls back rather than spreads. */
    @Test
    void aFailedGenreCallFallsBackToTheKnownList() {
        when(client.genres()).thenThrow(new AniListUnavailableException("down"));

        FilterField genres = byId(adapter.discoverFilters(MediaType.ANIME), "genres");

        assertThat(genres.options()).hasSameSizeAs(AniListFilters.FALLBACK_GENRES);
    }

    @Test
    void filterValuesReachTheClientUnchanged() {
        when(client.discoverMedia(
                        any(MediaType.class),
                        anyString(),
                        any(),
                        any(),
                        anyInt(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyInt(),
                        anyInt()))
                .thenReturn(new AniListClient.MediaPage(List.of(media()), true));

        BrowseResults results = adapter.discover(
                MediaType.ANIME,
                new DiscoverFilters(Map.of(
                        "q", List.of("frieren"),
                        "genres", List.of("genre:Fantasy", "tag:Isekai"),
                        "year", List.of("2023"),
                        "season", List.of("FALL"),
                        "format", List.of("TV"),
                        "status", List.of("FINISHED"))),
                2,
                40);

        verify(client)
                .discoverMedia(
                        eq(MediaType.ANIME),
                        eq("frieren"),
                        eq(List.of("Fantasy")),
                        eq(List.of("Isekai")),
                        eq(2023),
                        eq("FALL"),
                        eq("TV"),
                        eq("FINISHED"),
                        eq(2),
                        eq(40));

        assertThat(results.items()).singleElement().satisfies(item -> assertThat(item.externalId())
                .isEqualTo("21"));
        assertThat(results.hasMore()).isTrue();
    }

    /** A link written before the tags joined the box still means the genre it named. */
    @Test
    void anUnmarkedValueIsReadAsAGenre() {
        when(client.discoverMedia(
                        any(MediaType.class), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new AniListClient.MediaPage(List.of(media()), false));

        adapter.discover(MediaType.ANIME, new DiscoverFilters(Map.of("genres", List.of("Fantasy"))), 1, 20);

        verify(client)
                .discoverMedia(
                        eq(MediaType.ANIME),
                        any(),
                        eq(List.of("Fantasy")),
                        eq(List.of()),
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(1),
                        eq(20));
    }

    private static Map<String, Object> media() {
        return Map.of(
                "id", 21,
                "type", "ANIME",
                "format", "TV",
                "title", Map.of("romaji", "One Piece"));
    }
}
