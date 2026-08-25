package dev.nexus.modules.film;

/** No Simkl credentials on this server, so no account can be connected to it. */
public class SimklNotConfiguredException extends RuntimeException {

    public SimklNotConfiguredException() {
        super("Simkl is not configured on this server.");
    }
}
