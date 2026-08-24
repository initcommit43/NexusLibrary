package dev.nexus.modules.anime;

/**
 * The callback arrived without a live verifier — the attempt expired, the server
 * restarted, or the callback was replayed. Starting over mints a fresh one; nothing else
 * can, since the verifier is deliberately single-use.
 */
public class MalAuthorizationExpiredException extends RuntimeException {

    public MalAuthorizationExpiredException() {
        super("The MyAnimeList link attempt is no longer valid.");
    }
}
