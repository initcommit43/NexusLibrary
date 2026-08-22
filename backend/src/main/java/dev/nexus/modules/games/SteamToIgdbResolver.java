package dev.nexus.modules.games;

import dev.nexus.core.adapter.CanonicalRef;
import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ItemResolver;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Maps Steam appids onto IGDB ids through IGDB's {@code external_games} cross-reference.
 *
 * <p>This is a hard id join, not a title match: IGDB already records which of its games
 * corresponds to which Steam appid, so there is nothing to guess. Titles that Steam knows
 * and IGDB does not simply fail to resolve and go to the unmatched report.
 *
 * <p>Lives in the games module rather than in core because it necessarily knows both APIs;
 * core owns the import process, not the cross-references between particular catalogues.
 */
@Component
public class SteamToIgdbResolver implements ItemResolver {

    private final IgdbClient client;

    public SteamToIgdbResolver(IgdbClient client) {
        this.client = client;
    }

    @Override
    public Provider provider() {
        return Provider.STEAM;
    }

    @Override
    public Map<ExternalItemRef, CanonicalRef> resolveAll(Collection<ExternalItemRef> refs) {
        Map<String, ExternalItemRef> byAppId = refs.stream()
                .collect(Collectors.toMap(ExternalItemRef::providerItemId, ref -> ref, (a, b) -> a));

        Map<ExternalItemRef, CanonicalRef> resolved = new HashMap<>();

        for (List<String> batch : IgdbClient.partition(byAppId.keySet())) {
            client.findGamesBySteamAppIds(batch).forEach(row -> {
                String appId = String.valueOf(row.get("uid"));
                Object game = row.get("game");
                // external_games returns the linked game either inlined or as a bare id.
                String igdbId = game instanceof Map<?, ?> nested
                        ? String.valueOf(nested.get("id"))
                        : String.valueOf(game);

                ExternalItemRef ref = byAppId.get(appId);
                if (ref != null && game != null) {
                    resolved.put(ref, new CanonicalRef(Source.IGDB, igdbId));
                }
            });
        }

        return resolved;
    }
}
