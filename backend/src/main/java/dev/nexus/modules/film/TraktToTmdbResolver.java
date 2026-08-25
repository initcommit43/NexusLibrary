package dev.nexus.modules.film;

import dev.nexus.core.adapter.CanonicalRef;
import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ItemResolver;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps Trakt entries onto their TMDB canonicals — the easiest resolver in the codebase, and
 * deliberately so.
 *
 * <p>Trakt records the TMDB id of everything it knows, and the import adapter carries it
 * along as a hint, so there is nothing left to look up: no request, no batching, no title
 * matching. Where the MyAnimeList resolver needs a join and then a fuzzy fallback, this one
 * reads an id that was already in the response.
 *
 * <p>What does not resolve is a Trakt title with no TMDB id at all — rare, and usually
 * something newly added to Trakt. Those land in the unmatched report, which is the same
 * escape hatch every other provider gets rather than a special case.
 */
@Component
public class TraktToTmdbResolver implements ItemResolver {

    /** Hint keys, named here because this is the class that reads them. */
    public static final String TMDB_ID_HINT = "tmdbId";

    public static final String KIND_HINT = "tmdbKind";

    private static final Logger log = LoggerFactory.getLogger(TraktToTmdbResolver.class);

    @Override
    public Provider provider() {
        return Provider.TRAKT;
    }

    @Override
    public Map<ExternalItemRef, CanonicalRef> resolveAll(Collection<ExternalItemRef> refs) {
        Map<ExternalItemRef, CanonicalRef> resolved = new HashMap<>();

        for (ExternalItemRef ref : refs) {
            canonical(ref).ifPresent(canonical -> resolved.put(ref, canonical));
        }

        if (resolved.size() < refs.size()) {
            log.debug("Trakt join resolved {} of {}; the rest carry no TMDB id", resolved.size(), refs.size());
        }
        return resolved;
    }

    private Optional<CanonicalRef> canonical(ExternalItemRef ref) {
        String tmdbId = ref.hints().get(TMDB_ID_HINT);
        String kindPath = ref.hints().get(KIND_HINT);
        if (tmdbId == null || kindPath == null) {
            return Optional.empty();
        }

        // Back through the same prefix the catalogue side stores, so a film and a show of
        // the same TMDB number stay two different items.
        return TmdbKind.ofExternalId(kindPath + ":" + tmdbId)
                .map(kind -> new CanonicalRef(Source.TMDB, kind.externalId(tmdbId)));
    }
}
