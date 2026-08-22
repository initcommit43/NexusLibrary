package dev.nexus.modules.games;

import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.adapter.LibraryImportAdapter;
import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SteamLibraryImportAdapter implements LibraryImportAdapter {

    private final RestClient restClient;
    private final SteamProperties properties;

    public SteamLibraryImportAdapter(RestClient.Builder builder, SteamProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    @Override
    public Provider provider() {
        return Provider.STEAM;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ImportedEntry> pullLibrary(ExternalAccount account) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new SteamUnavailableException("Steam API key is not configured; set STEAM_API_KEY");
        }

        Map<String, Object> body;
        try {
            body = restClient
                    .get()
                    .uri(
                            properties.apiBaseUrl()
                                    + "/IPlayerService/GetOwnedGames/v1/?key={key}&steamid={id}"
                                    + "&include_appinfo=true&include_played_free_games=true",
                            properties.apiKey(),
                            account.getExternalUserId())
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw new SteamUnavailableException("Could not reach Steam", e);
        }

        Object response = body == null ? null : body.get("response");
        if (!(response instanceof Map<?, ?> payload) || !(payload.get("games") instanceof List<?> games)) {
            // Steam answers with an empty object rather than an error when the profile's
            // game details are private. It is a privacy setting, not a failure.
            throw new SteamProfilePrivateException();
        }

        return games.stream()
                .filter(Map.class::isInstance)
                .map(game -> toEntry((Map<String, Object>) game))
                .toList();
    }

    private ImportedEntry toEntry(Map<String, Object> game) {
        int minutesPlayed = game.get("playtime_forever") instanceof Number n ? n.intValue() : 0;

        return new ImportedEntry(
                new ExternalItemRef(
                        Provider.STEAM,
                        String.valueOf(game.get("appid")),
                        String.valueOf(game.getOrDefault("name", "Unknown title"))),
                // Steam knows only how long something was played, so anything touched counts
                // as in progress and the rest as a backlog item. It cannot tell us more.
                minutesPlayed > 0 ? TrackingStatus.IN_PROGRESS : TrackingStatus.PLANNING,
                minutesPlayed,
                // Playtime has no maximum, which is exactly why progress_max is nullable.
                null,
                ProgressUnit.MINUTES,
                null,
                null,
                null,
                null);
    }
}
