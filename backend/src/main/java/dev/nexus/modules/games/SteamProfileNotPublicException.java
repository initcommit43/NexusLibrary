package dev.nexus.modules.games;

/**
 * Steam will not reveal achievements for this account.
 *
 * <p>Deliberately separate from {@link SteamProfilePrivateException}: the library needs
 * "Game details" to be public, while achievements check the profile itself. A user can
 * have one set correctly and the other not, so telling them the wrong setting to change
 * sends them in circles.
 */
public class SteamProfileNotPublicException extends RuntimeException {

    public SteamProfileNotPublicException() {
        super("Steam will not share achievements for this profile.");
    }
}
