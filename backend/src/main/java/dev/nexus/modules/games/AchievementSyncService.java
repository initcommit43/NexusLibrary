package dev.nexus.modules.games;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.jobs.JobRegistry;
import dev.nexus.core.jobs.SyncJob;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AchievementSyncService {

    private final AchievementItemSyncer syncer;
    private final AchievementSyncRunner runner;
    private final JobRegistry jobs;

    public AchievementSyncService(AchievementItemSyncer syncer, AchievementSyncRunner runner, JobRegistry jobs) {
        this.syncer = syncer;
        this.runner = runner;
        this.jobs = jobs;
    }

    /**
     * Starts a sync and hands back a job to poll straight away.
     *
     * <p>Only one runs per user at a time. A second would repeat every Steam call for no
     * further result, against a request budget shared by everyone using the app.
     */
    public SyncJob start(ExternalAccount account) {
        return jobs.runningFor(account.getUserId()).orElseGet(() -> {
            List<Long> entryIds = syncer.steamEntryIds(account.getUserId());
            SyncJob job = jobs.start(account.getUserId(), entryIds.size());
            runner.run(job, account.getExternalUserId(), entryIds);
            return job;
        });
    }
}
