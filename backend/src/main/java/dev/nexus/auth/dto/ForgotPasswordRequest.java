package dev.nexus.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The address to send a reset link to. Whether it belongs to an account is not something the
 * answer says — see {@link dev.nexus.auth.PasswordResetService#requestLink}.
 */
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 320) String email) {}
