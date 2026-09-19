package dev.nexus.auth.agreements;

import dev.nexus.auth.AuthClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param client which platform these acceptances are recorded against. Required, and taken
 *     from the caller's own declaration for the same reason login takes it: nothing else in
 *     the request says what kind of client this is, and a User-Agent is not an answer.
 */
public record AcceptAgreementsRequest(
        @NotNull AuthClient client,
        @NotEmpty @Size(max = 16) List<@Valid AcceptedAgreement> agreements) {}
