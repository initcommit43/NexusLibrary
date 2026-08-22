package dev.nexus.auth;

import dev.nexus.config.NexusProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookies {

    static final String COOKIE_NAME = "nexus_refresh";

    private final boolean secure;

    public RefreshCookies(NexusProperties properties) {
        this.secure = properties.security().cookieSecure();
    }

    public ResponseCookie issue(String token, Duration ttl) {
        return base(token).maxAge(ttl).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    public Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                // Strict keeps the cookie off cross-site requests entirely, which is what
                // makes disabling CSRF tokens safe for these endpoints.
                .sameSite("Strict")
                .path("/api/auth");
    }
}
