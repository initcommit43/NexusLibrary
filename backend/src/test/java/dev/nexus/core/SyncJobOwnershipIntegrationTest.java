package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.jobs.JobRegistry;
import dev.nexus.core.jobs.SyncJob;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * A background job is named by an id in the path, the same shape of request as reaching for
 * someone else's entry, and refused the same way: 404, since a 403 would confirm it exists.
 */
class SyncJobOwnershipIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    AppUserRepository users;

    @Autowired
    JobRegistry jobs;

    private HttpTestClient http;
    private String ownerToken;
    private String intruderToken;
    private SyncJob ownersJob;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        ownerToken = registerAndGetToken(http, "owner@example.com", "owner");
        intruderToken = registerAndGetToken(http, "intruder@example.com", "intruder");

        // Started on the registry itself: whose job it is matters here, not what it imports.
        Long ownerId = users.findByEmail("owner@example.com").orElseThrow().getId();
        ownersJob = jobs.start(ownerId, SyncJob.Kind.IMPORT, Provider.STEAM, 0);
    }

    /**
     * The registry is in memory and outlives the truncate, while user ids restart with it, so
     * a job left running would belong to whoever is user 1 in the next test.
     */
    @AfterEach
    void finishTheJob() {
        if (ownersJob != null) {
            ownersJob.complete();
        }
    }

    @Test
    void anotherUserCannotWatchYourJob() {
        assertThat(job(intruderToken).status()).isEqualTo(404);
        assertThat(job(ownerToken).status()).isEqualTo(200);
    }

    @Test
    void anotherUserCannotCancelYourJob() {
        Response refused = http.delete(
                "/integrations/jobs/" + ownersJob.getId(), "Authorization", "Bearer " + intruderToken);

        assertThat(refused.status()).isEqualTo(404);
        assertThat(ownersJob.isCancelled()).isFalse();
        assertThat(job(ownerToken).body()).containsEntry("state", "RUNNING");
    }

    private Response job(String token) {
        return http.get("/integrations/jobs/" + ownersJob.getId(), "Authorization", "Bearer " + token);
    }
}
