package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The quarter arithmetic behind the seasonal shelves. */
class AniListSeasonTest {

    @Test
    void mapsEachQuarterOntoItsSeason() {
        assertThat(AniListSeason.of(LocalDate.of(2026, 1, 1))).isEqualTo(AniListSeason.WINTER);
        assertThat(AniListSeason.of(LocalDate.of(2026, 3, 31))).isEqualTo(AniListSeason.WINTER);
        assertThat(AniListSeason.of(LocalDate.of(2026, 4, 1))).isEqualTo(AniListSeason.SPRING);
        assertThat(AniListSeason.of(LocalDate.of(2026, 7, 15))).isEqualTo(AniListSeason.SUMMER);
        assertThat(AniListSeason.of(LocalDate.of(2026, 10, 1))).isEqualTo(AniListSeason.FALL);
        assertThat(AniListSeason.of(LocalDate.of(2026, 12, 31))).isEqualTo(AniListSeason.FALL);
    }

    @Test
    void wrapsFromAutumnRoundToWinter() {
        assertThat(AniListSeason.FALL.next()).isEqualTo(AniListSeason.WINTER);
        assertThat(AniListSeason.WINTER.next()).isEqualTo(AniListSeason.SPRING);
    }

    /** The one case the year moves: winter follows autumn into the next year. */
    @Test
    void rollsTheYearForwardOnlyAfterAutumn() {
        assertThat(AniListSeason.FALL.nextYear(2026)).isEqualTo(2027);
        assertThat(AniListSeason.SUMMER.nextYear(2026)).isEqualTo(2026);
        assertThat(AniListSeason.WINTER.nextYear(2026)).isEqualTo(2026);
        assertThat(AniListSeason.SPRING.nextYear(2026)).isEqualTo(2026);
    }
}
