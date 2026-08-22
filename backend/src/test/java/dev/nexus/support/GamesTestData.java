package dev.nexus.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Canned IGDB payloads, so no test ever reaches the real API. */
public final class GamesTestData {

    public static final String BOTW_ID = "7346";
    public static final String HADES_ID = "113112";

    private GamesTestData() {}

    public static Map<String, Object> botw() {
        return game(7346, "The Legend of Zelda: Breath of the Wild");
    }

    public static Map<String, Object> hades() {
        return game(113112, "Hades");
    }

    public static Map<String, Object> game(int id, String name) {
        Map<String, Object> game = new HashMap<>();
        game.put("id", id);
        game.put("name", name);
        game.put("summary", "A game about " + name + ".");
        game.put("first_release_date", 1488499200L);
        game.put("cover", Map.of("url", "//images.igdb.com/igdb/image/upload/t_thumb/cover.jpg"));
        game.put("platforms", List.of(Map.of("name", "PC")));
        game.put("genres", List.of(Map.of("name", "Adventure")));
        game.put("total_rating", 93.4);
        return game;
    }
}
