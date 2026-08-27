package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.PostgresIntegrationTest;
import dev.nexus.support.HttpTestClient.Response;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The download itself: what a browser is handed, and whose rows are in it.
 *
 * <p>Entries are written straight through the repositories rather than tracked over HTTP,
 * because nothing here is about the catalogue adapters — mocking three of them to assert on
 * a CSV would test the mocks.
 */
class EntryExportIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    AppUserRepository users;

    @Autowired
    UserEntryRepository entries;

    @Autowired
    TrackableItemRepository items;

    private HttpTestClient http;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);

        ownerToken = registerAndGetToken(http, "owner@example.com", "owner");
        registerAndGetToken(http, "intruder@example.com", "intruder");

        trackFor("owner@example.com", MediaType.ANIME, "5114", "Fullmetal Alchemist: Brotherhood");
        trackFor("intruder@example.com", MediaType.ANIME, "1535", "Death Note");
    }

    @Test
    void handsBackTheShelfAsANamedCsvFile() {
        Response response = export("ANIME", ownerToken);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.rawBody()).contains("\"title\"").contains("Fullmetal Alchemist: Brotherhood");
    }

    /** The whole point of the file: another reader's shelf is not in it. */
    @Test
    void leavesOtherReadersRowsOut() {
        assertThat(export("ANIME", ownerToken).rawBody()).doesNotContain("Death Note");
    }

    /** A shelf with nothing on it is a header row, not an error. */
    @Test
    void exportsAnEmptyShelfAsAHeaderOnly() {
        Response response = export("BOOK", ownerToken);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.rawBody()).contains("\"title\"").doesNotContain("Fullmetal");
    }

    @Test
    void doesNotExportGames() {
        assertThat(export("GAME", ownerToken).status()).isEqualTo(501);
    }

    @Test
    void refusesAnExportWithoutAToken() {
        assertThat(http.get("/exports/ANIME").status()).isEqualTo(401);
    }

    private Response export(String mediaType, String token) {
        return http.get("/exports/" + mediaType, "Authorization", "Bearer " + token);
    }

    private void trackFor(String email, MediaType mediaType, String externalId, String title) {
        Long userId = users.findByEmail(email).orElseThrow().getId();
        TrackableItem item = items.save(new TrackableItem(
                mediaType, Source.ANILIST, externalId, title, null, null, ItemState.RELEASED, Map.of()));
        entries.save(new UserEntry(userId, item, TrackingStatus.COMPLETED));
    }
}
