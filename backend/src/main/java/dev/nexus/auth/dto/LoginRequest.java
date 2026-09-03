package dev.nexus.auth.dto;

import dev.nexus.auth.AuthClient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param client which kind of client is signing in, and so where its refresh token goes.
 *     Required: a caller that does not say what it is would otherwise be answered as a
 *     browser, and a native client would silently get a session with no refresh token in
 *     it — working for as long as its access token lasts, then gone.
 */
public record LoginRequest(
        @NotBlank @Size(max = 320) String email,
        @NotBlank @Size(max = 72) String password,
        @NotNull AuthClient client) {}
