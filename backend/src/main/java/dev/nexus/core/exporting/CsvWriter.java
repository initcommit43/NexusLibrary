package dev.nexus.core.exporting;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a CSV document a header row at a time.
 *
 * <p>The counterpart to {@link dev.nexus.core.importing.CsvTable}, and deliberately as small:
 * writing RFC 4180 is a quoting rule and a line ending, and a dependency for that would be
 * more to audit than the code it replaces.
 *
 * <p>Rows end in CRLF and every field is quoted. Both are what a spreadsheet expects — Excel
 * opening a bare LF file puts every row in one cell — and quoting unconditionally means a
 * title full of commas and a plain number take the same path, so there is one behaviour to
 * be wrong about rather than two.
 */
public final class CsvWriter {

    private static final String LINE_END = "\r\n";

    private final List<String> columns;
    private final StringBuilder body = new StringBuilder();

    public CsvWriter(List<String> columns) {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("A CSV needs at least one column");
        }
        this.columns = List.copyOf(columns);
        writeRow(this.columns);
    }

    /**
     * @throws IllegalArgumentException when the row is not as wide as the header, which would
     *     otherwise shift every later value into the wrong column
     */
    public void row(List<String> values) {
        if (values.size() != columns.size()) {
            throw new IllegalArgumentException(
                    "Row has " + values.size() + " values but the header has " + columns.size());
        }
        writeRow(values);
    }

    public String toCsv() {
        return body.toString();
    }

    private void writeRow(List<String> values) {
        List<String> quoted = new ArrayList<>(values.size());
        for (String value : values) {
            quoted.add(quote(value));
        }
        body.append(String.join(",", quoted)).append(LINE_END);
    }

    /** A null is an empty cell, and a quote inside a field is doubled. */
    private static String quote(String value) {
        String text = value == null ? "" : value;
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
