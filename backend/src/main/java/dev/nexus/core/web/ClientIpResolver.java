package dev.nexus.core.web;

import dev.nexus.config.NexusProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Who a request came from, as far as that can be trusted.
 *
 * <p>{@code getRemoteAddr()} is the proxy in front, not the caller. Behind Railway every
 * request arrives from the platform's edge, so a per-IP limit keyed on it is one bucket for
 * the whole deployment: one person mistyping a password spends everyone's allowance, and an
 * attacker is handed the same allowance as all the real users put together.
 *
 * <p>{@code X-Forwarded-For} carries the answer but cannot be read from the left. Proxies
 * append rather than overwrite, so a caller who sends the header themselves puts a value of
 * their choosing at the head of the list and picks their own bucket. Only the entries the
 * proxies added are worth anything, and those are at the end — the last is what the nearest
 * proxy saw, and each hop before it sits one place further left. Counting in from the right
 * by the number of proxies actually in front is the only reading a caller cannot influence,
 * which is why the count is configuration rather than a guess: it is 1 for Railway alone and
 * 2 with a CDN in front of it, and a count that is too high reads a spoofed entry.
 */
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final int trustedProxyCount;

    public ClientIpResolver(NexusProperties properties) {
        this.trustedProxyCount = properties.security().trustedProxyCount();
    }

    public String resolve(HttpServletRequest request) {
        String header = request.getHeader(FORWARDED_FOR);

        // Nothing in front, or nothing forwarded: the peer is the caller.
        if (trustedProxyCount == 0 || header == null || header.isBlank()) {
            return request.getRemoteAddr();
        }

        String[] hops = header.split(",");
        int caller = hops.length - trustedProxyCount;

        // A chain shorter than the one configured is not the deployment this was set up for.
        // Rather than read whatever is there, fall back to the address that cannot be forged.
        if (caller < 0) {
            return request.getRemoteAddr();
        }

        String address = hops[caller].trim();
        return address.isEmpty() ? request.getRemoteAddr() : address;
    }
}
