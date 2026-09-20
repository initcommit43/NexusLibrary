package dev.nexus.auth.dto;

import dev.nexus.auth.AuthClient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The address to send a reset link to. Whether it belongs to an account is not something the
 * answer says — see {@link dev.nexus.auth.PasswordResetService#requestLink}.
 */
public record ForgotPasswordRequest(
        @NotBlank @Email @Size(max = 320) String email,
        /** See {@link RegisterRequest#turnstileToken()}. */
        @Size(max = 2048) String turnstileToken,
        /**
         * Which kind of client is asking, so a native one can be excused the challenge it has
         * no way to answer. Optional, unlike on login and register: the web client predates
         * this field and sends nothing, and absent is read as {@code WEB} — the stricter of the
         * two, so a caller that says nothing is not the one that gets excused.
         */
        AuthClient client) {}
