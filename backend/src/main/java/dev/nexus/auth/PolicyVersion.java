package dev.nexus.auth;

/**
 * Which version of the terms and privacy policy a new account is agreeing to.
 *
 * <p>A constant rather than configuration: it names the text that ships in this build, and a
 * deployment cannot be allowed to claim its readers accepted something they were never
 * shown. Raise it in the same commit that changes {@code TermsPage} or {@code PrivacyPage},
 * and only when the change is one a reader should have to accept again — a typo is not.
 *
 * <p>Dated rather than numbered so the value matches the "Last updated" line on the pages
 * themselves, which is what a reader would quote when asking what they agreed to.
 */
public final class PolicyVersion {

    public static final String CURRENT = "2026-10-04";

    private PolicyVersion() {}
}
