package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.config.NexusProperties;
import dev.nexus.core.security.SiteGate;
import dev.nexus.core.security.SiteGateProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SiteGateTest {

    private static final NexusProperties NEXUS = new NexusProperties(
            new NexusProperties.Jwt("test-only-signing-key-not-used-anywhere-else-0123456789", 15, 30, 10),
            null,
            null,
            null);

    private static SiteGate gate(String password) {
        return new SiteGate(new SiteGateProperties(password), NEXUS);
    }

    private final Instant now = Instant.parse("2026-09-14T12:00:00Z");

    @Test
    void noPasswordMeansNoGate() {
        assertThat(gate(null).isEnabled()).isFalse();
        assertThat(gate("  ").isEnabled()).isFalse();
        assertThat(gate("secret").isEnabled()).isTrue();
    }

    @Test
    void onlyThePasswordItselfMatches() {
        SiteGate gate = gate("secret");

        assertThat(gate.passwordMatches("secret")).isTrue();
        assertThat(gate.passwordMatches("Secret")).isFalse();
        assertThat(gate.passwordMatches("")).isFalse();
        assertThat(gate.passwordMatches(null)).isFalse();
    }

    @Test
    void aMintedCookieIsValidUntilItExpires() {
        SiteGate gate = gate("secret");
        String cookie = gate.mint(now);

        assertThat(gate.isValid(cookie, now)).isTrue();
        assertThat(gate.isValid(cookie, now.plus(SiteGate.LIFETIME).minusSeconds(1))).isTrue();
        assertThat(gate.isValid(cookie, now.plus(SiteGate.LIFETIME))).isFalse();
    }

    @Test
    void aTamperedCookieIsNotValid() {
        SiteGate gate = gate("secret");
        String cookie = gate.mint(now);
        String expiry = cookie.substring(0, cookie.indexOf('.'));
        String mac = cookie.substring(cookie.indexOf('.') + 1);

        assertThat(gate.isValid((Long.parseLong(expiry) + 86_400) + "." + mac, now)).isFalse();
        assertThat(gate.isValid(expiry + "." + mac.substring(1), now)).isFalse();
        assertThat(gate.isValid("garbage", now)).isFalse();
        assertThat(gate.isValid(null, now)).isFalse();
    }

    @Test
    void changingThePasswordRetiresEveryCookie() {
        String cookie = gate("secret").mint(now);

        assertThat(gate("a-new-password").isValid(cookie, now)).isFalse();
    }

    @Test
    void theCookieLastsThirtyDays() {
        assertThat(SiteGate.LIFETIME).isEqualTo(Duration.ofDays(30));
    }
}
