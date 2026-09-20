package dev.nexus.auth.agreements;

/**
 * One document, in full, for a client that draws it itself instead of opening a page.
 *
 * <p>The version travels with the text and comes from the same answer, so a client cannot
 * show one document and record acceptance of another — which is the failure a client holding
 * its own compiled-in copy is always one release away from.
 *
 * @param markdown the body only; {@code title} and {@code updated} are the heading above it,
 *     handed over separately so a client can lay them out rather than parse them back out.
 */
public record AgreementTextResponse(
        Agreement document, String version, String title, String updated, String markdown) {

    static AgreementTextResponse of(
            Agreement document, AgreementProperties.Document declared, AgreementDocuments.Document text) {
        return new AgreementTextResponse(
                document, declared.version(), text.title(), text.updated(), text.markdown());
    }
}
