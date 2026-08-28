package dev.nexus.modules.games;

import dev.nexus.core.adapter.DiscoverFilters;
import dev.nexus.core.adapter.FilterField;
import dev.nexus.core.adapter.FilterField.FilterOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * What a reader can narrow the games browse page down to, and the IGDB query behind it.
 *
 * <p>The platform list is a shortlist rather than IGDB's own: it knows 220 platforms and most
 * of them — the Advanced Pico Beena, the HyperScan — are not something anyone is choosing
 * between. The ids are stable and the names still come from IGDB.
 */
final class IgdbFilters {

    /** The platforms worth offering. Ids are IGDB's own and do not move. */
    static final List<Integer> PLATFORM_IDS = List.of(
            6, // PC
            3, // Linux
            14, // Mac
            34, // Android
            39, // iOS
            7, 8, 9, 48, 167, // PlayStation 1-5
            38, 46, // PSP, Vita
            11, 12, 49, 169, // Xbox, 360, One, Series
            4, 5, 41, 130, 508, // N64, Wii, Wii U, Switch, Switch 2
            20, 37); // DS, 3DS

    /** IGDB's oldest games are early seventies; below this a year is an empty shelf. */
    private static final int EARLIEST_YEAR = 1970;

    /**
     * Release states, which are only partly IGDB's {@code status}.
     *
     * <p>That field is all but unpopulated — 29 games in three hundred thousand are marked
     * released, against 284,000 with nothing set at all — so reading "Released" off it would
     * answer almost nothing. Released and Upcoming come from the release date, which every
     * game has; the rest come from status, which is only used where it says something the
     * date cannot.
     */
    private static final List<FilterOption> STATUSES = List.of(
            new FilterOption("RELEASED", "Released"),
            new FilterOption("UPCOMING", "Upcoming"),
            new FilterOption("EARLY_ACCESS", "Early access"),
            new FilterOption("ALPHA", "Alpha"),
            new FilterOption("BETA", "Beta"),
            new FilterOption("CANCELLED", "Cancelled"),
            new FilterOption("DELISTED", "Delisted"));

    private IgdbFilters() {}

    static List<FilterField> fields(List<FilterOption> genres, List<FilterOption> platforms, LocalDate today) {
        List<FilterField> fields = new ArrayList<>();

        fields.add(FilterField.text("q", "Search"));
        fields.add(FilterField.select("status", "Status", STATUSES));
        if (!genres.isEmpty()) {
            fields.add(FilterField.multi("genres", "Genres", genres));
        }
        if (!platforms.isEmpty()) {
            fields.add(FilterField.multi("platform", "Platform", platforms));
        }
        fields.add(FilterField.select("year", "Year", years(today)));

        return List.copyOf(fields);
    }

    /**
     * The chosen values as one APIcalypse condition, or empty where nothing was chosen.
     *
     * <p>Genres are chained one condition each, which is how IGDB says "and": a single
     * {@code genres = (a,b)} matches a game carrying either, so picking two would widen the
     * answer rather than narrow it. Platforms are deliberately the other way — a game on the
     * PS5 or the Series X is playable by someone who owns one of them.
     */
    static String where(DiscoverFilters filters, long now) {
        List<String> conditions = new ArrayList<>();

        for (String genre : filters.all("genres")) {
            if (isId(genre)) {
                conditions.add("genres = (" + genre + ")");
            }
        }

        List<String> platforms = filters.all("platform").stream()
                .filter(IgdbFilters::isId)
                .toList();
        if (!platforms.isEmpty()) {
            conditions.add("platforms = (" + String.join(",", platforms) + ")");
        }

        Integer year = filters.number("year");
        if (year != null) {
            conditions.add("first_release_date >= " + startOf(year));
            conditions.add("first_release_date < " + startOf(year + 1));
        }

        String status = statusCondition(filters.one("status"), now);
        if (status != null) {
            conditions.add(status);
        }

        return String.join(" & ", conditions);
    }

    private static String statusCondition(String status, long now) {
        if (status == null || status.isBlank()) {
            return null;
        }

        return switch (status) {
            case "RELEASED" -> "first_release_date < " + now;
            case "UPCOMING" -> "first_release_date > " + now;
            case "EARLY_ACCESS" -> "status = 4";
            case "ALPHA" -> "status = 2";
            case "BETA" -> "status = 3";
            case "CANCELLED" -> "status = 6";
            case "DELISTED" -> "status = 8";
            // Anything else came from outside the list this adapter published.
            default -> null;
        };
    }

    private static long startOf(int year) {
        return LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    }

    /** Guards the query text: these values are ids, and only ids may be written into it. */
    private static boolean isId(String value) {
        return value != null && !value.isBlank() && value.chars().allMatch(Character::isDigit);
    }

    /** Newest first, and one year ahead of today, since a game is announced before it ships. */
    private static List<FilterOption> years(LocalDate today) {
        return IntStream.rangeClosed(EARLIEST_YEAR, today.getYear() + 1)
                .map(year -> EARLIEST_YEAR + today.getYear() + 1 - year)
                .mapToObj(year -> new FilterOption(String.valueOf(year), String.valueOf(year)))
                .toList();
    }
}
