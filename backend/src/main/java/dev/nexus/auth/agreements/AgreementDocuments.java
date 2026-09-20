package dev.nexus.auth.agreements;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The text of each document, read once at boot from {@code resources/agreements}.
 *
 * <p>Markdown rather than HTML, and served rather than compiled into either client. A native
 * build that ships its legal text inside the binary cannot have that text revised without a
 * new release and a review, and in the meantime it would show an old text beside the new
 * version string it is being asked to accept — which is the one thing a record of consent
 * must never do.
 *
 * <p>Serving it also collapses the two copies the project used to keep: the words lived in
 * four React pages and the version lived in {@code application.yml}, in different languages,
 * kept in step by a comment asking for both to be changed together. Now the pages render this,
 * and {@code AgreementDocumentsTest} refuses a build where a document's own "last updated"
 * date and its configured version disagree.
 */
@Component
public class AgreementDocuments {

    private static final String FRONT_MATTER = "---";

    /** What one file holds: its own heading and date, and the body beneath them. */
    public record Document(String title, String updated, String markdown) {}

    private final Map<Agreement, Document> documents = new EnumMap<>(Agreement.class);

    public AgreementDocuments() {
        for (Agreement agreement : Agreement.values()) {
            documents.put(agreement, read(agreement));
        }
    }

    public Document of(Agreement agreement) {
        return documents.get(agreement);
    }

    /**
     * A missing or malformed file fails the boot rather than serving an empty document. A
     * sign-up form showing a blank policy is worse than one that does not load: the reader
     * accepts it either way.
     */
    private static Document read(Agreement agreement) {
        String fileName = "agreements/" + agreement.name().toLowerCase(Locale.ROOT) + ".md";

        String raw;
        try {
            raw = new ClassPathResource(fileName).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No text for agreement " + agreement + " at " + fileName, e);
        }

        if (!raw.startsWith(FRONT_MATTER)) {
            throw new IllegalStateException(fileName + " must open with a --- front matter block");
        }

        int end = raw.indexOf(FRONT_MATTER, FRONT_MATTER.length());
        if (end < 0) {
            throw new IllegalStateException(fileName + " has an unterminated front matter block");
        }

        Map<String, String> header = headerOf(raw.substring(FRONT_MATTER.length(), end), fileName);
        return new Document(
                required(header, "title", fileName),
                required(header, "updated", fileName),
                raw.substring(end + FRONT_MATTER.length()).strip());
    }

    private static Map<String, String> headerOf(String block, String fileName) {
        Map<String, String> header = new java.util.LinkedHashMap<>();
        for (String line : block.strip().lines().toList()) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IllegalStateException(fileName + " has a front matter line that is not key: value");
            }
            header.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
        }
        return header;
    }

    private static String required(Map<String, String> header, String key, String fileName) {
        String value = header.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fileName + " front matter has no " + key);
        }
        return value;
    }
}
