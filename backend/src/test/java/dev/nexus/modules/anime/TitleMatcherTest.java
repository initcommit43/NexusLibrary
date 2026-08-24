package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.modules.anime.TitleMatcher.Titles;
import org.junit.jupiter.api.Test;

/**
 * Pins the matching rules to the reference implementation's behaviour: these cases are
 * ported from AL-MAL-Sync's test suite, so the Java reimplementation and the Python
 * original agree on the calls that were tuned against real lists.
 */
class TitleMatcherTest {

    private static Titles english(String title) {
        return new Titles(title, "", "");
    }

    @Test
    void normalizeStripsBracketsPunctuationAndCase() {
        assertThat(TitleMatcher.normalize("Attack on Titan (TV)!")).isEqualTo("attack on titan");
    }

    @Test
    void titlesMatchAfterNormalization() {
        assertThat(TitleMatcher.titlesMatch(english("Attack on Titan!"), english("attack on titan")))
                .isTrue();
    }

    @Test
    void titlesMatchOnSharedWords() {
        assertThat(TitleMatcher.titlesMatch(
                        english("Fullmetal Alchemist Brotherhood"),
                        english("Fullmetal Alchemist: Brotherhood")))
                .isTrue();
    }

    @Test
    void unrelatedTitlesDoNotMatch() {
        assertThat(TitleMatcher.titlesMatch(english("Cowboy Bebop"), english("Naruto"))).isFalse();
    }

    /** Two blanks are not the same title; they are the absence of one. */
    @Test
    void emptyTitlesNeverMatch() {
        assertThat(TitleMatcher.titlesMatch(english(""), english(""))).isFalse();
        assertThat(TitleMatcher.identicalTitle(english(""), english(""))).isFalse();
    }

    /** Forms are compared like with like: an English title must not match a romaji one. */
    @Test
    void differentFormsAreNotComparedAcross() {
        Titles englishOnly = new Titles("Attack on Titan", "", "");
        Titles romajiOnly = new Titles("", "", "Attack on Titan");
        assertThat(TitleMatcher.titlesMatch(englishOnly, romajiOnly)).isFalse();
    }

    @Test
    void sameAnimeAcceptsMatchingTitlesAndCloseEpisodeCounts() {
        assertThat(TitleMatcher.sameAnime(english("Show"), 12, english("Show"), 13)).isTrue();
    }

    /** The same title on a 12-episode entry and a 1-episode entry is a different cut. */
    @Test
    void sameAnimeRejectsWildlyDifferentEpisodeCounts() {
        assertThat(TitleMatcher.sameAnime(english("Show"), 12, english("Show"), 1)).isFalse();
    }

    @Test
    void anUnknownEpisodeCountDoesNotBlockATitleMatch() {
        assertThat(TitleMatcher.sameAnime(english("Show"), 0, english("Show"), 24)).isTrue();
    }

    @Test
    void aSpecialMatchedOntoASeriesIsSuspicious() {
        assertThat(TitleMatcher.specialMatchedToSeries(english("Show A"), 1, english("Show B"), 24))
                .isTrue();
    }

    @Test
    void identicalTitlesOverrideTheSpecialGuard() {
        assertThat(TitleMatcher.specialMatchedToSeries(english("Show"), 1, english("Show"), 24))
                .isFalse();
    }

    /**
     * The split-entry case the manga fallback exists for: parts on one service merged on
     * the other, so the names disagree while the counts still agree exactly.
     */
    @Test
    void mangaFallsBackToChapterAndVolumeCountsWhenTitlesDiffer() {
        assertThat(TitleMatcher.sameManga(english("Part Two"), 120, 12, english("The Whole Thing"), 120, 12))
                .isTrue();
    }

    @Test
    void mangaWithDifferentTitlesAndCountsDoesNotMatch() {
        assertThat(TitleMatcher.sameManga(english("Part Two"), 120, 12, english("The Whole Thing"), 300, 30))
                .isFalse();
    }

    /** Zero counts prove nothing: the fallback needs a real number to lean on. */
    @Test
    void mangaWithNoCountsCannotUseTheFallback() {
        assertThat(TitleMatcher.sameManga(english("Part Two"), 0, 0, english("The Whole Thing"), 0, 0))
                .isFalse();
    }
}
