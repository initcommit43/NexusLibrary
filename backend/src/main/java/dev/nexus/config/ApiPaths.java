package dev.nexus.config;

/**
 * Where the API lives, in one place.
 *
 * <p>The version sits in the path because the web frontend is no longer going to be the only
 * client. It ships with the backend and always speaks the current version, so a breaking change
 * has always been free; a phone keeps whatever build it was installed with, and a change that
 * suits the frontend can leave every installed copy talking to endpoints that no longer answer.
 * A version segment is what lets the next shape of an endpoint sit beside the old one instead of
 * replacing it.
 *
 * <p>Everything that has to agree on the path derives it from here: the prefix the controllers
 * are mounted under, the public whitelist, and the refresh cookie's scope. Those were three
 * separate literals, and the cookie is the dangerous one — it is scoped to the auth path, so a
 * version bump that missed it would leave the browser withholding the cookie and every session
 * ending at the first refresh, with nothing in the logs to say why.
 */
public final class ApiPaths {

    /** Bump only for a breaking change, and only alongside a plan for the clients still on v1. */
    public static final String VERSION = "v1";

    public static final String PREFIX = "/api/" + VERSION;

    private ApiPaths() {}
}
