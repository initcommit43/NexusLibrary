package dev.nexus.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The token out of a confirmation link. Sized to the 32 random bytes that produced it. */
public record VerifyEmailRequest(@NotBlank @Size(max = 128) String token) {}
