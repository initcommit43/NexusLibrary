package dev.nexus.core.importing;

/** Steam did not confirm the callback, so the claimed SteamID cannot be trusted. */
public class SteamVerificationFailedException extends RuntimeException {

    public SteamVerificationFailedException() {
        super("Could not verify the Steam sign-in. Please try again.");
    }
}
