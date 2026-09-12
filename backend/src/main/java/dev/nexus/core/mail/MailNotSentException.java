package dev.nexus.core.mail;

/**
 * The mail could not be handed to whatever sends it.
 *
 * <p>Says nothing about the recipient. A provider being down, a key being wrong and a domain
 * not being verified all land here, and none of them is the reader's fault or their business.
 */
public class MailNotSentException extends RuntimeException {

    public MailNotSentException(String message, Throwable cause) {
        super(message, cause);
    }

    public MailNotSentException(String message) {
        super(message);
    }
}
