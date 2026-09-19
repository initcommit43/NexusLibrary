package dev.nexus.auth.agreements;

/** A document, the version of it that is current, and where to go and read it. */
public record AgreementResponse(Agreement document, String version, String url) {

    static AgreementResponse of(Agreement document, AgreementProperties.Document declared) {
        return new AgreementResponse(document, declared.version(), declared.url());
    }
}
