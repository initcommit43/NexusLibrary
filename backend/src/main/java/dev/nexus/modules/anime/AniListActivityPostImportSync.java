package dev.nexus.modules.anime;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.importing.PostImportSync;
import dev.nexus.core.jobs.SyncJob;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * A reader's activity follows every AniList import, without anyone asking for it twice.
 *
 * <p>It has to run after rather than alongside: an event is only kept for a title already on
 * the shelf, and the import is what puts it there.
 */
@Component
public class AniListActivityPostImportSync implements PostImportSync {

    private final AniListActivityService activity;

    public AniListActivityPostImportSync(AniListActivityService activity) {
        this.activity = activity;
    }

    @Override
    public Provider provider() {
        return Provider.ANILIST;
    }

    @Override
    public Optional<SyncJob> startAfter(ExternalAccount account) {
        return Optional.of(activity.start(account));
    }
}
