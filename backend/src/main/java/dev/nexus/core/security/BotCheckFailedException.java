package dev.nexus.core.security;

/** The Turnstile token was absent, already spent, or rejected by Cloudflare. */
public class BotCheckFailedException extends RuntimeException {

    public BotCheckFailedException() {
        super("Could not confirm that request came from a browser. Please try again.");
    }
}
