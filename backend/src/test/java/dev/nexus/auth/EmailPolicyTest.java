package dev.nexus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nexus.config.NexusProperties;
import org.junit.jupiter.api.Test;

/**
 * The shape and blocklist halves, which are pure. Deliverability is deliberately switched off
 * here: it asks the network a question, and a unit test that fails on a train is worse than no
 * unit test. {@link dev.nexus.core.RegistrationHardeningIntegrationTest} covers the wiring.
 */
class EmailPolicyTest {

    private final EmailPolicy policy = new EmailPolicy(settings(true, false));

    private static NexusProperties settings(boolean blockDisposable, boolean requireDeliverable) {
        return new NexusProperties(
                null,
                null,
                null,
                new NexusProperties.Registration(blockDisposable, requireDeliverable, 2500));
    }

    @Test
    void acceptsAnOrdinaryAddress() {
        assertThatCode(() -> policy.check("eric@example.com")).doesNotThrowAnyException();
        assertThatCode(() -> policy.check("first.last+tag@sub.example.co.uk")).doesNotThrowAnyException();
    }

    /** The gap {@code @Email} leaves: it accepts a bare host with no dot in it. */
    @Test
    void refusesAnAddressWithNoRealDomain() {
        assertThatThrownBy(() -> policy.check("someone@localhost"))
                .isInstanceOf(EmailNotAcceptableException.class)
                .hasMessageContaining("does not look like");
    }

    @Test
    void refusesAnAddressWithNoAtSign() {
        assertThatThrownBy(() -> policy.check("notanaddress")).isInstanceOf(EmailNotAcceptableException.class);
    }

    @Test
    void refusesANumericTopLevelDomain() {
        assertThatThrownBy(() -> policy.check("someone@example.123"))
                .isInstanceOf(EmailNotAcceptableException.class);
    }

    @Test
    void refusesAKnownThrowawayService() {
        assertThatThrownBy(() -> policy.check("someone@mailinator.com"))
                .isInstanceOf(EmailNotAcceptableException.class)
                .hasMessageContaining("permanent email address");
    }

    /** Listing every subdomain a throwaway service invents is a race nobody wins. */
    @Test
    void refusesASubdomainOfAThrowawayService() {
        assertThatThrownBy(() -> policy.check("someone@inbox.mailinator.com"))
                .isInstanceOf(EmailNotAcceptableException.class);
    }

    /** A domain that merely ends in a listed one is a different domain. */
    @Test
    void allowsADomainThatOnlyLooksLikeAListedOne() {
        assertThatCode(() -> policy.check("someone@notmailinator.com")).doesNotThrowAnyException();
    }

    @Test
    void theBlocklistCanBeTurnedOff() {
        EmailPolicy permissive = new EmailPolicy(settings(false, false));

        assertThatCode(() -> permissive.check("someone@mailinator.com")).doesNotThrowAnyException();
    }

    /** Case and surrounding space are the reader's, not the address's. */
    @Test
    void normalisesBeforeJudging() {
        assertThatThrownBy(() -> policy.check("  SOMEONE@MailInAtOr.CoM  "))
                .isInstanceOf(EmailNotAcceptableException.class);
    }

    @Test
    void namesTheFieldSoAFormCanPointAtIt() {
        assertThatThrownBy(() -> policy.check("someone@mailinator.com"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(EmailNotAcceptableException.class))
                .satisfies(e -> assertThat(e.getFieldErrors()).containsKey("email"));
    }
}
