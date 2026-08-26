package dev.nexus.core.importing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed CSV, addressed by column name rather than by position.
 *
 * <p>Every service spells its export differently — {@code MAL ID}, {@code mal_id} and
 * {@code malId} are the same column, and a European export separates with semicolons —
 * so lookups take a list of names and headers are normalised to letters and digits before
 * matching. A parser pinned to one vendor's exact header would break on the next one.
 *
 * <p>Deliberately not a CSV library: the format needed here is one header row, quoted
 * fields with doubled quotes inside them, and nothing else. Pulling in a dependency to
 * parse that would be more code to audit than the thirty lines it takes.
 */
public final class CsvTable {

    /** What a file has to have before it is worth reading a single row of. */
    private static final int MIN_COLUMNS = 2;

    private final List<String> headers;
    private final List<Row> rows;

    private CsvTable(List<String> headers, List<Row> rows) {
        this.headers = headers;
        this.rows = rows;
    }

    public static CsvTable parse(String text) {
        if (text == null || text.isBlank()) {
            throw new CsvFormatException("That file is empty.");
        }

        // A spreadsheet writes a byte order mark, which would otherwise become part of the
        // first column's name and make it match nothing.
        String body = text.startsWith("﻿") ? text.substring(1) : text;

        List<List<String>> lines = split(body, delimiterOf(body));
        if (lines.isEmpty()) {
            throw new CsvFormatException("That file has no rows.");
        }

        List<String> headers = lines.getFirst().stream().map(CsvTable::normalise).toList();
        if (headers.size() < MIN_COLUMNS) {
            throw new CsvFormatException(
                    "That file does not look like a CSV export: the first row has no column names.");
        }

        List<Row> rows = new ArrayList<>();
        for (List<String> values : lines.subList(1, lines.size())) {
            if (values.stream().allMatch(String::isBlank)) {
                continue;
            }
            Map<String, String> byColumn = new LinkedHashMap<>();
            for (int i = 0; i < headers.size() && i < values.size(); i++) {
                byColumn.put(headers.get(i), values.get(i).trim());
            }
            rows.add(new Row(byColumn));
        }
        return new CsvTable(headers, rows);
    }

    public List<Row> rows() {
        return rows;
    }

    /** True when any of these column names is present, whatever the file calls it exactly. */
    public boolean has(String... columnNames) {
        for (String name : columnNames) {
            if (headers.contains(normalise(name))) {
                return true;
            }
        }
        return false;
    }

    public List<String> headers() {
        return headers;
    }

    /**
     * Semicolons where a locale uses them for lists, tabs where something exported a TSV.
     * Decided on the header line alone: a title with a comma in it would otherwise outvote
     * the real separator.
     */
    private static char delimiterOf(String body) {
        String header = body.lines().findFirst().orElse("");
        long commas = header.chars().filter(c -> c == ',').count();
        long semicolons = header.chars().filter(c -> c == ';').count();
        long tabs = header.chars().filter(c -> c == '\t').count();

        if (semicolons > commas && semicolons >= tabs) {
            return ';';
        }
        return tabs > commas ? '\t' : ',';
    }

    /** One pass, tracking whether we are inside quotes, so a delimiter in a title survives. */
    private static List<List<String>> split(String body, char delimiter) {
        List<List<String>> lines = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);

            if (quoted) {
                if (c == '"') {
                    // A doubled quote inside a quoted field is one literal quote.
                    if (i + 1 < body.length() && body.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }

            switch (c) {
                case '"' -> quoted = true;
                case '\r' -> {
                    // Swallowed: the line ends at the \n that follows.
                }
                case '\n' -> {
                    current.add(field.toString());
                    field.setLength(0);
                    lines.add(current);
                    current = new ArrayList<>();
                }
                default -> {
                    if (c == delimiter) {
                        current.add(field.toString());
                        field.setLength(0);
                    } else {
                        field.append(c);
                    }
                }
            }
        }

        if (!field.isEmpty() || !current.isEmpty()) {
            current.add(field.toString());
            lines.add(current);
        }
        return lines;
    }

    /** Letters and digits only, lowercased, so spelling and punctuation stop mattering. */
    private static String normalise(String header) {
        return header == null ? "" : header.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** One row, read by whichever of several column names this particular export used. */
    public static final class Row {

        private final Map<String, String> values;

        private Row(Map<String, String> values) {
            this.values = values;
        }

        /** The first of these columns that holds anything; null when none of them do. */
        public String value(String... columnNames) {
            for (String name : columnNames) {
                String value = values.get(normalise(name));
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }

        /**
         * The same, as a whole number. Tolerates a decimal point, because a rating column
         * written by a spreadsheet arrives as {@code 8.0} rather than {@code 8}.
         */
        public Integer number(String... columnNames) {
            String raw = value(columnNames);
            if (raw == null) {
                return null;
            }
            try {
                return (int) Math.round(Double.parseDouble(raw.replace(",", ".").trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /** A date, whether the export wrote a day or a full timestamp. */
        public LocalDate date(String... columnNames) {
            String raw = value(columnNames);
            if (raw == null || raw.startsWith("0000")) {
                return null;
            }
            // Goodreads writes 2020/05/12 where everything else writes 2020-05-12. Same
            // field order either way, so the separator is the whole difference.
            String isoish = raw.replace('/', '-');
            try {
                return LocalDate.parse(isoish.length() > 10 ? isoish.substring(0, 10) : isoish);
            } catch (DateTimeParseException e) {
                // Not a plain date; it may still be an instant.
            }
            try {
                return Instant.parse(raw).atZone(ZoneOffset.UTC).toLocalDate();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }
}
