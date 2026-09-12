package dev.nexus.auth;

import dev.nexus.config.NexusProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Whether an address may open an account.
 *
 * <p>There is no confirmation mail to lean on — nothing here sends any — so this is the only
 * moment the address is ever examined. That makes it worth more than {@code @Email}, which
 * accepts {@code a@b} and anything else with an at-sign in it.
 *
 * <p>Three questions, cheapest first: is it shaped like an address, is the domain a throwaway
 * service, and does the domain publish any way to receive mail at all. The third is what
 * separates a typo or an invented domain from a real inbox, and it is the only one that
 * touches the network.
 */
@Component
public class EmailPolicy {

    private static final Logger log = LoggerFactory.getLogger(EmailPolicy.class);

    /**
     * Stricter than {@code @Email} on purpose: a dotted domain whose last label is alphabetic
     * and at least two characters. That is the part {@code @Email} leaves out, and the part a
     * made-up address usually fails.
     */
    private static final Pattern SHAPE = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+@"
                    + "(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+"
                    + "[A-Za-z]{2,63}$");

    private static final String BLOCKLIST = "disposable-email-domains.txt";

    private final NexusProperties.Registration settings;
    private final Set<String> disposable;

    public EmailPolicy(NexusProperties properties) {
        this.settings = properties.registration();
        this.disposable = loadBlocklist();
    }

    /**
     * @throws EmailNotAcceptableException with a message the reader can act on. Each says which
     *     of the three it failed, because "invalid email" in front of an address someone knows
     *     is theirs reads as the form being broken.
     */
    public void check(String email) {
        String normalised = EmailAddresses.normalise(email);

        if (!SHAPE.matcher(normalised).matches()) {
            throw new EmailNotAcceptableException("That does not look like an email address.");
        }

        String domain = normalised.substring(normalised.lastIndexOf('@') + 1);

        if (settings.blockDisposableDomains() && isDisposable(domain)) {
            throw new EmailNotAcceptableException(
                    "Please use a permanent email address rather than a temporary one.");
        }

        if (settings.requireDeliverableDomain() && !acceptsMail(domain)) {
            throw new EmailNotAcceptableException("That email domain does not accept mail.");
        }
    }

    /**
     * Subdomains count: a service handing out {@code inbox.example-temp.com} is the same
     * service as {@code example-temp.com}, and listing every subdomain is a losing race.
     */
    private boolean isDisposable(String domain) {
        for (int at = 0; at != -1; at = domain.indexOf('.', at + 1)) {
            if (disposable.contains(domain.substring(at == 0 ? 0 : at + 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the domain publishes anywhere to deliver mail.
     *
     * <p>MX first, then A: a domain with no MX but an address record still accepts mail there
     * by the fallback every mail server implements, and rejecting those would turn away small
     * self-hosted domains that work perfectly well.
     *
     * <p>Fails open. A resolver that is slow, blocked, or absent is a fact about this server,
     * not about the reader's address, and the cost of being wrong in that direction is one
     * junk account rather than a stranger who cannot sign up and cannot find out why.
     */
    private boolean acceptsMail(String domain) {
        ExecutorService lookup = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "email-mx-lookup");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<Boolean> answer = lookup.submit(resolves(domain));
            return answer.get(settings.deliverabilityTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Deliberately not the address in the log: it is a person's, and it is unverified.
            log.warn("Could not check deliverability for a sign-up domain; allowing it through", e);
            return true;
        } finally {
            lookup.shutdownNow();
        }
    }

    private Callable<Boolean> resolves(String domain) {
        return () -> {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            InitialDirContext dns = new InitialDirContext(env);
            try {
                Attributes records = dns.getAttributes(domain, new String[] {"MX", "A"});
                return records.get("MX") != null || records.get("A") != null;
            } catch (NamingException notFound) {
                // A definite answer: the domain publishes nothing. Not a failure to reach DNS.
                return false;
            } finally {
                dns.close();
            }
        };
    }

    private Set<String> loadBlocklist() {
        Set<String> domains = new HashSet<>();
        try (InputStream source = new ClassPathResource(BLOCKLIST).getInputStream();
                BufferedReader lines =
                        new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                String domain = line.trim().toLowerCase(Locale.ROOT);
                if (!domain.isEmpty() && !domain.startsWith("#")) {
                    domains.add(domain);
                }
            }
        } catch (IOException e) {
            // Not fatal: the shape and deliverability checks still stand, and refusing to boot
            // over a missing text file would take the whole app down to stop throwaway inboxes.
            log.error("Could not read {}; disposable addresses will not be blocked", BLOCKLIST, e);
        }
        return Set.copyOf(domains);
    }
}
