package dev.nexus.modules.film;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.CanonicalRef;
import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Trusted before clever: the id Simkl handed over, then TMDB's IMDb index for the rest. */
class SimklToTmdbResolverTest {

    private TmdbClient client;
    private SimklToTmdbResolver resolver;

    @BeforeEach
    void setUp() {
        client = mock(TmdbClient.class);
        resolver = new SimklToTmdbResolver(client);
    }

    private ExternalItemRef ref(String simklId, String kind, String tmdbId, String imdbId) {
        Map<String, String> hints = new LinkedHashMap<>();
        hints.put(SimklToTmdbResolver.KIND_HINT, kind);
        if (tmdbId != null) {
            hints.put(SimklToTmdbResolver.TMDB_ID_HINT, tmdbId);
        }
        if (imdbId != null) {
            hints.put(SimklToTmdbResolver.IMDB_ID_HINT, imdbId);
        }
        return new ExternalItemRef(Provider.SIMKL, kind + ":" + simklId, "A Title", Map.copyOf(hints));
    }

    @Test
    void resolvesOntoThePrefixedTmdbId() {
        ExternalItemRef movie = ref("12", "movie", "550", "tt0137523");

        Map<ExternalItemRef, CanonicalRef> resolved = resolver.resolveAll(List.of(movie));

        assertThat(resolved).containsEntry(movie, new CanonicalRef(Source.TMDB, "movie:550"));
    }

    /** The hint is free; spending a request when one was already given would be the waste. */
    @Test
    void spendsNoRequestWhenTheTmdbIdIsAlreadyThere() {
        resolver.resolveAll(List.of(ref("12", "movie", "550", "tt0137523")));

        verify(client, never()).findIdByImdbId(any(), anyString());
    }

    /** The collision guard has to survive the round trip through Simkl, not just TMDB. */
    @Test
    void aFilmAndAShowOfTheSameTmdbNumberResolveApart() {
        ExternalItemRef movie = ref("12", "movie", "550", null);
        ExternalItemRef show = ref("34", "tv", "550", null);

        Map<ExternalItemRef, CanonicalRef> resolved = resolver.resolveAll(List.of(movie, show));

        assertThat(resolved.get(movie).externalId()).isEqualTo("movie:550");
        assertThat(resolved.get(show).externalId()).isEqualTo("tv:550");
    }

    /** TMDB indexes IMDb ids, so a title Simkl only knew by IMDb still lands on a canonical. */
    @Test
    void fallsBackToTmdbsImdbIndex() {
        when(client.findIdByImdbId(TmdbKind.SHOW, "tt0903747")).thenReturn(Optional.of("1396"));
        ExternalItemRef show = ref("34", "tv", null, "tt0903747");

        Map<ExternalItemRef, CanonicalRef> resolved = resolver.resolveAll(List.of(show));

        assertThat(resolved).containsEntry(show, new CanonicalRef(Source.TMDB, "tv:1396"));
        verify(client).findIdByImdbId(TmdbKind.SHOW, "tt0903747");
    }

    /** Neither id means no canonical: it goes to the unmatched report rather than a guess. */
    @Test
    void aTitleWithNeitherIdDoesNotResolve() {
        ExternalItemRef unknown = ref("99", "movie", null, null);

        assertThat(resolver.resolveAll(List.of(unknown))).isEmpty();
    }

    /** TMDB not knowing the IMDb id either is an unmatched title, not a failed import. */
    @Test
    void anImdbIdTmdbCannotPlaceDoesNotResolve() {
        when(client.findIdByImdbId(TmdbKind.MOVIE, "tt9999999")).thenReturn(Optional.empty());

        assertThat(resolver.resolveAll(List.of(ref("99", "movie", null, "tt9999999")))).isEmpty();
    }

    @Test
    void resolvesForSimklAndNothingElse() {
        assertThat(resolver.provider()).isEqualTo(Provider.SIMKL);
    }
}
