package dev.nexus.core.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

/**
 * Which sender starts, decided by whether a key was really given.
 *
 * <p>This is the regression that mattered: the key is declared with an empty default so the
 * app boots without one, and presence-based matching read that empty string as configured. The
 * real sender started keyless everywhere, every sign-up failed against the provider, and the
 * logging stand-in — the thing that was supposed to run — never did.
 */
class ResendKeyConfiguredTest {

    private static boolean realSenderStarts(MockEnvironment environment) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return new ResendKeyConfigured().matches(context, mock(AnnotatedTypeMetadata.class));
    }

    private static boolean standInStarts(MockEnvironment environment) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return new ResendKeyConfigured.Missing().matches(context, mock(AnnotatedTypeMetadata.class));
    }

    /** The case that shipped broken: declared, empty. */
    @Test
    void anEmptyKeyIsNotConfigured() {
        MockEnvironment empty = new MockEnvironment().withProperty(ResendKeyConfigured.KEY, "");

        assertThat(realSenderStarts(empty)).isFalse();
        assertThat(standInStarts(empty)).isTrue();
    }

    @Test
    void aWhitespaceKeyIsNotConfigured() {
        MockEnvironment blank = new MockEnvironment().withProperty(ResendKeyConfigured.KEY, "   ");

        assertThat(realSenderStarts(blank)).isFalse();
    }

    @Test
    void anAbsentKeyIsNotConfigured() {
        assertThat(realSenderStarts(new MockEnvironment())).isFalse();
        assertThat(standInStarts(new MockEnvironment())).isTrue();
    }

    @Test
    void aRealKeyStartsTheRealSender() {
        MockEnvironment configured = new MockEnvironment().withProperty(ResendKeyConfigured.KEY, "re_live_key");

        assertThat(realSenderStarts(configured)).isTrue();
        assertThat(standInStarts(configured)).isFalse();
    }

    /** Exactly one of the two, always — never both senders, and never neither. */
    @Test
    void theTwoConditionsAreExactOpposites() {
        for (String value : new String[] {"", "  ", "re_key"}) {
            MockEnvironment environment = new MockEnvironment().withProperty(ResendKeyConfigured.KEY, value);
            assertThat(realSenderStarts(environment)).isNotEqualTo(standInStarts(environment));
        }
    }
}
