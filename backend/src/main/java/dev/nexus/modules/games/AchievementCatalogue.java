package dev.nexus.modules.games;

import dev.nexus.core.catalog.MediaDetailService;
import dev.nexus.core.domain.ExternalIds;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one owner of a game's achievement list on the shared item.
 *
 * <p>The list carries no user context — the same names, icons and hidden flags for every
 * player — so it is fetched once per game and then read by everyone, tracking it or not. Two
 * callers want it: a library sync, which already holds the appid it is syncing, and a game's
 * page, which has to find one before it can ask.
 */
@Component
public class AchievementCatalogue {

    static final String ACHIEVEMENTS_KEY = "achievements";

    /**
     * When a page last sent us to Steam for this game.
     *
     * <p>Plenty of games have no achievements at all, and that answer is not stored anywhere
     * — without a stamp every visit to such a page would spend another call against a key
     * budget shared by every user of the app.
     */
    static final String CHECKED_AT_KEY = "achievementsCheckedAt";

    private static final Duration RECHECK_AFTER = Duration.ofDays(30);

    /** Steam store links carry the appid in the path: store.steampowered.com/app/292030/… */
    private static final Pattern STEAM_APP_URL = Pattern.compile("store\\.steampowered\\.com/app/(\\d+)");

    private final TrackableItemRepository items;
    private final SteamAchievementsClient client;

    public AchievementCatalogue(TrackableItemRepository items, SteamAchievementsClient client) {
        this.items = items;
        this.client = client;
    }

    /** What is already stored. No request, no write. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> read(TrackableItem item) {
        return item.getMetadata().get(ACHIEVEMENTS_KEY) instanceof List<?> stored
                ? (List<Map<String, Object>>) stored
                : List.of();
    }

    /**
     * Stores the list for a caller that already knows the appid, if it is not stored yet.
     *
     * <p>Joins the caller's transaction rather than opening one: a library sync writes the
     * item's catalogue and the user's unlocks as one unit.
     */
    public void ensure(TrackableItem item, String appId) {
        if (!needsCatalogue(item)) {
            return;
        }
        List<Map<String, Object>> catalogue = fetch(appId);
        if (catalogue.isEmpty()) {
            return;
        }
        item.getMetadata().put(ACHIEVEMENTS_KEY, catalogue);
        items.save(item);
    }

    /**
     * The list for a game someone opened, fetched on demand.
     *
     * <p>This is the untracked case: nobody has imported the game, so no sync has ever
     * fetched its achievements, and the page still wants to show what there is to earn.
     */
    @Transactional
    public List<Map<String, Object>> forMedia(Source source, String externalId) {
        TrackableItem item = items.findBySourceAndExternalId(source, externalId).orElse(null);
        if (item == null || item.getMediaType() != MediaType.GAME) {
            return List.of();
        }

        List<Map<String, Object>> stored = read(item);
        if (!stored.isEmpty() || checkedRecently(item)) {
            return stored;
        }

        String appId = steamAppId(item).orElse(null);
        List<Map<String, Object>> catalogue = appId == null ? List.<Map<String, Object>>of() : fetch(appId);

        item.getMetadata().put(CHECKED_AT_KEY, Instant.now().getEpochSecond());
        if (!catalogue.isEmpty()) {
            item.getMetadata().put(ACHIEVEMENTS_KEY, catalogue);
        }
        items.save(item);
        return catalogue;
    }

    /** Steam's schema rows, trimmed to what a list of achievements actually shows. */
    private List<Map<String, Object>> fetch(String appId) {
        return client.fetchSchema(appId).stream()
                .map(achievement -> {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("id", string(achievement.get("name")));
                    summary.put("name", string(achievement.get("displayName")));
                    summary.put("description", string(achievement.get("description")));
                    summary.put("icon", string(achievement.get("icon")));
                    summary.put("lockedIcon", string(achievement.get("icongray")));
                    summary.put("hidden", achievement.get("hidden") instanceof Number h && h.intValue() == 1);
                    return summary;
                })
                .toList();
    }

    /**
     * Also refreshes a catalogue stored before icons were fetched, which is cheaper than a
     * migration and self-correcting as libraries are synced.
     */
    private boolean needsCatalogue(TrackableItem item) {
        List<Map<String, Object>> stored = read(item);
        if (stored.isEmpty()) {
            return true;
        }
        return stored.getFirst().get("icon") == null;
    }

    private boolean checkedRecently(TrackableItem item) {
        return item.getMetadata().get(CHECKED_AT_KEY) instanceof Number checkedAt
                && Instant.ofEpochSecond(checkedAt.longValue()).isAfter(Instant.now().minus(RECHECK_AFTER));
    }

    /**
     * The Steam appid behind this game, recorded on the item once it is known.
     *
     * <p>An import writes it through {@link ExternalIds}; a game nobody imported has it only
     * in the store link IGDB already handed over with the rest of the page's detail. Reading
     * it there is exact — it is an id in a URL, not a title match — and costs no request,
     * which is what makes this affordable on a page nobody is tracking.
     */
    private Optional<String> steamAppId(TrackableItem item) {
        Optional<String> recorded = ExternalIds.read(item, Provider.STEAM);
        if (recorded.isPresent()) {
            return recorded;
        }

        for (Map<String, Object> website : websites(item)) {
            Matcher match = STEAM_APP_URL.matcher(String.valueOf(website.get("url")));
            if (match.find()) {
                String appId = match.group(1);
                ExternalIds.record(item, Provider.STEAM, appId);
                return Optional.of(appId);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> websites(TrackableItem item) {
        if (!(item.getMetadata().get(MediaDetailService.DETAIL_KEY) instanceof Map<?, ?> detail)
                || !(((Map<String, Object>) detail).get("websites") instanceof List<?> websites)) {
            return List.of();
        }
        return (List<Map<String, Object>>) websites;
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
