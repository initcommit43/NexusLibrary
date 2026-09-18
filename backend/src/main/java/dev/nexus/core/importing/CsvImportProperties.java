package dev.nexus.core.importing;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How many rows an uploaded CSV export may hold before it is refused outright.
 *
 * <p>Parsing happens on the request thread (see {@link CsvImportService}), and each row goes
 * on to spend its own share of source budget once the import runs — a file with tens of
 * thousands of them is not a library anyone actually has, and a cap here is cheaper than
 * finding that out row by row.
 */
@Validated
@ConfigurationProperties(prefix = "nexus.import")
public record CsvImportProperties(@Positive int maxCsvRows) {}
