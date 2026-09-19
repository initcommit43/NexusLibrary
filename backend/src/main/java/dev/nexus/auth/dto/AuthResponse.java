package dev.nexus.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.nexus.auth.agreements.AgreementResponse;
import java.util.List;

/**
 * @param refreshToken present only for a native client, which has to keep the token itself.
 *     A browser is never told its own: an injected script could read anything the page can,
 *     so the web's arrives in an httpOnly cookie and is absent here.
 * @param outstandingAgreements documents this reader has not accepted at their current
 *     version, for the platform they signed in from. Empty means they are clear.
 *     <p>Carried on the session rather than refused: a reader with something outstanding
 *     still gets a working session, because accepting is itself an authenticated call and
 *     there would otherwise be no session to make it with. The client gates its own UI on
 *     this and posts to {@code /agreements/accept}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String accessToken, String refreshToken, UserResponse user, List<AgreementResponse> outstandingAgreements) {

    public static AuthResponse forBrowser(
            String accessToken, UserResponse user, List<AgreementResponse> outstandingAgreements) {
        return new AuthResponse(accessToken, null, user, outstandingAgreements);
    }

    public static AuthResponse forNativeClient(
            String accessToken,
            String refreshToken,
            UserResponse user,
            List<AgreementResponse> outstandingAgreements) {
        return new AuthResponse(accessToken, refreshToken, user, outstandingAgreements);
    }
}
