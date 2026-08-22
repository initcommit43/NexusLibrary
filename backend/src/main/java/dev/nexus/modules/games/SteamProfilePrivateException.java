package dev.nexus.modules.games;

/**
 * Steam returned no library. Almost always the profile's game details are set to private,
 * which no amount of authentication can override: OpenID grants no read scope.
 */
public class SteamProfilePrivateException extends RuntimeException {

    public SteamProfilePrivateException() {
        super("Steam returned no games for this account.");
    }
}
