package dev.nexus.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.support.AuthenticatedTest;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The cap on a JSON request body — nothing else in the security chain bounds one at all. See
 * {@code application.yml}'s {@code nexus.request.max-json-body-size} (1MB in every profile
 * here) and {@link dev.nexus.core.web.RequestSizeLimitFilter}.
 */
class RequestSizeLimitIntegrationTest extends PostgresIntegrationTest {

    private static final int OVER_THE_CAP = 1_100_000;

    @LocalServerPort
    int port;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
    }

    @Test
    void aJsonBodyOverTheCapIsRefused() {
        Response response = http.postJson(
                "/auth/register", Map.of("email", "big@example.com", "padding", "a".repeat(OVER_THE_CAP)));

        assertThat(response.status()).isEqualTo(413);
        assertThat(response.body()).containsEntry("message", "That request is too large.");
    }

    /**
     * No {@code Content-Length} at all — chunked transfer — must not read as "no limit". The
     * body is counted as it streams in and cut off mid-read instead of trusting the missing
     * header.
     */
    @Test
    void aChunkedJsonBodyOverTheCapIsStillRefused() {
        String raw = "{\"padding\":\"" + "a".repeat(OVER_THE_CAP) + "\"}";

        Response response = http.postJsonChunked("/auth/register", raw);

        assertThat(response.status()).isEqualTo(413);
    }

    @Test
    void aJsonBodyUnderTheCapPasses() {
        Response response = http.postJson(
                "/auth/register",
                Map.of(
                        "email", "small@example.com",
                        "username", "smallbody",
                        "password", AuthenticatedTest.PASSWORD,
                        "client", "WEB",
                        "dateOfBirth", "1990-01-01",
                        "acceptedTerms", true));

        assertThat(response.status()).isEqualTo(201);
    }

    /**
     * The one multipart route in the app — the CSV import upload — is untouched by this cap:
     * the filter only bounds a JSON content type, so a file well over 1MB still reaches the
     * controller.
     */
    @Test
    void aMultipartUploadOverTheJsonCapStillWorks() {
        String token = AuthenticatedTest.registerAndGetToken(http, "importer@example.com", "importer");

        // One field padded past 1MB, not tens of thousands of short rows: this is about the
        // byte cap, not the separate row cap on an import (see CsvImportServiceTest).
        String csv = "Simkl ID,Title,TMDB\n1," + "a".repeat(OVER_THE_CAP) + ",550\n";

        Response response = http.postMultipart(
                "/integrations/SIMKL/import/csv",
                "file",
                "export.csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "Authorization",
                "Bearer " + token);

        assertThat(response.status()).isNotEqualTo(413);
        assertThat(response.status()).isEqualTo(200);
    }
}
