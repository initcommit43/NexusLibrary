package dev.nexus.core.importing;

import java.util.Optional;

/**
 * An external service stopped answering — the counterpart of {@link UserFixableException}
 * for failures nobody on this side can do anything about.
 *
 * <p>The two must stay distinct because they call for opposite advice. "Please try again"
 * on an outage tells a reader to retry something that cannot succeed; what they need to
 * hear is whose fault it is and that waiting is the fix. Like its counterpart, this is an
 * interface rather than a class so a background job can carry the message without core
 * naming any particular module's exceptions.
 */
public interface UpstreamUnavailableException {

    /** The service's name as a reader knows it, fit for the start of a sentence. */
    String serviceName();

    /**
     * What the service said for itself, when it said anything a person could read.
     *
     * <p>AniList once answered every call with "temporarily disabled due to severe
     * stability issues" — a message that explains everything, and that we were throwing
     * away while telling readers to try again. Gateway noise stays absent: a Cloudflare
     * error page is not the service speaking.
     */
    default Optional<String> serviceSays() {
        return Optional.empty();
    }
}
