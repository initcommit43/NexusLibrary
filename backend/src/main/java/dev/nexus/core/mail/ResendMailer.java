package dev.nexus.core.mail;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends through Resend's HTTP API.
 *
 * <p>Only exists when a non-blank key is configured; see {@link ResendKeyConfigured} for why
 * presence alone is not enough. A deployment without one gets {@link LoggingMailer}
 * in dev and test, and nothing at all in production — which is what makes an unsendable mail
 * an error the caller can report rather than a silent success.
 *
 * <p><strong>A Resend account with no verified domain can only deliver to the address that
 * owns it.</strong> Mail to anyone else is accepted by the API and then dropped, so the call
 * succeeds and nothing arrives. That is a property of the account rather than of this code,
 * and it is why verification can be switched off until a domain is in place.
 */
@Component
@Conditional(ResendKeyConfigured.class)
public class ResendMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(ResendMailer.class);
    private static final String ENDPOINT = "https://api.resend.com/emails";

    private final RestClient http;
    private final String from;

    public ResendMailer(
            RestClient.Builder builder,
            @org.springframework.beans.factory.annotation.Value("${nexus.mail.resend.api-key}") String apiKey,
            @org.springframework.beans.factory.annotation.Value("${nexus.mail.from}") String from) {
        this.from = from;
        this.http = builder.baseUrl(ENDPOINT)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public void send(String to, String subject, String html) {
        try {
            http.post()
                    .body(Map.of("from", from, "to", List.of(to), "subject", subject, "html", html))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Neither the address nor the body reaches the log: one is a person's, the other
            // carries the link. The provider's own dashboard is where a failed send is read.
            log.warn("Resend refused a message", e);
            throw new MailNotSentException("Could not send the message.", e);
        }
    }
}
