package dev.nexus.modules.games;

import dev.nexus.core.domain.ExternalIds;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.domain.UserEntryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Syncs one game's achievements in its own transaction.
 *
 * <p>A separate bean on purpose: a transaction started by a method calling itself is not a
 * transaction at all, because the proxy that would open one is bypassed. Per-game
 * transactions also mean a failure part-way through a library keeps whatever already
 * synced.
 */
@Component
public class AchievementItemSyncer {

    static final String ACHIEVEMENTS_KEY = "achievements";
    static final String SYNCED_AT_KEY = "syncedAt";

    /**
     * How long a game's achievements are considered fresh.
     *
     * <p>Steam enforces an undocumented request budget per window, so a full library sync
     * can run out part-way through. Skipping recently-synced games costs no request at all,
     * which makes a retry pick up roughly where the last run stopped instead of spending
     * the whole budget again on work already done.
     */
    private static final Duration FRESH_FOR = Duration.ofHours(6);

    private final UserEntryRepository entries;
    private final TrackableItemRepository items;
    private final SteamAchievementsClient client;

    public AchievementItemSyncer(
            UserEntryRepository entries, TrackableItemRepository items, SteamAchievementsClient client) {
        this.entries = entries;
        this.items = items;
        this.client = client;
    }

    @Transactional(readOnly = true)
    public List<Long> steamEntryIds(Long userId) {
        return entries.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(entry -> ExternalIds.read(entry.getItem(), Provider.STEAM).isPresent())
                .map(UserEntry::getId)
                .toList();
    }

    /**
     * @return true when this game's achievement progress actually changed
     */
    @Transactional
    public boolean syncOne(Long entryId, String steamId) {
        UserEntry entry = entries.findById(entryId).orElse(null);
        if (entry == null) {
            return false;
        }

        String appId = ExternalIds.read(entry.getItem(), Provider.STEAM).orElse(null);
        if (appId == null || isFresh(entry)) {
            return false;
        }

        List<Map<String, Object>> achievements = client.fetch(appId, steamId).orElse(null);
        if (achievements == null || achievements.isEmpty()) {
            return false;
        }

        storeCatalogue(entry.getItem(), appId);
        return storeProgress(entry, achievements);
    }

    /**
     * Stores the game's achievement list on the shared item: names, icons and whether an
     * achievement is a hidden one. Identical for every player, so it is fetched once per
     * game and then costs nothing for everyone after.
     */
    private void storeCatalogue(TrackableItem item, String appId) {
        if (!needsCatalogue(item)) {
            return;
        }

        List<Map<String, Object>> catalogue = client.fetchSchema(appId).stream()
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

        if (catalogue.isEmpty()) {
            return;
        }

        item.getMetadata().put(ACHIEVEMENTS_KEY, catalogue);
        items.save(item);
    }

    /**
     * Also refreshes a catalogue stored before icons were fetched, which is cheaper than a
     * migration and self-correcting as libraries are synced.
     */
    @SuppressWarnings("unchecked")
    private boolean needsCatalogue(TrackableItem item) {
        Object stored = item.getMetadata().get(ACHIEVEMENTS_KEY);
        if (!(stored instanceof List<?> catalogue) || catalogue.isEmpty()) {
            return true;
        }
        return !(catalogue.getFirst() instanceof Map<?, ?> first) || first.get("icon") == null;
    }

    @SuppressWarnings("unchecked")
    private boolean isFresh(UserEntry entry) {
        if (!(entry.getProgressExtra() instanceof Map<?, ?> extra)
                || !(((Map<String, Object>) extra).get(ACHIEVEMENTS_KEY) instanceof Map<?, ?> progress)) {
            return false;
        }
        if (!(((Map<String, Object>) progress).get(SYNCED_AT_KEY) instanceof Number syncedAt)) {
            return false;
        }
        return Instant.ofEpochSecond(syncedAt.longValue()).isAfter(Instant.now().minus(FRESH_FOR));
    }

    private boolean storeProgress(UserEntry entry, List<Map<String, Object>> achievements) {
        List<String> unlocked = new ArrayList<>();
        Map<String, Object> unlockedAt = new LinkedHashMap<>();

        for (Map<String, Object> achievement : achievements) {
            if (!(achievement.get("achieved") instanceof Number achieved) || achieved.intValue() != 1) {
                continue;
            }
            String id = string(achievement.get("apiname"));
            unlocked.add(id);
            if (achievement.get("unlocktime") instanceof Number time && time.longValue() > 0) {
                unlockedAt.put(id, time.longValue());
            }
        }

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("unlocked", unlocked);
        progress.put("unlockedAt", unlockedAt);
        progress.put("total", achievements.size());

        Map<String, Object> extra = Optional.ofNullable(entry.getProgressExtra())
                .map(LinkedHashMap::new)
                .orElseGet(LinkedHashMap::new);

        // Compared without the timestamp, so re-syncing an unchanged game is not reported
        // as a change; it is still stamped, so it counts as fresh either way.
        boolean changed = !progress.equals(withoutTimestamp(extra.get(ACHIEVEMENTS_KEY)));

        progress.put(SYNCED_AT_KEY, Instant.now().getEpochSecond());
        extra.put(ACHIEVEMENTS_KEY, progress);
        entry.setProgressExtra(extra);
        entries.save(entry);
        return changed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> withoutTimestamp(Object stored) {
        if (!(stored instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) map);
        copy.remove(SYNCED_AT_KEY);
        return copy;
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
