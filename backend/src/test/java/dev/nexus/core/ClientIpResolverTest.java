package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.config.NexusProperties;
import dev.nexus.core.web.ClientIpResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private static final String PEER = "10.0.0.1";

    private static ClientIpResolver resolverWith(int trustedProxyCount) {
        return new ClientIpResolver(new NexusProperties(
                new NexusProperties.Jwt("a-signing-key-long-enough-for-hs256-0123", 15, 30),
                new NexusProperties.Security(false, List.of(), "http://localhost:5173", true, trustedProxyCount),
                new NexusProperties.RateLimit(10, 30, 3)));
    }

    private static MockHttpServletRequest request(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(PEER);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void readsThePeerWhenNothingIsInFront() {
        assertThat(resolverWith(0).resolve(request("203.0.113.9"))).isEqualTo(PEER);
    }

    @Test
    void readsThePeerWhenNothingWasForwarded() {
        assertThat(resolverWith(1).resolve(request(null))).isEqualTo(PEER);
    }

    @Test
    void readsTheAddressTheNearestProxyAdded() {
        assertThat(resolverWith(1).resolve(request("203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    /** The reason the header is read from the right: the head of the list is the caller's to write. */
    @Test
    void ignoresAnAddressTheCallerForged() {
        assertThat(resolverWith(1).resolve(request("1.2.3.4, 203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    /** A CDN appends the caller, then the platform appends the CDN: the caller is second from last. */
    @Test
    void countsInPastEveryProxyInFront() {
        assertThat(resolverWith(2).resolve(request("1.2.3.4, 203.0.113.9, 172.16.0.5")))
                .isEqualTo("203.0.113.9");
    }

    /** A chain shorter than the configured one is not this deployment; nothing in it is trusted. */
    @Test
    void fallsBackToThePeerWhenTheChainIsTooShort() {
        assertThat(resolverWith(2).resolve(request("203.0.113.9"))).isEqualTo(PEER);
    }
}
