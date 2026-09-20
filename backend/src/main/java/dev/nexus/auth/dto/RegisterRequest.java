package dev.nexus.auth.dto;

import dev.nexus.auth.agreements.AcceptedAgreement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import dev.nexus.auth.AuthClient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

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
        /**
         * Only its shape is checked here. Whether it clears {@link dev.nexus.auth.AgePolicy#MINIMUM}
         * depends on what day it is, which is the service's call.
         */
        @NotNull @Past(message = "Please give the date you were born.") LocalDate dateOfBirth,
        /** Required; see {@link LoginRequest}. */
        @NotNull AuthClient client,
        /**
         * Whether the reader accepted the terms and the privacy policy.
         *
         * <p>Superseded by {@code acceptedAgreements}, and kept because clients already send
         * it: on its own it records those two documents at their current version and nothing
         * more. Still required, so a caller that sends neither field creates no account.
         *
         * <p>Checked here rather
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
         * <p>Asked of {@code WEB} only. Cloudflare ships no native SDK, so a native client
         * cannot produce one; {@link dev.nexus.auth.AuthController#register} exempts
         * {@code NATIVE} and charges it a confirmed email address instead, because
         * {@code client} is chosen by the caller and an exemption nobody pays for is one
         * anyone can claim.
         */
        @Size(max = 2048) String turnstileToken,
        /**
         * Which documents were on screen and accepted, at which version. Optional, and the
         * replacement for {@code acceptedTerms}: that boolean can say only that something was
         * agreed to, which is not an answer once a document changes or once the documents
         * differ per platform.
         *
         * <p>Left absent by a client that predates this field, and those keep working — see
         * {@link dev.nexus.auth.agreements.AgreementService#acceptTermsAndPrivacy}. Anything
         * not covered here is not assumed: it falls outstanding and is put to the reader on
         * their next sign-in.
         */
        @Valid @Size(max = 16) List<AcceptedAgreement> acceptedAgreements) {

    /** Never null, so neither the controller nor the service has to ask. */
    public List<AcceptedAgreement> acceptedAgreements() {
        return acceptedAgreements == null ? List.of() : acceptedAgreements;
    }
}
