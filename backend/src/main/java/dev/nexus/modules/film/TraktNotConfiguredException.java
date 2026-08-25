package dev.nexus.modules.film;

/** No Trakt credentials on this server, so no account can be connected to it. */
public class TraktNotConfiguredException extends RuntimeException {

    public TraktNotConfiguredException() {
        super("Trakt is not configured on this server.");
    }
}
