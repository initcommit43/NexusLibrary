package dev.nexus.core.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.Provider;
import dev.nexus.modules.film.SimklCsvImportAdapter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The row cap the upload route enforces before an adapter ever sees the file — {@link
 * CsvTableTest} already covers {@link CsvTable} refusing past it; this pins that the service
 * actually reads its configured limit rather than the parser's own unbounded default.
 */
class CsvImportServiceTest {

    private final CsvImportService service =
            new CsvImportService(List.of(new SimklCsvImportAdapter()), new CsvImportProperties(3));

    @Test
    void parsesAFileAtTheCap() {
        List<ImportedEntry> entries = service.parse(Provider.SIMKL, csvOf(3));

        assertThat(entries).hasSize(3);
    }

    @Test
    void refusesAFileOverTheCapWithAGenericMessage() {
        assertThatExceptionOfType(CsvFormatException.class)
                .isThrownBy(() -> service.parse(Provider.SIMKL, csvOf(4)))
                .withMessageContaining("3");
    }

    private byte[] csvOf(int rows) {
        StringBuilder csv = new StringBuilder("Simkl ID,Title,TMDB\n");
        for (int i = 0; i < rows; i++) {
            csv.append(i).append(",Title ").append(i).append(",").append(100 + i).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
}
