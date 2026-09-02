package dev.nexus.auth;

/**
 * Which kind of client a session belongs to, which decides how its refresh token is handed
 * over and nothing else.
 *
 * <p>A browser must not be able to read its own refresh token — an injected script would
 * read it too — so the web's arrives in an httpOnly cookie. A native client has no such
 * cookie store worth using and keeps its token in the platform keychain, so it has to be
 * given the value. Both go through the same endpoints and the same revocation.
 */
public enum AuthClient {
    WEB,
    NATIVE
}
