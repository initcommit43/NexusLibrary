package dev.nexus.core.adapter;

import dev.nexus.core.domain.Provider;
import java.util.Map;

/**
 * How a provider identifies an item in a user's library — a Steam appid, a MAL id.
 * Not yet canonical: a resolver turns this into a {@link CanonicalRef}.
 *
 * @param hints cross-reference ids the provider happens to supply, which can spare the
 *     resolver a lookup or a fuzzy match
 */
public record ExternalItemRef(Provider provider, String providerItemId, String title, Map<String, String> hints) {

    public ExternalItemRef(Provider provider, String providerItemId, String title) {
        this(provider, providerItemId, title, Map.of());
    }
}
