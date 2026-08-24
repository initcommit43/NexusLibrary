package dev.nexus.modules.games;

import dev.nexus.core.importing.UpstreamUnavailableException;

public class SteamUnavailableException extends RuntimeException implements UpstreamUnavailableException {

    public SteamUnavailableException(String message) {
        super(message);
    }

    public SteamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String serviceName() {
        return "Steam";
    }
}
