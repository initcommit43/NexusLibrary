package dev.nexus.core.notifications;

import dev.nexus.core.adapter.MetadataAdapterRegistry;
import dev.nexus.core.cache.TrackableItemWriter.ReleaseStarted;
import dev.nexus.core.domain.NotificationType;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.domain.UserEntryRepository;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Tells everyone keeping a title that it has started coming out, where its adapter says that
 * is news — a manga whose publication began, which has no schedule to be told about otherwise.
 *
 * <p>Every list, not only the ones being read along with: a start is worth a word to someone
 * who only planned it, the same as an anime's first episode.
 */
@Component
public class ReleaseStartNotifier {

    /** One start per title, so a status that flickers back and forth says it once. */
    static final String SUBJECT = "premiere";

    private final MetadataAdapterRegistry adapters;
    private final UserEntryRepository entries;
    private final NotificationService notifications;

    public ReleaseStartNotifier(
            MetadataAdapterRegistry adapters, UserEntryRepository entries, NotificationService notifications) {
        this.adapters = adapters;
        this.entries = entries;
        this.notifications = notifications;
    }

    @EventListener
    public void onReleaseStarted(ReleaseStarted event) {
        TrackableItem item = event.item();
        boolean announced = adapters.forSource(item.getSource())
                .map(adapter -> adapter.announcesReleaseStart(item.getMediaType()))
                .orElse(false);
        if (!announced) {
            return;
        }

        for (UserEntry entry : entries.findByItemId(item.getId())) {
            notifications.raise(entry.getUserId(), item, NotificationType.RELEASE_STARTED, SUBJECT, Map.of());
        }
    }
}
