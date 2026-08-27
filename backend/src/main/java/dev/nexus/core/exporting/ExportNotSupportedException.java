package dev.nexus.core.exporting;

/** Asked for a CSV of something this app does not hand back as a file. */
public class ExportNotSupportedException extends RuntimeException {

    public ExportNotSupportedException(String message) {
        super(message);
    }
}
