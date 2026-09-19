package dev.nexus.auth.agreements;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Arrays;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The current version of each document, and where a reader can go and read it.
 *
 * <p>Configuration rather than a constant so that re-consent is a deploy-time decision and
 * not a migration: raising a version is what puts a document back in front of everyone who
 * accepted the older text, and that should not require a schema change to express.
 *
 * <p>The defaults ship in {@code application.yml} beside the build, which is what keeps the
 * safety {@link dev.nexus.auth.PolicyVersion} argued for — a deployment cannot claim a
 * version its own pages do not show unless someone deliberately overrides it. Raise the
 * default in the same commit that changes the document's text, and only when the change is
 * one a reader should have to accept again.
 *
 * <p>Versions are dated, so they sort and so they match the "Last updated" line on the page.
 */
@Validated
@ConfigurationProperties(prefix = "nexus.agreements")
public record AgreementProperties(@NotEmpty Map<Agreement, @Valid Document> documents) {

    /**
     * @param url where the text is, relative to whichever host is serving the reader. Relative
     *     on purpose: the web and the native build reach the same document over different
     *     origins, and an absolute URL here would be wrong for one of them.
     */
    public record Document(@NotBlank String version, @NotBlank String url) {}

    /**
     * A document with no version declared could never be accepted, and its absence would only
     * show as a sign-in that can never be completed. Fail the boot instead.
     */
    @PostConstruct
    void everyDocumentIsDeclared() {
        String missing = Arrays.stream(Agreement.values())
                .filter(agreement -> !documents.containsKey(agreement))
                .map(Enum::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        if (!missing.isEmpty()) {
            throw new IllegalStateException("nexus.agreements.documents has no entry for: " + missing);
        }
    }

    public Document of(Agreement agreement) {
        return documents.get(agreement);
    }

    public String versionOf(Agreement agreement) {
        return of(agreement).version();
    }
}
