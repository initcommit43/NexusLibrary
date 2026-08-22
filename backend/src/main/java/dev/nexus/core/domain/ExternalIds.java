package dev.nexus.core.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cross-references from a canonical item back to the providers that know it — the Steam
 * appid behind an IGDB game, later the MAL id behind an AniList entry.
 *
 * <p>Kept in the shared item's metadata rather than per user, because which Steam appid
 * corresponds to which IGDB game is a fact about the game, identical for everyone. The
 * resolver already computes it during an import; without recording it the mapping is
 * discarded and there is no route from a tracked game back to the provider.
 */
public final class ExternalIds {

    static final String METADATA_KEY = "externalIds";

    private ExternalIds() {}

    @SuppressWarnings("unchecked")
    public static Optional<String> read(TrackableItem item, Provider provider) {
        Object ids = item.getMetadata().get(METADATA_KEY);
        if (!(ids instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        return Optional.ofNullable(map.get(provider.name())).map(Object::toString);
    }

    /**
     * @return true when the mapping was missing and has now been added
     */
    @SuppressWarnings("unchecked")
    public static boolean record(TrackableItem item, Provider provider, String providerItemId) {
        Map<String, Object> metadata = item.getMetadata();
        Map<String, Object> ids = metadata.get(METADATA_KEY) instanceof Map<?, ?> existing
                ? new LinkedHashMap<>((Map<String, Object>) existing)
                : new LinkedHashMap<>();

        if (providerItemId.equals(ids.get(provider.name()))) {
            return false;
        }

        ids.put(provider.name(), providerItemId);
        metadata.put(METADATA_KEY, ids);
        return true;
    }
}
