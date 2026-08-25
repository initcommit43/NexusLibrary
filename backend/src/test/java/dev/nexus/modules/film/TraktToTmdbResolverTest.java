package dev.nexus.modules.film;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.core.adapter.CanonicalRef;
import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The whole join, which Trakt hands over for free: a TMDB id already in its own response. */
class TraktToTmdbResolverTest {

    private final TraktToTmdbResolver resolver = new TraktToTmdbResolver();

    private ExternalItemRef ref(String traktId, String tmdbId, String kind) {
        Map<String, String> hints = tmdbId == null
                ? Map.of()
                : Map.of(TraktToTmdbResolver.TMDB_ID_HINT, tmdbId, TraktToTmdbResolver.KIND_HINT, kind);
        return new ExternalItemRef(Provider.TRAKT, kind + ":" + traktId, "A Title", hints);
    }

    @Test
    void resolvesOntoThePrefixedTmdbId() {
        ExternalItemRef movie = ref("1", "550", "movie");

        Map<ExternalItemRef, CanonicalRef> resolved = resolver.resolveAll(List.of(movie));

        assertThat(resolved).containsEntry(movie, new CanonicalRef(Source.TMDB, "movie:550"));
    }

    /** The collision guard has to survive the round trip through Trakt, not just TMDB. */
    @Test
    void aFilmAndAShowOfTheSameTmdbNumberResolveApart() {
        ExternalItemRef movie = ref("1", "550", "movie");
        ExternalItemRef show = ref("2", "550", "tv");

        Map<ExternalItemRef, CanonicalRef> resolved = resolver.resolveAll(List.of(movie, show));

        assertThat(resolved.get(movie).externalId()).isEqualTo("movie:550");
        assertThat(resolved.get(show).externalId()).isEqualTo("tv:550");
    }

    /** No TMDB id means no canonical: it goes to the unmatched report rather than a guess. */
    @Test
    void aTitleWithoutATmdbIdDoesNotResolve() {
        ExternalItemRef unknown = ref("3", null, "movie");

        assertThat(resolver.resolveAll(List.of(unknown))).isEmpty();
    }

    @Test
    void resolvesForTraktAndNothingElse() {
        assertThat(resolver.provider()).isEqualTo(Provider.TRAKT);
    }
}
