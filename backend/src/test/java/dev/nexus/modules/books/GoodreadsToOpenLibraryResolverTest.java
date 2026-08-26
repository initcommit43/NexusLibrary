package dev.nexus.modules.books;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.CanonicalRef;
import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The three passes that place a Goodreads row, and what each of them refuses to do. */
class GoodreadsToOpenLibraryResolverTest {

    private OpenLibraryClient client;
    private GoodreadsToOpenLibraryResolver resolver;

    @BeforeEach
    void setUp() {
        client = mock(OpenLibraryClient.class);
        resolver = new GoodreadsToOpenLibraryResolver(client);
    }

    private ExternalItemRef ref(String title, Map<String, String> hints) {
        return new ExternalItemRef(Provider.GOODREADS, "row", title, hints);
    }

    private Map<String, Object> work(String key, String title, String... authors) {
        return Map.of("key", key, "title", title, "author_name", List.of(authors));
    }

    /** The pass that makes this module cheap: an id match, with nothing to be wrong about. */
    @Test
    void resolvesOnTheGoodreadsIdWithoutTouchingAnythingElse() {
        when(client.findByGoodreadsId("104")).thenReturn(Optional.of(work("/works/OL893414W", "Dune")));

        Map<ExternalItemRef, CanonicalRef> resolved = resolver.resolveAll(List.of(ref(
                "Dune",
                Map.of(
                        GoodreadsToOpenLibraryResolver.GOODREADS_ID_HINT, "104",
                        GoodreadsToOpenLibraryResolver.ISBN13_HINT, "9780441013593"))));

        assertThat(resolved).containsValue(new CanonicalRef(Source.OPEN_LIBRARY, "OL893414W"));
        verify(client, never()).findByIsbn(anyString());
        verify(client, never()).findByTitleAndAuthor(anyString(), anyString(), anyInt());
    }

    @Test
    void fallsBackToTheIsbnWhenTheGoodreadsIdIsUnknownToOpenLibrary() {
        when(client.findByGoodreadsId("104")).thenReturn(Optional.empty());
        when(client.findByIsbn("9780441013593")).thenReturn(Optional.of(work("/works/OL893414W", "Dune")));

        Map<ExternalItemRef, CanonicalRef> resolved = resolver.resolveAll(List.of(ref(
                "Dune",
                Map.of(
                        GoodreadsToOpenLibraryResolver.GOODREADS_ID_HINT, "104",
                        GoodreadsToOpenLibraryResolver.ISBN13_HINT, "9780441013593"))));

        assertThat(resolved).containsValue(new CanonicalRef(Source.OPEN_LIBRARY, "OL893414W"));
    }

    /** ISBN-10 is only asked for by a row that has no ISBN-13; both name the same edition. */
    @Test
    void prefersTheThirteenDigitIsbn() {
        when(client.findByIsbn("9780441013593")).thenReturn(Optional.of(work("/works/OL893414W", "Dune")));

        resolver.resolveAll(List.of(ref(
                "Dune",
                Map.of(
                        GoodreadsToOpenLibraryResolver.ISBN13_HINT, "9780441013593",
                        GoodreadsToOpenLibraryResolver.ISBN10_HINT, "0441013597"))));

        verify(client).findByIsbn("9780441013593");
        verify(client, never()).findByIsbn("0441013597");
    }

    @Test
    void fallsBackToATitleSearchForARowWithNoIdAtAll() {
        when(client.findByTitleAndAuthor("Dune", "Frank Herbert", 5))
                .thenReturn(List.of(work("/works/OL893414W", "Dune", "Frank Herbert")));

        Map<ExternalItemRef, CanonicalRef> resolved = resolver.resolveAll(
                List.of(ref("Dune", Map.of(GoodreadsToOpenLibraryResolver.AUTHOR_HINT, "Frank Herbert"))));

        assertThat(resolved).containsValue(new CanonicalRef(Source.OPEN_LIBRARY, "OL893414W"));
    }

