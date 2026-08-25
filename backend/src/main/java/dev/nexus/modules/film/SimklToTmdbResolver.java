package dev.nexus.modules.film;

import dev.nexus.core.adapter.CanonicalRef;
import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ItemResolver;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps Simkl entries onto their TMDB canonicals.
 *
 * <p>Two passes, in the order the MyAnimeList resolver established — trusted before clever,
 * cheap before expensive. First the id Simkl already handed over: it records the TMDB id of
 * what it knows, and the import adapter carries it along as a hint, so most of a library
 * resolves without a single request. Only what the hint missed falls back to TMDB's
 * {@code /find}, which costs one call per title and is why it is a fallback rather than the
 * main path.
 *
 * <p>What survives neither lands in the unmatched report, which is the same escape hatch
 * every other provider gets rather than a special case.
 */
@Component
public class SimklToTmdbResolver implements ItemResolver {

    /** Hint keys, named here because this is the class that reads them. */
    public static final String TMDB_ID_HINT = "tmdbId";

    public static final String IMDB_ID_HINT = "imdbId";

    public static final String KIND_HINT = "tmdbKind";

    private static final Logger log = LoggerFactory.getLogger(SimklToTmdbResolver.class);

    private final TmdbClient client;

    public SimklToTmdbResolver(TmdbClient client) {
        this.client = client;
    }

    @Override
    public Provider provider() {
        return Provider.SIMKL;
    }

    @Override
    public Map<ExternalItemRef, CanonicalRef> resolveAll(Collection<ExternalItemRef> refs) {
        Map<ExternalItemRef, CanonicalRef> resolved = new HashMap<>();
        List<ExternalItemRef> unresolved = new ArrayList<>();

        for (ExternalItemRef ref : refs) {
            Optional<CanonicalRef> canonical = fromTmdbHint(ref);
            if (canonical.isPresent()) {
                resolved.put(ref, canonical.get());
            } else {
                unresolved.add(ref);
            }
        }

        if (unresolved.isEmpty()) {
            return resolved;
        }

        log.debug("Simkl join resolved {} of {}; trying IMDb ids for the rest", resolved.size(), refs.size());
        for (ExternalItemRef ref : unresolved) {
            fromImdbId(ref).ifPresent(canonical -> resolved.put(ref, canonical));
        }
        return resolved;
    }

    /** The free pass: an id that was already in Simkl's own response. */
    private Optional<CanonicalRef> fromTmdbHint(ExternalItemRef ref) {
        String tmdbId = ref.hints().get(TMDB_ID_HINT);
        return tmdbId == null ? Optional.empty() : kindOf(ref).map(kind -> canonical(kind, tmdbId));
    }

    /** The paid one: TMDB indexes IMDb ids, so a title Simkl only knew by IMDb still lands. */
    private Optional<CanonicalRef> fromImdbId(ExternalItemRef ref) {
        String imdbId = ref.hints().get(IMDB_ID_HINT);
        if (imdbId == null) {
            return Optional.empty();
        }
        return kindOf(ref).flatMap(kind -> client.findIdByImdbId(kind, imdbId).map(tmdbId -> canonical(kind, tmdbId)));
    }

    /**
     * Back through the same prefix the catalogue side stores, so a film and a show of the
     * same TMDB number stay two different items.
     */
    private CanonicalRef canonical(TmdbKind kind, String tmdbId) {
        return new CanonicalRef(Source.TMDB, kind.externalId(tmdbId));
    }

    private Optional<TmdbKind> kindOf(ExternalItemRef ref) {
        String kindPath = ref.hints().get(KIND_HINT);
        return kindPath == null ? Optional.empty() : TmdbKind.ofPath(kindPath);
    }
}
