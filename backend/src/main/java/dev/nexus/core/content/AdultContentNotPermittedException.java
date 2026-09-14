package dev.nexus.core.content;

import dev.nexus.auth.AgePolicy;

/** Turning adult titles on from an account not known to be 18. A 403 that says why. */
public class AdultContentNotPermittedException extends RuntimeException {

    public AdultContentNotPermittedException() {
        super("Showing 18+ titles needs an account that is " + AgePolicy.ADULT + " or over.");
    }
}
