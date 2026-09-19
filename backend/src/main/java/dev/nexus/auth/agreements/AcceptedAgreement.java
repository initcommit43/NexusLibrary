package dev.nexus.auth.agreements;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One (document, version) pair a client says was on screen and accepted.
 *
 * <p>The version travels rather than being assumed: only the client knows which text it
 * actually rendered, and a native build installed months ago may not be showing the current
 * one. {@link AgreementService} refuses anything but the current version, so a stale build
 * is told to update rather than having consent recorded on its behalf.
 */
public record AcceptedAgreement(@NotNull Agreement document, @NotNull @Size(max = 32) String version) {}
