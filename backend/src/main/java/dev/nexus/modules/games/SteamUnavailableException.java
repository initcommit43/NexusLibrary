package dev.nexus.modules.games;

public class SteamUnavailableException extends RuntimeException {

    public SteamUnavailableException(String message) {
        super(message);
    }

    public SteamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
