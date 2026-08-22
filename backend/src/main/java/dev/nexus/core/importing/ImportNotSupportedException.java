package dev.nexus.core.importing;

/** No module contributes an adapter or resolver for that provider yet. */
public class ImportNotSupportedException extends RuntimeException {

    public ImportNotSupportedException(String message) {
        super(message);
    }
}
