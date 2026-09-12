package dev.nexus.core.mail;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Whether this deployment has any {@link Mailer} at all — the real sender, or the dev and test
 * stand-in.
 *
 * <p>Mirrors exactly the two conditions those beans carry, so something built on a mailer exists
 * precisely when a mailer does. That has to be decided up front rather than discovered at send
 * time: a flow that looks an account up and only then finds it cannot mail answers differently
 * for a real address than for a made-up one.
 */
public class MailerAvailable implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return new ResendKeyConfigured().matches(context, metadata)
                || context.getEnvironment().acceptsProfiles(Profiles.of("dev", "test"));
    }
}
