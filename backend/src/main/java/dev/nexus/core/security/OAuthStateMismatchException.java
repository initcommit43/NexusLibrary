package dev.nexus.core.security;

/**
 * The callback did not carry the state this session minted — it expired, it was replayed,
 * or the approval was begun somewhere other than here. Starting over mints a fresh one;
 * nothing else can, since the state is deliberately single-use.
 */
public class OAuthStateMismatchException extends RuntimeException {

    private final String service;

    public OAuthStateMismatchException(String service) {
        super("The " + service + " link attempt is no longer valid.");
        this.service = service;
    }

    /** The provider's name as a reader knows it, for the sentence shown to them. */
    public String service() {
        return service;
    }
}
