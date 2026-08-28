package dev.nexus.modules.books;

import dev.nexus.core.adapter.DiscoverFilters;
import dev.nexus.core.adapter.FilterField;
import dev.nexus.core.adapter.FilterField.FilterOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * What a reader can narrow the books browse page down to, and the Open Library query it
 * becomes.
 *
 * <p>Subject is one value rather than several on purpose. Open Library takes a repeated
 * {@code subject} as something closer to "or" than "and" — two of them answer books carrying
 * only the second — so offering a multi-select would promise a narrowing it does not do.
 */
final class OpenLibraryFilters {

    /** Open Library's catalogue thins out below this, and the dropdown has to end somewhere. */
    private static final int EARLIEST_YEAR = 1900;

    /**
     * Subjects, as Open Library spells them. Its subject vocabulary is open — anyone
     * cataloguing a book can invent one — so this is a shortlist of the ones that are actually
     * populated rather than everything the index has ever seen.
     */
    private static final List<FilterOption> SUBJECTS = List.of(
            new FilterOption("fiction", "Fiction"),
            new FilterOption("fantasy", "Fantasy"),
            new FilterOption("science_fiction", "Science Fiction"),
            new FilterOption("mystery", "Mystery"),
            new FilterOption("thriller", "Thriller"),
            new FilterOption("horror", "Horror"),
            new FilterOption("romance", "Romance"),
            new FilterOption("historical_fiction", "Historical Fiction"),
            new FilterOption("young_adult", "Young Adult"),
            new FilterOption("comics", "Comics"),
            new FilterOption("biography", "Biography"),
            new FilterOption("history", "History"),
            new FilterOption("science", "Science"),
            new FilterOption("philosophy", "Philosophy"),
            new FilterOption("psychology", "Psychology"),
            new FilterOption("poetry", "Poetry"),
            new FilterOption("travel", "Travel"),
            new FilterOption("cooking", "Cooking"));

    /** MARC codes, which is what Open Library files a language under — not ISO's two letters. */
    private static final List<FilterOption> LANGUAGES = List.of(
            new FilterOption("eng", "English"),
            new FilterOption("ger", "German"),
            new FilterOption("fre", "French"),
            new FilterOption("spa", "Spanish"),
            new FilterOption("ita", "Italian"),
            new FilterOption("por", "Portuguese"),
            new FilterOption("dut", "Dutch"),
            new FilterOption("swe", "Swedish"),
            new FilterOption("rus", "Russian"),
            new FilterOption("jpn", "Japanese"),
            new FilterOption("chi", "Chinese"));

    private OpenLibraryFilters() {}

    static List<FilterField> fields(LocalDate today) {
        return List.of(
                FilterField.text("q", "Search"),
                FilterField.select("subject", "Subject", SUBJECTS),
                FilterField.select("year", "First Published", years(today)),
                FilterField.select("language", "Language", LANGUAGES));
    }

    /**
     * The chosen values as alternating parameter names and values.
     *
     * <p>Sorted by rating only when nothing was typed: with a term, Open Library's own
     * relevance is the better order, and overriding it answers well-rated books that have
     * little to do with what was asked for.
     */
    static List<String> query(DiscoverFilters filters) {
        List<String> params = new ArrayList<>();

        String term = filters.one("q");
        if (term != null && !term.isBlank()) {
            params.add("q");
            params.add(term.trim());
        } else {
            params.add("sort");
            params.add("rating");
        }

        addChosen(params, "subject", filters.one("subject"), SUBJECTS);
        addChosen(params, "language", filters.one("language"), LANGUAGES);

        Integer year = filters.number("year");
        if (year != null && year >= EARLIEST_YEAR) {
            params.add("first_publish_year");
            params.add(String.valueOf(year));
        }

        return List.copyOf(params);
    }

    /** Only values this adapter published reach the query; anything else was not offered. */
    private static void addChosen(List<String> params, String name, String chosen, List<FilterOption> offered) {
        if (chosen == null || offered.stream().noneMatch(option -> option.value().equals(chosen))) {
            return;
        }
        params.add(name);
        params.add(chosen);
    }

    private static List<FilterOption> years(LocalDate today) {
        return IntStream.rangeClosed(EARLIEST_YEAR, today.getYear())
                .map(year -> EARLIEST_YEAR + today.getYear() - year)
                .mapToObj(year -> new FilterOption(String.valueOf(year), String.valueOf(year)))
                .toList();
    }
}
