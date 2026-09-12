package dev.nexus.core.mail;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Whether a Resend key has actually been given, as opposed to merely declared.
 *
 * <p>{@code @ConditionalOnProperty} cannot answer this. The key is written
 * {@code ${RESEND_API_KEY:}} so the app boots without one, and that empty default makes the
 * property present with the value {@code ""} — which {@code @ConditionalOnProperty} counts as
 * set. The real sender then started on every deployment, keyless, and every sign-up failed
 * against Resend with a 401 while the logging stand-in never ran. Blank is not configured.
 */
public class ResendKeyConfigured implements Condition {

    static final String KEY = "nexus.mail.resend.api-key";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String key = context.getEnvironment().getProperty(KEY);
        return key != null && !key.isBlank();
    }

    /** The inverse, for the stand-in that should run exactly when the real sender does not. */
    public static class Missing implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !new ResendKeyConfigured().matches(context, metadata);
        }
    }
}
