package dev.nexus.modules.anime;

import dev.nexus.core.adapter.CanonicalRef;
import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ItemResolver;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * AniList entries are already canonical: the id in a reader's list is the id of the item
 * itself, so there is nothing to resolve and nothing that can fail to match.
 *
 * <p>The resolver still exists because the import orchestrator asks every provider for one.
 * Keeping this trivial case explicit is what lets MyAnimeList's — which has real matching to
 * do — stay a separate, replaceable thing rather than a branch inside the pipeline.
 */
@Component
public class AniListDirectResolver implements ItemResolver {

    @Override
    public Provider provider() {
        return Provider.ANILIST;
    }

    @Override
    public Map<ExternalItemRef, CanonicalRef> resolveAll(Collection<ExternalItemRef> refs) {
        return refs.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        ref -> new CanonicalRef(Source.ANILIST, ref.providerItemId()),
                        (a, b) -> a));
    }
}
