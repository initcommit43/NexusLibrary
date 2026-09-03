package dev.nexus.support;

import dev.nexus.support.HttpTestClient.Response;
import java.util.Map;

/** Registers accounts and hands back their bearer headers. */
public final class AuthenticatedTest {

    private AuthenticatedTest() {}

    public static final String PASSWORD = "correct-horse-battery";

    public static String registerAndGetToken(HttpTestClient http, String email, String username) {
        Response response =
                http.postJson(
                        "/auth/register",
                        Map.of("email", email, "username", username, "password", PASSWORD, "client", "WEB"));
        if (response.status() != 201) {
            throw new IllegalStateException("Could not register " + email + ": " + response.rawBody());
        }
        return response.accessToken();
    }
}
