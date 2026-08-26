package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.nexus.core.importing.CsvFormatException;
import dev.nexus.core.importing.CsvTable;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * The parser exists because every service spells its export differently. These pin the
 * differences that actually turn up in real files — a title with a comma in it, a European
 * semicolon, a spreadsheet's byte order mark — each of which silently produces a wrong or
 * empty import rather than an error.
 */
class CsvTableTest {

    @Test
    void readsColumnsByNameWhateverTheSpelling() {
        CsvTable table = CsvTable.parse("MAL ID,Series Title\n5114,Fullmetal Alchemist\n");

        CsvTable.Row row = table.rows().getFirst();

        assertThat(row.value("mal_id")).isEqualTo("5114");
        assertThat(row.value("malid")).isEqualTo("5114");
        assertThat(row.value("series_title")).isEqualTo("Fullmetal Alchemist");
    }

    /** The first name that holds anything wins, so one adapter can serve several exporters. */
    @Test
    void takesTheFirstColumnThatHasAValue() {
        CsvTable table = CsvTable.parse("tmdb,imdb\n,tt0137523\n");

        assertThat(table.rows().getFirst().value("tmdb", "imdb")).isEqualTo("tt0137523");
    }

    /** A comma inside a quoted title is part of the title, not a new column. */
    @Test
    void keepsDelimitersThatAreInsideQuotes() {
        CsvTable table = CsvTable.parse("title,year\n\"Dr. Strangelove, or: How I Learned\",1964\n");

        CsvTable.Row row = table.rows().getFirst();

        assertThat(row.value("title")).isEqualTo("Dr. Strangelove, or: How I Learned");
        assertThat(row.value("year")).isEqualTo("1964");
    }

    @Test
    void readsADoubledQuoteAsOneQuote() {
        CsvTable table = CsvTable.parse("title,year\n\"The \"\"Burbs\",1989\n");

        assertThat(table.rows().getFirst().value("title")).isEqualTo("The \"Burbs");
    }

    /** A locale that writes lists with semicolons exports its CSV that way too. */
    @Test
    void detectsASemicolonSeparatedExport() {
        CsvTable table = CsvTable.parse("title;year;rating\nInception;2010;9\n");

        assertThat(table.rows().getFirst().value("title")).isEqualTo("Inception");
        assertThat(table.rows().getFirst().number("rating")).isEqualTo(9);
    }

    /** Decided on the header alone: a title full of commas must not outvote the separator. */
    @Test
    void doesNotLetRowContentChooseTheDelimiter() {
        CsvTable table = CsvTable.parse("title;year\n\"Lock, Stock, and Two Smoking Barrels\";1998\n");

        assertThat(table.rows().getFirst().value("title")).isEqualTo("Lock, Stock, and Two Smoking Barrels");
    }

    /** A spreadsheet writes a byte order mark, which would become part of the first header. */
    @Test
    void ignoresAByteOrderMark() {
        CsvTable table = CsvTable.parse("﻿title,year\nAkira,1988\n");

        assertThat(table.rows().getFirst().value("title")).isEqualTo("Akira");
    }

    @Test
    void handlesWindowsLineEndings() {
        CsvTable table = CsvTable.parse("title,year\r\nAkira,1988\r\n");

        assertThat(table.rows()).hasSize(1);
        assertThat(table.rows().getFirst().value("year")).isEqualTo("1988");
    }

    @Test
    void skipsBlankRows() {
        CsvTable table = CsvTable.parse("title,year\nAkira,1988\n\n,\nRan,1985\n");

        assertThat(table.rows()).hasSize(2);
    }

    /** A rating column written by a spreadsheet arrives as 8.0, which is still an 8. */
    @Test
    void readsAWholeNumberOutOfADecimal() {
        CsvTable table = CsvTable.parse("score,other\n8.0,7,5\n");

        assertThat(table.rows().getFirst().number("score")).isEqualTo(8);
    }

    @Test
    void readsBothPlainDatesAndTimestamps() {
        CsvTable table = CsvTable.parse("started,finished\n2019-04-26,2019-05-02T18:30:00Z\n");

        CsvTable.Row row = table.rows().getFirst();

        assertThat(row.date("started")).isEqualTo(LocalDate.of(2019, 4, 26));
        assertThat(row.date("finished")).isEqualTo(LocalDate.of(2019, 5, 2));
    }

    /** Goodreads separates a date with slashes where every other export here uses dashes. */
    @Test
    void readsSlashSeparatedDates() {
        CsvTable table = CsvTable.parse("date read,date added\n2021/03/14,2019/12/01\n");

        CsvTable.Row row = table.rows().getFirst();

        assertThat(row.date("date read")).isEqualTo(LocalDate.of(2021, 3, 14));
        assertThat(row.date("date added")).isEqualTo(LocalDate.of(2019, 12, 1));
    }

    /** MyAnimeList writes an unset date as zeroes rather than leaving the column empty. */
    @Test
    void treatsAZeroDateAsNoDate() {
        CsvTable table = CsvTable.parse("my_start_date,x\n0000-00-00,1\n");

        assertThat(table.rows().getFirst().date("my_start_date")).isNull();
    }

    @Test
    void aMissingValueIsNullRatherThanEmpty() {
        CsvTable table = CsvTable.parse("title,year\nAkira,\n");

        assertThat(table.rows().getFirst().value("year")).isNull();
        assertThat(table.rows().getFirst().number("year")).isNull();
    }

    @Test
    void refusesAFileThatIsNotACsvAtAll() {
        assertThatExceptionOfType(CsvFormatException.class).isThrownBy(() -> CsvTable.parse("just one line of prose\n"));
        assertThatExceptionOfType(CsvFormatException.class).isThrownBy(() -> CsvTable.parse("   "));
    }

    @Test
    void reportsWhichColumnsAreThere() {
        CsvTable table = CsvTable.parse("Simkl ID,Title,TMDB\n1,Akira,149\n");

        assertThat(table.has("tmdb")).isTrue();
        assertThat(table.has("imdb", "tmdb")).isTrue();
        assertThat(table.has("anilist_id")).isFalse();
    }
}
