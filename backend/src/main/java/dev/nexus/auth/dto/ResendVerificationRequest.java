package dev.nexus.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Which address to send another confirmation link to.
 *
 * <p>The address rather than a session, because nobody is signed in while the gate is closed —
 * that is the state this endpoint exists to get someone out of.
 */
public record ResendVerificationRequest(@NotBlank @Email @Size(max = 320) String email) {}
