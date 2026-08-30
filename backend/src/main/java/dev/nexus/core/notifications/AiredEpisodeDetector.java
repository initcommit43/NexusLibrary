package dev.nexus.core.notifications;

import dev.nexus.core.cache.ItemRefreshService;
import dev.nexus.core.domain.NotificationType;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notices that an episode has aired, and tells everyone keeping the series.
 *
 * <p>Nothing is asked of a source to find this out. Every ongoing title already carries the
 * episode that is next and the moment it lands, because a shelf counts down beside each row —
 * so an episode having aired is a question the database answers on its own, and the source is
 * only asked afterwards, for what comes after it.
 *
 * <p>What sets it going is {@link AiredEpisodeSchedule}, kept apart so a test can sweep when
 * it means to rather than whenever the clock says.
 */
@Component
public class AiredEpisodeDetector {

    private static final Logger log = LoggerFactory.getLogger(AiredEpisodeDetector.class);

    private final TrackableItemRepository items;
    private final NotificationService notifications;
    private final ItemRefreshService refresh;

    public AiredEpisodeDetector(
            TrackableItemRepository items, NotificationService notifications, ItemRefreshService refresh) {
        this.items = items;
        this.notifications = notifications;
        this.refresh = refresh;
    }

    @Transactional
    public void sweep() {
        List<TrackableItemRepository.AiredEpisode> aired = items.airedSince(Instant.now().getEpochSecond());
        if (aired.isEmpty()) {
            return;
        }

        Map<Long, TrackableItem> titles = items
                .findAllById(aired.stream()
                        .map(TrackableItemRepository.AiredEpisode::getItemId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(TrackableItem::getId, title -> title));

        for (TrackableItemRepository.AiredEpisode episode : aired) {
            TrackableItem title = titles.get(episode.getItemId());
            if (title == null || episode.getEpisode() == null) {
                continue;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("episode", episode.getEpisode());

            notifications.raise(
                    episode.getUserId(),
                    title,
                    NotificationType.EPISODE_AIRED,
                    "episode:" + episode.getEpisode(),
                    payload);
        }

        /*
         * The source is asked last, and only for the titles that just landed one.
         *
         * <p>Until it answers, the item still carries the episode that has aired — which is
         * exactly why a notification is written against the episode number rather than the
         * moment it was noticed: the sweep runs again in a quarter of an hour, finds the same
         * episode, and has nothing new to say about it.
         */
        Set<TrackableItem> landed = aired.stream()
                .map(row -> titles.get(row.getItemId()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        log.debug("{} episodes aired across {} titles", aired.size(), landed.size());
        refresh.refreshIfStale(landed);
    }
}
