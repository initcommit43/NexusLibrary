package dev.nexus.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank
                @Size(min = 3, max = 32)
                @Pattern(
                        regexp = "^[a-zA-Z0-9_-]+$",
                        message = "may only contain letters, numbers, underscores and hyphens")
                String username,
        // bcrypt silently ignores input past 72 bytes, so cap it rather than let a
        // longer password give a false sense of strength.
        @NotBlank @Size(min = 12, max = 72) String password) {}
