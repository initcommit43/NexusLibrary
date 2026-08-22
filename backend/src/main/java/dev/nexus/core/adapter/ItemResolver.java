package dev.nexus.core.adapter;

import dev.nexus.core.domain.Provider;
import java.util.Collection;
import java.util.Map;

/**
 * Maps a provider's item ids onto canonical catalogue ids — Steam appid to IGDB id, MAL id
 * to AniList id.
 *
 * <p>Resolves in bulk by design. A library is hundreds of items, and resolving them one at
 * a time would spend minutes inside an external API's rate limit; implementations are
 * expected to batch.
 *
 * @return only the refs that resolved; anything absent lands in the unmatched report
 */
public interface ItemResolver {

    Provider provider();

    Map<ExternalItemRef, CanonicalRef> resolveAll(Collection<ExternalItemRef> refs);
}