    /**
     * A search for a well-known title returns its sequels and its study guides first. Taking the
     * top hit on trust would file the wrong book under the reader's rating.
     */
    @Test
    void skipsATitleThatOnlyLooksLikeTheOneAskedFor() {
        when(client.findByTitleAndAuthor("Dune", "Frank Herbert", 5))
                .thenReturn(List.of(
                        work("/works/OL9W", "Dune Messiah", "Frank Herbert"),
                        work("/works/OL8W", "A Study Guide to Dune", "Some Academic"),
                        work("/works/OL893414W", "Dune", "Frank Herbert")));

        Map<ExternalItemRef, CanonicalRef> resolved = resolver.resolveAll(
                List.of(ref("Dune", Map.of(GoodreadsToOpenLibraryResolver.AUTHOR_HINT, "Frank Herbert"))));

        assertThat(resolved).containsValue(new CanonicalRef(Source.OPEN_LIBRARY, "OL893414W"));
    }

    /**
     * Goodreads writes the series inline in the title, which no catalogue does. Left in, it
     * would stop a first volume matching anything at all.
     */
    @Test
    void ignoresTheSeriesGoodreadsWritesIntoTheTitle() {
        when(client.findByTitleAndAuthor("Dune (Dune Chronicles, #1)", "Frank Herbert", 5))
                .thenReturn(List.of(work("/works/OL893414W", "Dune", "Frank Herbert")));

        Map<ExternalItemRef, CanonicalRef> resolved = resolver.resolveAll(List.of(ref(
                "Dune (Dune Chronicles, #1)", Map.of(GoodreadsToOpenLibraryResolver.AUTHOR_HINT, "Frank Herbert"))));

        assertThat(resolved).containsValue(new CanonicalRef(Source.OPEN_LIBRARY, "OL893414W"));
    }

    /** Same book, differently punctuated name — the reason punctuation goes before comparing. */
    @Test
    void acceptsAnAuthorWhoseInitialsArePunctuatedDifferently() {
        when(client.findByTitleAndAuthor("The Hobbit", "J.R.R. Tolkien", 5))
                .thenReturn(List.of(work("/works/OL262758W", "The Hobbit", "J. R. R. Tolkien")));

        Map<ExternalItemRef, CanonicalRef> resolved = resolver.resolveAll(
                List.of(ref("The Hobbit", Map.of(GoodreadsToOpenLibraryResolver.AUTHOR_HINT, "J.R.R. Tolkien"))));

        assertThat(resolved).containsValue(new CanonicalRef(Source.OPEN_LIBRARY, "OL262758W"));
    }

    /** An export that omitted the subtitle still names the same work. */
    @Test
    void acceptsATitleThatIsAPrefixOfTheCataloguedOne() {
        when(client.findByTitleAndAuthor("The Fellowship of the Ring", null, 5))
                .thenReturn(List.of(work("/works/OL27479W", "The Fellowship of the Ring: Being the First Part")));

        assertThat(resolver.resolveAll(List.of(ref("The Fellowship of the Ring", Map.of()))))
                .containsValue(new CanonicalRef(Source.OPEN_LIBRARY, "OL27479W"));
    }

    @Test
    void refusesACandidateByADifferentAuthor() {
        when(client.findByTitleAndAuthor("Dune", "Frank Herbert", 5))
                .thenReturn(List.of(work("/works/OL9W", "Dune", "Someone Else")));

        assertThat(resolver.resolveAll(
                        List.of(ref("Dune", Map.of(GoodreadsToOpenLibraryResolver.AUTHOR_HINT, "Frank Herbert")))))
                .isEmpty();
    }

    /** What resolves nowhere is left out, so it lands in the unmatched report the reader sees. */
    @Test
    void leavesAnUnplaceableRowOutRatherThanGuessing() {
        when(client.findByGoodreadsId(anyString())).thenReturn(Optional.empty());
        when(client.findByTitleAndAuthor(anyString(), anyString(), anyInt())).thenReturn(List.of());

        assertThat(resolver.resolveAll(List.of(ref(
                        "Some Obscure Book",
                        Map.of(
                                GoodreadsToOpenLibraryResolver.GOODREADS_ID_HINT, "999",
                                GoodreadsToOpenLibraryResolver.AUTHOR_HINT, "Nobody")))))
                .isEmpty();
    }
}
