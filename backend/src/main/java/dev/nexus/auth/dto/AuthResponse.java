package dev.nexus.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param refreshToken present only for a native client, which has to keep the token itself.
 *     A browser is never told its own: an injected script could read anything the page can,
 *     so the web's arrives in an httpOnly cookie and is absent here.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {

    public static AuthResponse forBrowser(String accessToken, UserResponse user) {
        return new AuthResponse(accessToken, null, user);
    }

    public static AuthResponse forNativeClient(String accessToken, String refreshToken, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, user);
    }
}
