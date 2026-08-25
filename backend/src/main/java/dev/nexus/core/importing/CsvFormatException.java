package dev.nexus.core.importing;

/**
 * The uploaded file is not something this import can read - empty, headerless, or missing
 * the one column that identifies a title.
 *
 * <p>User fixable by construction: nobody but the reader can supply a different file, and
 * the advice has to say which columns were wanted rather than only that parsing failed.
 */
public class CsvFormatException extends RuntimeException implements UserFixableException {

    private final String advice;

    public CsvFormatException(String advice) {
        super(advice);
        this.advice = advice;
    }

    @Override
    public String advice() {
        return advice;
    }
}
