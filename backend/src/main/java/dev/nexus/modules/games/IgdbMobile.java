package dev.nexus.modules.games;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phone games, and keeping them out.
 *
 * <p>A library of things worth finishing is not what a storefront of gacha and idle clickers
 * belongs in: they arrive in their thousands, they outnumber everything else in a search for
 * a common word, and nobody tracking their backlog is looking for them.
 *
 * <p>Only games that exist <em>nowhere else</em> are dropped. Minecraft and Stardew Valley
 * are on a phone too, and a filter that read "has a mobile port" would take half the shelf
 * with it.
 */
final class IgdbMobile {

    /** IGDB's own platform ids. They do not move. */
    private static final Set<Integer> MOBILE = Set.of(
            34, // Android
            39, // iOS
            55, // Legacy mobile device
            73, // BlackBerry OS
            74); // Windows Phone

    private IgdbMobile() {}

    /**
     * Whether a game is worth showing: anything with a platform outside the phone, and
     * anything IGDB lists no platform for at all.
     *
     * <p>A record with no platforms is unknown rather than mobile, and an unreleased game is
     * often exactly that — dropping those would empty the shelf of what is coming.
     */
    static boolean isNotMobileOnly(Map<String, Object> game) {
        if (!(game.get("platforms") instanceof List<?> platforms) || platforms.isEmpty()) {
            return true;
        }

        return platforms.stream().anyMatch(platform -> {
            Integer id = idOf(platform);
            // A platform the caller did not ask the id for is not evidence of a phone game.
            return id == null || !MOBILE.contains(id);
        });
    }

    private static Integer idOf(Object platform) {
        if (platform instanceof Number id) {
            return id.intValue();
        }
        return platform instanceof Map<?, ?> expanded && expanded.get("id") instanceof Number id
                ? id.intValue()
                : null;
    }
}
