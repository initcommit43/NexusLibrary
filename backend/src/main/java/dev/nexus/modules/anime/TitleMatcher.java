package dev.nexus.modules.anime;

import java.util.List;
import java.util.Locale;

/**
 * Decides whether a MAL entry and an AniList record name the same work, for the entries
 * the hard {@code idMal} join could not settle.
 *
 * <p>A Java reimplementation of the matching rules in AL-MAL-Sync (the author's Python
 * AniList↔MAL tool), which is the reference spec here — the thresholds and guards below
 * were tuned there against real lists, not invented for this port. The shape it inherits:
 * titles are compared at four levels of increasing tolerance — exact, normalised, shared
 * words, edit distance — across English, native and romaji forms, and a title match alone
 * is never enough where the numbers argue against it. A one-episode special matches its
 * parent series' title perfectly, which is exactly why it must not match its entry.
 *
 * <p>What is deliberately not ported: the manual-mappings file and the third-party mapping
 * APIs (offline database, ARM, Hato, Jikan). Those exist to make two live lists converge
 * over years of syncing; a one-time import's escape hatch is the unmatched report.
 */
final class TitleMatcher {

    /** One work under two names — "Attack on Titan" and "Shingeki no Kyojin" — sits far
     * below any lexical threshold, so tolerance buys little and mismatch a lot. These sit
     * high because a near-miss between different works is worse than an honest unmatched. */
    private static final double SIMILARITY_THRESHOLD = 98.0;

    private static final double LEVENSHTEIN_THRESHOLD = 98.0;

    /** Same-title entries whose episode counts differ by more than this are not the same cut. */
    private static final double EPISODE_DIFFERENCE_LIMIT = 20.0;

    private TitleMatcher() {}

    /** The three forms a title travels under; any of them may be blank. */
    record Titles(String english, String nativeTitle, String romaji) {

        private List<String> forms() {
            return List.of(orEmpty(english), orEmpty(nativeTitle), orEmpty(romaji));
        }

        private static String orEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    /**
     * Same anime? Titles must agree at some level, and the episode counts must not
     * disagree loudly: where both are known, a difference beyond twenty percent means a
     * different cut of the work — a season against its whole, a special against its series.
     */
    static boolean sameAnime(Titles source, int sourceEpisodes, Titles candidate, int candidateEpisodes) {
        if (!titlesMatch(source, candidate)) {
            return false;
        }
        if (sourceEpisodes > 0 && candidateEpisodes > 0) {
            int max = Math.max(sourceEpisodes, candidateEpisodes);
            int min = Math.min(sourceEpisodes, candidateEpisodes);
            double percentDifference = (max - min) * 100.0 / max;
            if (percentDifference > EPISODE_DIFFERENCE_LIMIT) {
                return false;
            }
        }
        return true;
    }

    /**
     * The special-vs-series guard: a special or OVA (an episode or none known) whose title
     * fuzzed onto a full series is the classic wrong match, and only a literally identical
     * title is allowed to override the suspicion.
     */
    static boolean specialMatchedToSeries(Titles source, int sourceEpisodes, Titles candidate, int candidateEpisodes) {
        return (sourceEpisodes == 0 || sourceEpisodes == 1)
                && candidateEpisodes > 4
                && !identicalTitle(source, candidate);
    }

    /**
     * Same manga? Titles at some level — or, failing that, identical chapter and volume
     * counts. Some manga are split across entries on one service and merged on the other,
     * so the names disagree while the numbers, which nobody restyles, still agree.
     */
    static boolean sameManga(
            Titles source,
            int sourceChapters,
            int sourceVolumes,
            Titles candidate,
            int candidateChapters,
            int candidateVolumes) {

        if (titlesMatch(source, candidate)) {
            return true;
        }
        return (sourceChapters > 0 || sourceVolumes > 0)
                && sourceChapters == candidateChapters
                && sourceVolumes == candidateVolumes;
    }

    /** Exact, normalised, shared-words, then edit distance — each tried across all three forms. */
    static boolean titlesMatch(Titles a, Titles b) {
        return anyForm(a, b, TitleMatcher::exactMatch)
                || anyForm(a, b, TitleMatcher::normalizedMatch)
                || anyForm(a, b, (x, y) -> bothPresent(x, y) && wordOverlap(x, y) >= SIMILARITY_THRESHOLD)
                || anyForm(a, b, (x, y) -> bothPresent(x, y) && levenshteinSimilarity(x, y) >= LEVENSHTEIN_THRESHOLD);
    }

    /** Character-for-character equality on any form; the only thing that overrides a guard. */
    static boolean identicalTitle(Titles a, Titles b) {
        List<String> formsA = a.forms();
        List<String> formsB = b.forms();
        for (int i = 0; i < formsA.size(); i++) {
            if (!formsA.get(i).isEmpty() && formsA.get(i).equals(formsB.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lowercase, parenthesised asides gone, punctuation flattened, whitespace collapsed —
     * so "Attack on Titan (TV)!" and "attack on titan" are the same string.
     */
    static String normalize(String title) {
        String normalized = title.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("\\(.*\\)", "");
        normalized = normalized
                .replace(":", "")
                .replace("!", "")
                .replace("?", "")
                .replace(".", "")
                .replace("-", " ")
                .replace("_", " ");
        return normalized.replaceAll("\\s+", " ").strip();
    }

    private interface FormMatch {
        boolean test(String a, String b);
    }

    /** Forms are compared like with like: English against English, romaji against romaji. */
    private static boolean anyForm(Titles a, Titles b, FormMatch match) {
        List<String> formsA = a.forms();
        List<String> formsB = b.forms();
        for (int i = 0; i < formsA.size(); i++) {
            if (match.test(formsA.get(i), formsB.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean bothPresent(String a, String b) {
        return !a.isEmpty() && !b.isEmpty();
    }

    private static boolean exactMatch(String a, String b) {
        return bothPresent(a, b) && a.equalsIgnoreCase(b);
    }

    private static boolean normalizedMatch(String a, String b) {
        return bothPresent(a, b) && normalize(a).equals(normalize(b));
    }

    /** Shared words over total words, as a percentage: word order and repetition forgiven. */
    private static double wordOverlap(String a, String b) {
        String normalizedA = normalize(a);
        String normalizedB = normalize(b);
        if (normalizedA.equals(normalizedB)) {
            return 100.0;
        }

        List<String> wordsA = List.of(normalizedA.split(" "));
        List<String> wordsB = List.of(normalizedB.split(" "));
        if (wordsA.isEmpty() || wordsB.isEmpty()) {
            return 0.0;
        }

        long common = wordsA.stream().filter(wordsB::contains).count();
        return common * 2.0 / (wordsA.size() + wordsB.size()) * 100.0;
    }

    private static double levenshteinSimilarity(String a, String b) {
        String normalizedA = normalize(a);
        String normalizedB = normalize(b);
        if (normalizedA.equals(normalizedB)) {
            return 100.0;
        }
        int maxLength = Math.max(normalizedA.length(), normalizedB.length());
        if (maxLength == 0) {
            return 100.0;
        }
        double distance = levenshtein(normalizedA, normalizedB);
        return Math.max((1.0 - distance / maxLength) * 100.0, 0.0);
    }

    private static int levenshtein(String a, String b) {
        if (a.isEmpty()) {
            return b.length();
        }
        if (b.isEmpty()) {
            return a.length();
        }

        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost);
            }
            previous = current;
        }
        return previous[b.length()];
    }
}
