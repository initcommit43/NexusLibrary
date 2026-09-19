package dev.nexus.auth.agreements;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * A document a reader is asked to accept, and where it applies.
 *
 * <p>Which platforms a document covers is product structure and lives here in code; what its
 * current version is changes on its own and lives in {@link AgreementProperties}.
 *
 * <p>The asymmetry is deliberate. An EULA is what an app store requires of a native build and
 * has nothing to say about a website. Cookie consent is the mirror image: a native client
 * stores its refresh token in the platform keychain and sets no cookie at all, so asking it
 * would be asking about something that does not happen.
 */
public enum Agreement {
    TERMS(Set.of(AgreementPlatform.WEB, AgreementPlatform.NATIVE)),
    PRIVACY(Set.of(AgreementPlatform.WEB, AgreementPlatform.NATIVE)),
    /** The App Store requires one of the native build; a browser is never shown it. */
    EULA(Set.of(AgreementPlatform.NATIVE)),
    /** Web only — see the class comment. Never offered to a native caller. */
    COOKIES(Set.of(AgreementPlatform.WEB));

    private final Set<AgreementPlatform> platforms;

    Agreement(Set<AgreementPlatform> platforms) {
        this.platforms = platforms;
    }

    public boolean appliesTo(AgreementPlatform platform) {
        return platforms.contains(platform);
    }

    /** Declaration order, so a client shows them in the same order every time. */
    public static List<Agreement> forPlatform(AgreementPlatform platform) {
        return Arrays.stream(values()).filter(a -> a.appliesTo(platform)).toList();
    }
}
