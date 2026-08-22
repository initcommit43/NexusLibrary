package dev.nexus.modules.games;

/**
 * Steam kept refusing requests even after backing off.
 *
 * <p>Distinct from a general outage because the answer is different: everything synced so
 * far is saved, and running again shortly picks up where it stopped.
 */
public class SteamThrottledException extends RuntimeException {

    public SteamThrottledException() {
        super("Steam is rate limiting requests.");
    }
}
