package dev.nexus.modules.games;

import dev.nexus.core.domain.ExternalIds;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.domain.UserEntryRepository;
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
        if (appId == null) {
            return false;
        }

        List<Map<String, Object>> achievements = client.fetch(appId, steamId).orElse(null);
        if (achievements == null || achievements.isEmpty()) {
            return false;
        }

        storeCatalogue(entry.getItem(), achievements);
        return storeProgress(entry, achievements);
    }

    /**
     * Names and descriptions are the same for every player, so they are written once onto
     * the shared item rather than copied into each user's entry.
     */
    private void storeCatalogue(TrackableItem item, List<Map<String, Object>> achievements) {
        if (item.getMetadata().containsKey(ACHIEVEMENTS_KEY)) {
            return;
        }

        List<Map<String, Object>> catalogue = achievements.stream()
                .map(achievement -> {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("id", string(achievement.get("apiname")));
                    summary.put("name", string(achievement.get("name")));
                    summary.put("description", string(achievement.get("description")));
                    return summary;
                })
                .toList();

        item.getMetadata().put(ACHIEVEMENTS_KEY, catalogue);
        items.save(item);
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

        if (progress.equals(extra.get(ACHIEVEMENTS_KEY))) {
            return false;
        }

        extra.put(ACHIEVEMENTS_KEY, progress);
        entry.setProgressExtra(extra);
        entries.save(entry);
        return true;
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
