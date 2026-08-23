package dev.nexus.modules.games;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.importing.PostImportSync;
import dev.nexus.core.jobs.SyncJob;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Steam's achievements follow every Steam import, without anyone asking for them twice. */
@Component
public class AchievementPostImportSync implements PostImportSync {

    private final AchievementSyncService achievements;

    public AchievementPostImportSync(AchievementSyncService achievements) {
        this.achievements = achievements;
    }

    @Override
    public Provider provider() {
        return Provider.STEAM;
    }

    @Override
    public Optional<SyncJob> startAfter(ExternalAccount account) {
        return Optional.of(achievements.start(account));
    }
}
