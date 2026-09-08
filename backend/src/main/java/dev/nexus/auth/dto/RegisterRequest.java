package dev.nexus.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import dev.nexus.auth.AuthClient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        @NotBlank @Size(min = 12, max = 72) String password,
        /** Required; see {@link LoginRequest}. */
        @NotNull AuthClient client,
        /**
         * Whether the reader accepted the terms and the privacy policy. Checked here rather
         * than trusted from the form: the web client disables its own button until the box
         * is ticked, but a native client and a direct call to this endpoint go through no
         * such form, and an account with no recorded consent is one nobody can account for.
         */
        @AssertTrue(message = "Please accept the terms and privacy policy.") boolean acceptedTerms,
        /**
         * The Turnstile token the widget produced. Deliberately not {@code @NotBlank}: a
         * deployment with no keys set switches the check off, and validating it here would
         * demand a token that no client could obtain. {@link
         * dev.nexus.core.security.TurnstileVerifier} decides whether its absence matters.
         *
         * <p>Required of every client, native included. {@code client} is chosen by the
         * caller, so exempting {@code NATIVE} would exempt anyone willing to claim it.
         */
        String turnstileToken) {}
