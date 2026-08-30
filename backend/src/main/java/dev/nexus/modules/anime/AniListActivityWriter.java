package dev.nexus.modules.anime;

import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.ProviderActivity;
import dev.nexus.core.domain.ProviderActivityRepository;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Turns a page of AniList activity into rows, and writes the ones that are new. */
@Component
public class AniListActivityWriter {

    /**
     * What one page came to.
     *
     * @param stored events written
     * @param known events already held, which is how a sync knows it has reached its own history
     * @param unmatched events about titles that are not on the shelf
     */
    public record Written(int stored, int known, int unmatched) {

        public int seen() {
            return stored + known + unmatched;
        }
    }

    private final ProviderActivityRepository activity;
    private final TrackableItemRepository items;

    public AniListActivityWriter(ProviderActivityRepository activity, TrackableItemRepository items) {
        this.activity = activity;
        this.items = items;
    }

    /**
     * Writes a page, skipping what is already held and what is about a title this library
     * does not have.
     *
     * <p>Both lookups are one query for the whole page rather than one per event: fifty round
     * trips a page, over a decade of history, is the difference between a sync and an
     * afternoon.
     *
     * <p>A title the reader has since removed from their list is left out rather than pulled
     * in. The map is drawn from the shelf, and an event about something not on it would be a
     * square nothing on the profile can explain.
     */
    @Transactional
    public Written save(Long userId, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return new Written(0, 0, 0);
        }

        List<String> ids = rows.stream().map(row -> string(row.get("id"))).filter(java.util.Objects::nonNull).toList();
        Set<String> held = activity
                .findByUserIdAndProviderAndExternalIdIn(userId, Provider.ANILIST, ids)
                .stream()
                .map(ProviderActivity::getExternalId)
                .collect(Collectors.toSet());

        Set<String> mediaIds = rows.stream()
                .map(AniListActivityWriter::mediaId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Map<String, Long> shelved = items.findBySourceAndExternalIdIn(Source.ANILIST, mediaIds).stream()
                .collect(Collectors.toMap(TrackableItem::getExternalId, TrackableItem::getId, (first, second) -> first));

        List<ProviderActivity> fresh = new ArrayList<>();
        int known = 0;
        int unmatched = 0;

        for (Map<String, Object> row : rows) {
            String id = string(row.get("id"));
            if (id == null) {
                continue;
            }
            if (held.contains(id)) {
                known++;
                continue;
            }

            Long itemId = shelved.get(mediaId(row));
            LocalDate day = happenedOn(row.get("createdAt"));
            if (itemId == null || day == null) {
                unmatched++;
                continue;
            }

            fresh.add(new ProviderActivity(
                    userId,
                    Provider.ANILIST,
                    id,
                    itemId,
                    day,
                    string(row.get("status")),
                    string(row.get("progress"))));
        }

        activity.saveAll(fresh);
        return new Written(fresh.size(), known, unmatched);
    }

    /**
     * The day the event happened, read in the app's own zone.
     *
     * <p>AniList stamps its stream in Unix seconds, which is an instant and not a day. The
     * map is drawn in the same zone the rest of the app reckons a day in, so an episode
     * watched late on a Sunday sits on the Sunday here as it does there.
     */
    private static LocalDate happenedOn(Object createdAt) {
        return createdAt instanceof Number seconds
                ? Instant.ofEpochSecond(seconds.longValue()).atZone(ZoneId.systemDefault()).toLocalDate()
                : null;
    }

    private static String mediaId(Map<String, Object> row) {
        return row.get("media") instanceof Map<?, ?> media ? string(media.get("id")) : null;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
