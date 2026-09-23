package dev.nexus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/** The store a push sender will read: which devices each reader can be reached on. */
class DeviceTokenIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @LocalServerPort
    int port;

    @Autowired
    DeviceTokenRepository devices;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
    }

    @Test
    void aDeviceIsStoredForTheReaderWhoRegisteredIt() {
        String reader = register("reader@example.com", "reader");

        assertThat(put(reader, "apns-token-1", "IOS").status()).isEqualTo(204);

        DeviceToken stored = devices.findAll().getFirst();
        assertThat(stored.getToken()).isEqualTo("apns-token-1");
        assertThat(stored.getPlatform()).isEqualTo(DevicePlatform.IOS);
        assertThat(stored.getUserId()).isEqualTo(userIdOf(reader));
        assertThat(stored.getRegisteredAt()).isNotNull();
        assertThat(stored.getLastSeenAt()).isNotNull();
    }

    /** The app registers on every cold start; that is one device seen again, not a new one. */
    @Test
    void registeringTheSameTokenAgainOnlyMovesLastSeenOn() {
        String reader = register("reader@example.com", "reader");
        put(reader, "fcm-token-1", "ANDROID");
        DeviceToken first = devices.findAll().getFirst();

        put(reader, "fcm-token-1", "ANDROID");

        assertThat(devices.findAll()).hasSize(1);
        DeviceToken again = devices.findAll().getFirst();
        assertThat(again.getRegisteredAt()).isEqualTo(first.getRegisteredAt());
        assertThat(again.getLastSeenAt()).isAfterOrEqualTo(first.getLastSeenAt());
    }

    /** A token names the phone, so someone else signing in on it takes it over. */
    @Test
    void aTokenRegisteredByAnotherReaderMovesToThem() {
        String previous = register("previous@example.com", "previous");
        String next = register("next@example.com", "next");
        put(previous, "shared-phone", "IOS");

        put(next, "shared-phone", "IOS");

        assertThat(devices.findAll())
                .singleElement()
                .extracting(DeviceToken::getUserId)
                .isEqualTo(userIdOf(next));
    }

    @Test
    void unregisteringRemovesTheDevice() {
        String reader = register("reader@example.com", "reader");
        put(reader, "apns-token-1", "IOS");

        assertThat(http.deleteJson("/devices", Map.of("token", "apns-token-1"), "Authorization", bearer(reader))
                        .status())
                .isEqualTo(204);

        assertThat(devices.count()).isZero();
    }

    /** Knowing someone's token is not a way to stop their notifications, nor to learn it is theirs. */
    @Test
    void namingAnotherReadersTokenRemovesNothingAndSaysNothing() {
        String owner = register("owner@example.com", "owner");
        String other = register("other@example.com", "other");
        put(owner, "owners-phone", "IOS");

        assertThat(http.deleteJson("/devices", Map.of("token", "owners-phone"), "Authorization", bearer(other))
                        .status())
                .isEqualTo(204);

        assertThat(devices.count()).isEqualTo(1);
    }

    @Test
    void changingThePasswordForgetsEveryDevice() {
        String reader = register("reader@example.com", "reader");
        put(reader, "apns-token-1", "IOS");
        put(reader, "fcm-token-1", "ANDROID");

        Response changed = http.postJson(
                "/settings/account/password",
                Map.of("currentPassword", PASSWORD, "newPassword", "a whole new one", "client", "WEB"),
                "Authorization",
                bearer(reader));

        assertThat(changed.status()).isEqualTo(200);
        assertThat(devices.count()).isZero();
    }

    @Test
    void deletingTheAccountTakesItsDevicesWithIt() {
        String reader = register("reader@example.com", "reader");
        put(reader, "apns-token-1", "IOS");

        http.deleteJson("/settings/account", Map.of("password", PASSWORD), "Authorization", bearer(reader));

        assertThat(devices.count()).isZero();
    }

    @Test
    void oneAccountCannotFillTheTableWithoutLimit() {
        String reader = register("reader@example.com", "reader");

        for (int i = 0; i <= DeviceTokenService.MAX_DEVICES_PER_USER; i++) {
            put(reader, "token-" + i, "ANDROID");
        }

        assertThat(devices.count()).isEqualTo(DeviceTokenService.MAX_DEVICES_PER_USER);
        assertThat(devices.findAll()).extracting(DeviceToken::getToken).doesNotContain("token-0");
    }

    @Test
    void bothRoutesNeedASignedInReader() {
        assertThat(http.putJson("/devices", Map.of("token", "t", "platform", "IOS")).status()).isEqualTo(401);
        assertThat(http.deleteJson("/devices", Map.of("token", "t")).status()).isEqualTo(401);
    }

    @Test
    void aRegistrationWithoutATokenOrWithAnUnknownPlatformIsRefused() {
        String reader = register("reader@example.com", "reader");

        assertThat(put(reader, Map.of("platform", "IOS")).status()).isEqualTo(400);
        assertThat(put(reader, Map.of("token", "t", "platform", "WINDOWS_PHONE")).status()).isEqualTo(400);
        assertThat(put(reader, Map.of("token", "x".repeat(513), "platform", "IOS")).status()).isEqualTo(400);
        assertThat(devices.count()).isZero();
    }

    private Response put(String accessToken, String token, String platform) {
        return put(accessToken, Map.of("token", token, "platform", platform));
    }

    private Response put(String accessToken, Map<String, ?> body) {
        return http.putJson("/devices", body, "Authorization", bearer(accessToken));
    }

    private Long userIdOf(String accessToken) {
        return ((Number) http.get("/auth/me", "Authorization", bearer(accessToken)).body().get("id")).longValue();
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    /** Signs a reader up and hands back their access token. */
    private String register(String email, String username) {
        return http.postJson(
                        "/auth/register",
                        Map.of(
                                "email", email,
                                "username", username,
                                "password", PASSWORD,
                                "client", "WEB",
                                "dateOfBirth", "1990-01-01",
                                "acceptedTerms", true))
                .accessToken();
    }
}
