package dev.nexus.core.security;

import dev.nexus.config.ApiPaths;
import dev.nexus.config.NexusProperties;
import dev.nexus.core.web.ClientIpResolver;
import dev.nexus.core.web.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps everything this app serves — the API, the built frontend and its static files — behind
 * the site password.
 *
 * <p>Runs inside the security chain, after CORS and the header writers, so a preflight is still
 * answered by the CORS filter (it carries no credentials and reveals nothing) and the password
 * page gets the same nosniff, framing and HSTS headers as every other answer.
 *
 * <p>Not a Spring bean on purpose: Boot registers every {@code Filter} bean with the servlet
 * container as well, and this would then run twice.
 */
public class SiteGateFilter extends OncePerRequestFilter {

    public static final String COOKIE = "nexus_site";
    public static final String UNLOCK_PATH = "/site-gate";

    private static final String HEALTH = ApiPaths.PREFIX + "/health";

    private final SiteGate gate;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIp;
    private final boolean cookieSecure;
    private final int attemptsPerMinute;

    public SiteGateFilter(
            SiteGate gate, RateLimiter rateLimiter, ClientIpResolver clientIp, NexusProperties properties) {
        this.gate = gate;
        this.rateLimiter = rateLimiter;
        this.clientIp = clientIp;
        this.cookieSecure = properties.security().cookieSecure();
        this.attemptsPerMinute = properties.rateLimit().authRequestsPerMinute();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!gate.isEnabled() || HEALTH.equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        if (gate.isValid(cookieValue(request), Instant.now())) {
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"This site is locked.\"}");
            return;
        }

        // Only a page load comes back to where it was going; a stray POST lands on the start.
        String next = "GET".equals(request.getMethod()) ? requestedPath(request) : "/";
        SiteGatePage.write(response, HttpServletResponse.SC_UNAUTHORIZED, next, null);
    }

    private static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String requestedPath(HttpServletRequest request) {
        String query = request.getQueryString();
        return safeNext(query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query);
    }

    /**
     * Where to send the browser once it is let in: a path on this site, or the start.
     *
     * <p>A leading {@code //} or {@code /\} is read by browsers as another host, and a control
     * character could split the header, so anything but a plain local path becomes {@code /}.
     */
    static String safeNext(String next) {
        if (next == null
                || !next.startsWith("/")
                || next.startsWith("//")
                || next.startsWith("/\\")
                || next.startsWith(UNLOCK_PATH)
                || next.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            return "/";
        }
        return next;
    }
}
