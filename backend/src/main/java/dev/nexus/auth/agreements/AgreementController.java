package dev.nexus.auth.agreements;

import dev.nexus.auth.AuthClient;
import dev.nexus.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The documents a reader is asked to accept.
 *
 * <p>Which ones apply is decided by the {@code client} the caller declares, on every route
 * here — never by a User-Agent. A browser and a phone are offered different sets, and a
 * native caller is never shown the cookie notice.
 */
@RestController
@RequestMapping("/agreements")
public class AgreementController {

    private final AgreementService agreements;

    public AgreementController(AgreementService agreements) {
        this.agreements = agreements;
    }

    /**
     * Public: a sign-up form has to show what it is asking to be accepted before anyone has an
     * account to be asked about. Says nothing about any person — only which documents exist,
     * at which version, and where to read them.
     *
     * <p>{@code client} is required rather than defaulted: a caller that does not say what it
     * is would be shown the web's set, and a phone would be handed a cookie notice it can
     * never act on.
     */
    @GetMapping
    public List<AgreementResponse> applicable(@RequestParam AuthClient client) {
        return agreements.current(AgreementPlatform.of(client));
    }

    /** What this reader still owes on the client they are calling from. Empty means clear. */
    @GetMapping("/outstanding")
    public List<AgreementResponse> outstanding(
            @AuthenticationPrincipal CurrentUser user, @RequestParam AuthClient client) {
        return agreements.outstanding(user.id(), AgreementPlatform.of(client));
    }

    /**
     * Records acceptance. 204 rather than the new outstanding set: the caller knows what it
     * sent, and a client that wants to be sure asks {@code /outstanding} again.
     */
    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody AcceptAgreementsRequest request) {
        agreements.accept(user.id(), AgreementPlatform.of(request.client()), request.agreements());
    }
}
