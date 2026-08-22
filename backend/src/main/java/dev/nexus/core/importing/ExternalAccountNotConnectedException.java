package dev.nexus.core.importing;

public class ExternalAccountNotConnectedException extends RuntimeException {

    public ExternalAccountNotConnectedException() {
        super("No account is connected for that provider.");
    }
}
