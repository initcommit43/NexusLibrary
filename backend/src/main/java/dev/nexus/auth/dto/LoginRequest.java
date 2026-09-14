package dev.nexus.auth.dto;

import dev.nexus.auth.AuthClient;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param login a username or an email address, whichever the reader remembers.
 * @param email the field sign-in took before {@code login}. Still read, so an app build that
 *     predates the change keeps signing in; {@code login} wins when both are sent.
 * @param client which kind of client is signing in, and so where its refresh token goes.
 *     Required: a caller that does not say what it is would otherwise be answered as a
 *     browser, and a native client would silently get a session with no refresh token in
 *     it — working for as long as its access token lasts, then gone.
 */
public record LoginRequest(
        @Size(max = 320) String login,
        @Size(max = 320) String email,
        @NotBlank @Size(max = 72) String password,
        @NotNull AuthClient client) {

    /** What the reader typed to name their account. */
    public String identifier() {
        return login != null && !login.isBlank() ? login.trim() : email;
    }

    @AssertTrue(message = "Enter your username or email.")
    public boolean isLogin() {
        String identifier = identifier();
        return identifier != null && !identifier.isBlank();
    }
}
