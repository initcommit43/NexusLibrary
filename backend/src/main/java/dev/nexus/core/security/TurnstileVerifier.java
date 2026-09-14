package dev.nexus.core.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks Cloudflare whether a token one of the public forms sent back was really issued to a
 * browser that solved the challenge. Tokens are single-use and short-lived, so a replayed or
 * stale one is rejected by Cloudflare rather than by anything here.
 */
@Component
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);

    private final RestClient restClient;
    private final TurnstileProperties properties;
    private final boolean configured;

    public TurnstileVerifier(RestClient.Builder builder, TurnstileProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
        this.configured = StringUtils.hasText(properties.secretKey());

        if (!configured) {
            log.warn("TURNSTILE_SECRET_KEY is not set, so sign-up and password-reset requests are "
                    + "not checked for automation. Set it on any deployment reachable from the internet.");
        }
    }

    /**
     * @throws BotCheckFailedException if the token is missing or Cloudflare rejects it — and
     *     equally if Cloudflare cannot be reached at all. Failing closed costs sign-ups while
     *     Cloudflare is down; failing open would hand the bypass to anyone able to block a
     *     single outbound call, which is the cheaper attack of the two.
     */
    public void verify(String token, String clientIp) {
        if (!configured) {
            return;
        }
        if (!StringUtils.hasText(token)) {
            throw new BotCheckFailedException();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.secretKey());
        form.add("response", token);
        // Cloudflare scores the address the challenge was solved from against the one asking.
        form.add("remoteip", clientIp);

        Map<?, ?> body;
        try {
            body = restClient
                    .post()
                    .uri(properties.verifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            // The address and the token are both left out on purpose: one is personal data,
            // the other is a live credential until it is spent.
            log.warn("Could not reach Turnstile to verify a token; refusing the request", e);
            throw new BotCheckFailedException();
        }

        if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
            log.warn("Turnstile rejected a token: {}", errorCodes(body));
            throw new BotCheckFailedException();
        }
    }

    /** Cloudflare's own reason, which is safe to log — it names the fault, not the caller. */
    private Object errorCodes(Map<?, ?> body) {
        return body == null ? "no response body" : new LinkedHashMap<>(body).get("error-codes");
    }
}
