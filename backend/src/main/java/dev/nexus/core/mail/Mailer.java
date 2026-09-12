package dev.nexus.core.mail;

/**
 * How a transactional mail leaves this application.
 *
 * <p>Deliberately narrow: an address, a subject, a body. Everything about why the mail is
 * being sent stays with the flow that sends it, so a second sender is one class rather than a
 * rewrite, and no caller has to know whether it left over an API or a socket.
 */
public interface Mailer {

    /**
     * @param to a single recipient. There is no bulk form on purpose — every mail this app
     *     sends is addressed to one person about their own account, and a list parameter is an
     *     invitation to write the one that is not.
     * @throws MailNotSentException when the mail could not be handed over. Callers decide what
     *     that means; for a verification link it is worth telling the reader about, because the
     *     alternative is a page saying "check your inbox" for a mail that does not exist.
     */
    void send(String to, String subject, String html);
}
