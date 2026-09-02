package dev.nexus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.config.ApiPaths;
import dev.nexus.config.NexusProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/** The refresh cookie's scope, which has to track the API prefix rather than be written twice. */
class RefreshCookiesTest {

    private static RefreshCookies cookies(boolean secure) {
        return new RefreshCookies(new NexusProperties(
                new NexusProperties.Jwt("a-signing-key-long-enough-for-hs256-0123", 15, 30),
                new NexusProperties.Security(secure, List.of(), "http://localhost:5173", true, 0),
                new NexusProperties.RateLimit(10, 30, 3)));
    }

    /**
     * The cookie is scoped to the auth path, so it moves with the API version whether or not
     * anyone remembers to move it. Pinned literally as well as by constant: reading
     * {@link ApiPaths} on both sides would let a wrong prefix agree with itself.
     */
    @Test
    void scopesTheCookieToTheVersionedAuthPath() {
        ResponseCookie cookie = cookies(true).issue("token", Duration.ofDays(30));

        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth").isEqualTo(ApiPaths.PREFIX + "/auth");
    }

    /** Clearing has to use the same scope, or the browser keeps the cookie it was told to drop. */
    @Test
    void clearsOnTheSamePathItIssuedOn() {
        RefreshCookies refreshCookies = cookies(true);

        assertThat(refreshCookies.clear().getPath())
                .isEqualTo(refreshCookies.issue("token", Duration.ofDays(30)).getPath());
        assertThat(refreshCookies.clear().getMaxAge()).isZero();
    }

    /** SameSite=Strict is what stands in for CSRF tokens on these endpoints; httpOnly keeps it off JS. */
    @Test
    void keepsTheCookieOffScriptAndOffCrossSiteRequests() {
        ResponseCookie cookie = cookies(true).issue("token", Duration.ofDays(30));

        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.isSecure()).isTrue();
    }

    /** Local dev runs on plain http, where a Secure cookie would simply never be sent. */
    @Test
    void dropsSecureWhenConfiguredForPlainHttp() {
        assertThat(cookies(false).issue("token", Duration.ofDays(30)).isSecure())
                .isFalse();
    }
}
