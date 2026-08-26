package dev.nexus.modules.books;

import dev.nexus.core.adapter.CanonicalRef;
import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ItemResolver;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps Goodreads rows onto their Open Library canonicals.
 *
 * <p>Three passes, in the order the other resolvers established — trusted before clever. The
 * first is the one that makes this module cheaper than the film one: Open Library records the
 * Goodreads id of editions catalogued from Goodreads data, and a Goodreads export's first
 * column is exactly that id. Where it lands, the match is by identity and there is nothing to
 * be wrong about.
 *
 * <p>The ISBN pass behind it is equally exact but narrower, since Goodreads leaves the ISBN
 * blank for ebooks and hand-catalogued editions. Only a row that has neither reaches the title
 * search, which is a guess and is treated as one.
 */
@Component
public class GoodreadsToOpenLibraryResolver implements ItemResolver {

    /** Hint keys, named here because this is the class that reads them. */
    public static final String GOODREADS_ID_HINT = "goodreadsId";

    public static final String ISBN13_HINT = "isbn13";

    public static final String ISBN10_HINT = "isbn10";

    public static final String AUTHOR_HINT = "author";

    private static final Logger log = LoggerFactory.getLogger(GoodreadsToOpenLibraryResolver.class);

    /** How far down a title search to look before calling the row unmatched. */
    private static final int TITLE_CANDIDATES = 5;

    private final OpenLibraryClient client;

    public GoodreadsToOpenLibraryResolver(OpenLibraryClient client) {
        this.client = client;
    }

    @Override
    public Provider provider() {
        return Provider.GOODREADS;
    }

    @Override
    public Map<ExternalItemRef, CanonicalRef> resolveAll(Collection<ExternalItemRef> refs) {
        Map<ExternalItemRef, CanonicalRef> resolved = new HashMap<>();
        int byGoodreadsId = 0;
        int byIsbn = 0;

        for (ExternalItemRef ref : refs) {
            Optional<CanonicalRef> canonical = fromGoodreadsId(ref);
            if (canonical.isPresent()) {
                byGoodreadsId++;
            } else {
                canonical = fromIsbn(ref);
                if (canonical.isPresent()) {
                    byIsbn++;
                } else {
                    canonical = fromTitleAndAuthor(ref);
                }
            }
            canonical.ifPresent(found -> resolved.put(ref, found));
        }

        log.debug(
                "Goodreads resolved {} of {}: {} on a Goodreads id, {} on an ISBN, {} on a title search",
                resolved.size(),
                refs.size(),
                byGoodreadsId,
                byIsbn,
                resolved.size() - byGoodreadsId - byIsbn);
        return resolved;
    }

    /** The identity pass: Open Library indexes the Goodreads id the export is keyed by. */
    private Optional<CanonicalRef> fromGoodreadsId(ExternalItemRef ref) {
        String goodreadsId = ref.hints().get(GOODREADS_ID_HINT);
        return goodreadsId == null
                ? Optional.empty()
                : client.findByGoodreadsId(goodreadsId).map(this::canonical).filter(Objects::nonNull);
    }

    /** The exact pass. ISBN-13 where the row has one, ISBN-10 only where it does not. */
    private Optional<CanonicalRef> fromIsbn(ExternalItemRef ref) {
        String isbn = ref.hints().getOrDefault(ISBN13_HINT, ref.hints().get(ISBN10_HINT));
        return isbn == null
                ? Optional.empty()
                : client.findByIsbn(isbn).map(this::canonical).filter(Objects::nonNull);
    }

    /**
     * The guess. A search for a well-known title returns its sequels, its companions and its
     * study guides alongside it, so the top hit is not taken on trust: a candidate has to agree
     * on the title and, where the export named one, on the author. A row that agrees with
     * nothing in the first few results lands in the unmatched report, which is the honest
     * outcome and the one the reader can act on.
     */
    private Optional<CanonicalRef> fromTitleAndAuthor(ExternalItemRef ref) {
        String title = ref.title();
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String author = ref.hints().get(AUTHOR_HINT);

        return client.findByTitleAndAuthor(title, author, TITLE_CANDIDATES).stream()
                .filter(doc -> titleAgrees(doc, title) && authorAgrees(doc, author))
                .findFirst()
                .map(this::canonical);
    }

    /**
     * Both titles are cut back to their main part and then have to match exactly.
     *
     * <p>A prefix match will not do here, however tempting: "Dune" is a prefix of "Dune
     * Messiah", so every first volume in a series would resolve onto its own sequel. Cutting
     * the subtitle and the series parenthetical off both sides instead lets an export that
     * wrote "The Fellowship of the Ring" still match "The Fellowship of the Ring: Being the
     * First Part", while leaving a genuinely different title genuinely different.
     */
    private boolean titleAgrees(Map<String, Object> doc, String title) {
        String wanted = mainTitle(title);
        String found = mainTitle(String.valueOf(doc.get("title")));
        return !wanted.isBlank() && wanted.equals(found);
    }

    /**
     * The title without its subtitle or its series note. Goodreads writes the series inline —
     * "Dune (Dune Chronicles, #1)" — and catalogues split subtitles after a colon, so the two
     * spellings of one book rarely agree until both are cut back.
     */
    private String mainTitle(String title) {
        if (title == null) {
            return "";
        }
        String main = title.split(":")[0].replaceAll("\\([^)]*\\)", "");
        return normalise(main);
    }

    /**
     * Absent on either side, this decides nothing and the title stands alone. Where both name
     * an author they have to be the same person — "J.R.R. Tolkien" and "J. R. R. Tolkien"
     * normalise to one string, which is the whole reason punctuation goes first.
     */
    private boolean authorAgrees(Map<String, Object> doc, String author) {
        if (author == null || author.isBlank()) {
            return true;
        }
        List<String> authors = doc.get("author_name") instanceof List<?> entries
                ? entries.stream().filter(Objects::nonNull).map(Object::toString).toList()
                : List.of();
        if (authors.isEmpty()) {
            return true;
        }
        String wanted = normalise(author);
        return authors.stream()
                .map(this::normalise)
                .anyMatch(found -> found.equals(wanted) || found.contains(wanted) || wanted.contains(found));
    }

    /** Open Library keys a work as {@code /works/OL893414W}; only the id is stored. */
    private CanonicalRef canonical(Map<String, Object> doc) {
        Object key = doc.get("key");
        if (key == null) {
            return null;
        }
        String text = key.toString();
        String workId = text.startsWith("/works/") ? text.substring("/works/".length()) : text;
        return workId.isBlank() ? null : new CanonicalRef(Source.OPEN_LIBRARY, workId);
    }

    /** Letters and digits only, lowercased — the same rule the CSV headers go through. */
    private String normalise(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
