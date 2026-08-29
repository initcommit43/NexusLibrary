package dev.nexus.modules.games;

import dev.nexus.core.domain.ExternalIds;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
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

    static final String ACHIEVEMENTS_KEY = AchievementCatalogue.ACHIEVEMENTS_KEY;
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
    private final SteamAchievementsClient client;
    private final AchievementCatalogue catalogue;

    public AchievementItemSyncer(
            UserEntryRepository entries, SteamAchievementsClient client, AchievementCatalogue catalogue) {
        this.entries = entries;
        this.client = client;
        this.catalogue = catalogue;
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

        catalogue.ensure(entry.getItem(), appId);
        return storeProgress(entry, achievements);
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

        completeIfFinished(entry, unlocked.size(), achievements.size(), extra.get(ACHIEVEMENTS_KEY));

        progress.put(SYNCED_AT_KEY, Instant.now().getEpochSecond());
        extra.put(ACHIEVEMENTS_KEY, progress);
        entry.setProgressExtra(extra);
        entries.save(entry);
        return changed;
    }

    /**
     * Marks a game finished the first time every achievement is earned.
     *
     * <p>Only on the crossing, never on every sync. A reader who earns the last achievement
     * and then puts the game back on another shelf — replaying it, or keeping it in progress
     * for a DLC — would otherwise have that undone by the next sync, and a shelf the app
     * keeps putting things back on is not a shelf they own.
     */
    private void completeIfFinished(UserEntry entry, int unlocked, int total, Object stored) {
        boolean allEarned = total > 0 && unlocked == total;
        if (!allEarned || wasAllEarned(stored) || entry.getStatus() == TrackingStatus.COMPLETED) {
            return;
        }
        entry.setStatus(TrackingStatus.COMPLETED);
    }

    @SuppressWarnings("unchecked")
    private boolean wasAllEarned(Object stored) {
        if (!(stored instanceof Map<?, ?> map)) {
            return false;
        }
        Map<String, Object> progress = (Map<String, Object>) map;
        return progress.get("unlocked") instanceof List<?> unlocked
                && progress.get("total") instanceof Number total
                && total.intValue() > 0
                && unlocked.size() == total.intValue();
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
