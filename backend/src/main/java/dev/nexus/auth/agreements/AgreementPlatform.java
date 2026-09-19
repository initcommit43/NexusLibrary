package dev.nexus.auth.agreements;

import dev.nexus.auth.AuthClient;

/**
 * Where an acceptance was given.
 *
 * <p>Derived from the {@code client} field the caller already sends, never from a User-Agent
 * — that header is a string anyone can write, and this decides which documents a person is
 * held to. A separate enum from {@link AuthClient} rather than a reuse of it: that one says
 * where a refresh token goes, and the two would have to be prised apart again the day the
 * stores want their own EULA per platform.
 */
public enum AgreementPlatform {
    WEB,
    NATIVE;

    public static AgreementPlatform of(AuthClient client) {
        return client == AuthClient.NATIVE ? NATIVE : WEB;
    }
}
