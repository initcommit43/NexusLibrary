package dev.nexus.modules.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.DiscoverFilters;
import dev.nexus.core.adapter.FilterField;
import dev.nexus.core.domain.MediaType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The games filter bar, and the APIcalypse a set of values turns into. */
class IgdbFiltersTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 29);

    /** 2026-08-29T00:00Z, so a status condition has something fixed to be written against. */
    private static final long NOW = 1787011200L;

    private IgdbClient client;
    private IgdbMetadataAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(IgdbClient.class);
        adapter = new IgdbMetadataAdapter(client);
    }

    private static DiscoverFilters of(Map<String, List<String>> values) {
        return new DiscoverFilters(values);
    }

    private static FilterField byId(List<FilterField> fields, String id) {
        return fields.stream().filter(field -> field.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void theBarIsSearchStatusGenresPlatformAndYear() {
        List<FilterField> fields = IgdbFilters.fields(
                List.of(new FilterField.FilterOption("12", "Role-playing (RPG)")),
                List.of(new FilterField.FilterOption("6", "PC (Microsoft Windows)")),
                TODAY);

        assertThat(fields.stream().map(FilterField::id))
                .containsExactly("q", "status", "genres", "platform", "year");
        assertThat(byId(fields, "genres").kind()).isEqualTo(FilterField.Kind.MULTI);
        assertThat(byId(fields, "platform").kind()).isEqualTo(FilterField.Kind.MULTI);
    }

    /** A lookup that could not be fetched leaves its control out rather than showing it empty. */
    @Test
    void anEmptyLookupDropsItsControl() {
        List<FilterField> fields = IgdbFilters.fields(List.of(), List.of(), TODAY);

        assertThat(fields.stream().map(FilterField::id)).containsExactly("q", "status", "year");
    }

    /**
     * The reason genres are chained rather than listed: IGDB reads one genres = (a,b) as
     * either, so listing two would widen the answer where the reader asked to narrow it.
     */
    @Test
    void severalGenresAreChainedAndSeveralPlatformsAreNot() {
        String where = IgdbFilters.where(of(Map.of("genres", List.of("12", "31"))), NOW);
        assertThat(where).contains("genres = (12)").contains("genres = (31)");

        assertThat(IgdbFilters.where(of(Map.of("platform", List.of("48", "167"))), NOW))
                .isEqualTo("platforms = (48,167)");
    }

    @Test
    void aYearBecomesTheRangeAcrossIt() {
        String where = IgdbFilters.where(of(Map.of("year", List.of("2020"))), NOW);

        // 2020-01-01 and 2021-01-01 as epoch seconds.
        assertThat(where).isEqualTo("first_release_date >= 1577836800 & first_release_date < 1609459200");
    }

    /**
     * Released is a date and not a status, because IGDB's status field is all but unpopulated
     * — reading it off there answers 29 games out of three hundred thousand.
     */
    @Test
    void releasedAndUpcomingComeFromTheDateAndTheRestFromStatus() {
        assertThat(IgdbFilters.where(of(Map.of("status", List.of("RELEASED"))), NOW))
                .isEqualTo("first_release_date < " + NOW);
        assertThat(IgdbFilters.where(of(Map.of("status", List.of("UPCOMING"))), NOW))
                .isEqualTo("first_release_date > " + NOW);
        assertThat(IgdbFilters.where(of(Map.of("status", List.of("EARLY_ACCESS"))), NOW))
                .isEqualTo("status = 4");
    }

    /** Ids are written straight into the query text, so nothing that is not one may be. */
    @Test
    void anythingButAnIdIsLeftOutOfTheQuery() {
        assertThat(IgdbFilters.where(of(Map.of("genres", List.of("12; drop"))), NOW))
                .isEmpty();
        assertThat(IgdbFilters.where(of(Map.of("platform", List.of("6", "not-an-id"))), NOW))
                .isEqualTo("platforms = (6)");
        assertThat(IgdbFilters.where(of(Map.of("status", List.of("MADE_UP"))), NOW))
                .isEmpty();
    }

    @Test
    void nothingChosenNarrowsNothing() {
        assertThat(IgdbFilters.where(DiscoverFilters.none(), NOW)).isEmpty();
    }

    @Test
    void yearsRunFromNextYearBackwards() {
        List<String> years = byId(IgdbFilters.fields(List.of(), List.of(), TODAY), "year").options().stream()
                .map(FilterField.FilterOption::value)
                .toList();

        assertThat(years).startsWith("2027", "2026");
        assertThat(years).endsWith("1970");
    }

    @Test
    void lookupsAreFetchedOnceAndThenReused() {
        when(client.genres()).thenReturn(List.of(Map.of("id", 12, "name", "Role-playing (RPG)")));
        when(client.platforms(IgdbFilters.PLATFORM_IDS))
                .thenReturn(List.of(Map.of("id", 6, "name", "PC (Microsoft Windows)")));

        adapter.discoverFilters(MediaType.GAME);
        adapter.discoverFilters(MediaType.GAME);

        verify(client, times(1)).genres();
        verify(client, times(1)).platforms(IgdbFilters.PLATFORM_IDS);
    }

    @Test
    void aFailedLookupLeavesTheRestOfTheBarStanding() {
        when(client.genres()).thenThrow(new IgdbUnavailableException("down", new RuntimeException()));
        when(client.platforms(IgdbFilters.PLATFORM_IDS)).thenThrow(new IgdbUnavailableException("down", new RuntimeException()));

        assertThat(adapter.discoverFilters(MediaType.GAME).stream().map(FilterField::id))
                .containsExactly("q", "status", "year");
    }
}
