package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.nexus.core.exporting.CsvWriter;
import dev.nexus.core.importing.CsvTable;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a writer has to get right is the character that ends a field early. These pin the
 * three that do it — a comma, a quote, a newline in a note — plus the round trip, since an
 * export nobody can read back is only half a feature.
 */
class CsvWriterTest {

    @Test
    void writesAHeaderAndQuotesEveryField() {
        CsvWriter writer = new CsvWriter(List.of("title", "status"));
        writer.row(List.of("Akira", "COMPLETED"));

        assertThat(writer.toCsv()).isEqualTo("\"title\",\"status\"\r\n\"Akira\",\"COMPLETED\"\r\n");
    }

    @Test
    void doublesQuotesAndKeepsCommasInsideAField() {
        CsvWriter writer = new CsvWriter(List.of("title", "notes"));
        writer.row(List.of("Dr. Strangelove, or: How I Learned", "He said \"stop\""));

        CsvTable.Row row = CsvTable.parse(writer.toCsv()).rows().getFirst();

        assertThat(row.value("title")).isEqualTo("Dr. Strangelove, or: How I Learned");
        assertThat(row.value("notes")).isEqualTo("He said \"stop\"");
    }

    /** A note written over two lines is one cell, not two rows. */
    @Test
    void keepsANewlineInsideAField() {
        CsvWriter writer = new CsvWriter(List.of("title", "notes"));
        writer.row(List.of("Berserk", "Read the manga.\nThen watch it."));

        CsvTable table = CsvTable.parse(writer.toCsv());

        assertThat(table.rows()).hasSize(1);
        assertThat(table.rows().getFirst().value("notes")).isEqualTo("Read the manga.\nThen watch it.");
    }

    @Test
    void writesANullAsAnEmptyCell() {
        CsvWriter writer = new CsvWriter(List.of("title", "rating"));
        writer.row(Arrays.asList("Akira", null));

        assertThat(writer.toCsv()).endsWith("\"Akira\",\"\"\r\n");
    }

    /** A short row would silently shift every value after it into the wrong column. */
    @Test
    void refusesARowThatIsNotAsWideAsTheHeader() {
        CsvWriter writer = new CsvWriter(List.of("title", "status"));

        assertThatIllegalArgumentException().isThrownBy(() -> writer.row(List.of("Akira")));
    }
}
