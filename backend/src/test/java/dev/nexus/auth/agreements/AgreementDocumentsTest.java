package dev.nexus.auth.agreements;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The text of each document, and the one thing that must stay true about it: that the words a
 * reader is shown and the version their acceptance is recorded against describe each other.
 *
 * <p>They used to be kept in step by a comment asking for both to be changed in one commit.
 * That is a convention, and a convention cannot fail a build.
 */
class AgreementDocumentsTest extends PostgresIntegrationTest {

    /** The form the "last updated" line is written in, as a reader would read it aloud. */
    private static final DateTimeFormatter AS_WRITTEN =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    @LocalServerPort
    int port;

    @Autowired
    AgreementDocuments documents;

    @Autowired
    AgreementProperties properties;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    /**
     * The check the old comment asked for by hand. A document whose text changed without its
     * version being raised is one every reader has already accepted and will never be asked
     * about again — the failure is silent, permanent, and only visible in a legal dispute.
     */
    @ParameterizedTest
    @EnumSource(Agreement.class)
    void theDateOnTheDocumentIsTheVersionItIsAcceptedAt(Agreement agreement) {
        AgreementDocuments.Document text = documents.of(agreement);

        LocalDate written = LocalDate.parse(text.updated(), AS_WRITTEN);

        assertThat(written.toString())
                .as("%s.md says it was updated %s; application.yml calls it version %s",
                        agreement.name().toLowerCase(Locale.ROOT), text.updated(), properties.versionOf(agreement))
                .isEqualTo(properties.versionOf(agreement));
    }

    @ParameterizedTest
    @EnumSource(Agreement.class)
    void everyDocumentHasWordsInIt(Agreement agreement) {
        AgreementDocuments.Document text = documents.of(agreement);

        assertThat(text.title()).isNotBlank();
        assertThat(text.markdown()).isNotBlank();
        // The front matter is the file's own metadata and has no business in what is rendered.
        assertThat(text.markdown()).doesNotStartWith("---");
    }

    /**
     * The web renders these with a renderer written for exactly the constructs they use
     * ({@code LegalDocument.tsx}), rather than pulling a general Markdown pipeline into the
     * bundle to draw four documents we write ourselves. That trade is only safe while it is
     * enforced: a table or a bold run added to a policy would otherwise reach the page as its
     * own source text, and nobody would notice until a reader did.
     */
    @ParameterizedTest
    @EnumSource(Agreement.class)
    void everyDocumentStaysInsideTheSubsetTheWebCanDraw(Agreement agreement) {
        for (String block : documents.of(agreement).markdown().split("\\n{2,}")) {
            String first = block.lines().findFirst().orElse("");

            assertThat(first)
                    .as("block in %s.md starts with something the renderer does not draw",
                            agreement.name().toLowerCase(Locale.ROOT))
                    .matches("^(## |### |- |[A-Za-z\\[`]).*");

            assertThat(block)
                    .as("block in %s.md uses Markdown the renderer does not draw",
                            agreement.name().toLowerCase(Locale.ROOT))
                    .doesNotContain("**", "![", "```", "<", "|")
                    .doesNotContainPattern("(?m)^\\s*(\\d+\\.|>|\\*)\\s");
        }
    }

    /** A sign-up form reads this before there is an account to read it with. */
    @Test
    void theTextIsServedWithoutSigningIn() {
        Response served = http.get("/agreements/documents/TERMS");

        assertThat(served.status()).isEqualTo(200);
        assertThat(served.body()).containsEntry("document", "TERMS");
        assertThat(String.valueOf(served.body().get("version")))
                .isEqualTo(properties.versionOf(Agreement.TERMS));
        assertThat(String.valueOf(served.body().get("markdown"))).contains("## Your account");
    }

    /**
     * Guards the whitelist entry that serves the text. It is a wildcard, and the route it must
     * not reach sits one segment away.
     */
    @Test
    void whatThisReaderStillOwesIsStillNotPublic() {
        assertThat(http.get("/agreements/outstanding?client=WEB").status()).isEqualTo(401);
    }

    /** The EULA is native-only to accept, but readable anywhere — a store listing links to it. */
    @Test
    void aDocumentIsReadableFromAPlatformThatIsNeverAskedToAcceptIt() {
        assertThat(http.get("/agreements/documents/EULA").status()).isEqualTo(200);
    }

    @Test
    void anUnknownDocumentIsNotFound() {
        assertThat(http.get("/agreements/documents/NONSENSE").status()).isEqualTo(400);
    }
}
