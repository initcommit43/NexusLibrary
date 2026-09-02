package dev.nexus.auth.dto;

import dev.nexus.auth.AuthClient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param client which kind of client is signing in, and so where its refresh token goes.
 *     Absent means a browser: the web app sends no such field and must keep working.
 */
public record LoginRequest(
        @NotBlank @Size(max = 320) String email, @NotBlank @Size(max = 72) String password, AuthClient client) {

    public AuthClient clientOrBrowser() {
        return client == null ? AuthClient.WEB : client;
    }
}
