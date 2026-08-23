package dev.nexus.modules.games;

import dev.nexus.core.importing.UserFixableException;

/**
 * Steam returned no library. Almost always the profile's game details are set to private,
 * which no amount of authentication can override: OpenID grants no read scope.
 */
public class SteamProfilePrivateException extends RuntimeException implements UserFixableException {

    public SteamProfilePrivateException() {
        super("Steam returned no games for this account.");
    }

    @Override
    public String advice() {
        return "Steam returned no games. Set \"Game details\" to Public in your Steam privacy "
                + "settings, then try again.";
    }
}
