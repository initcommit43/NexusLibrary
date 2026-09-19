package dev.nexus.auth.agreements;

import java.util.Map;

/**
 * An acceptance that cannot be recorded as sent: a version that is no longer the current one,
 * or a document that does not apply to the platform the caller is on.
 *
 * <p>Refused rather than quietly corrected to the current version. What a client sends is its
 * claim about which text was on screen, and a server that silently upgrades a stale claim
 * would be recording consent to something nobody was shown.
 */
public class AgreementRejectedException extends RuntimeException {

    private final transient Map<String, String> fieldErrors;

    private AgreementRejectedException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors;
    }

    static AgreementRejectedException staleVersion(Agreement document, String sent, String current) {
        return new AgreementRejectedException(
                "That version is no longer current. Please read it again and accept the current one.",
                Map.of(document.name(), "sent " + sent + ", current " + current));
    }

    static AgreementRejectedException notApplicable(Agreement document, AgreementPlatform platform) {
        return new AgreementRejectedException(
                "That document does not apply to this client.",
                Map.of(document.name(), "does not apply to " + platform));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
