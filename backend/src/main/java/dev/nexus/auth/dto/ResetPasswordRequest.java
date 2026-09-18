package dev.nexus.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The link that was mailed out, and the password to set with it. No client here, unlike
 * {@link LoginRequest}: a reset hands back no session, so there is nowhere for a token to go.
 */
public record ResetPasswordRequest(
        // Issued tokens are 43 characters (32 random bytes, base64url); the bound is only
        // there so a caller cannot send something unbounded for the lookup to hash.
        @NotBlank @Size(max = 64) String token,
        // The same rules registration sets; bcrypt ignores input past 72 bytes.
        @NotBlank @Size(min = 12, max = 72) String password) {}
