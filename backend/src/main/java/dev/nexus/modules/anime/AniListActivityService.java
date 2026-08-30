package dev.nexus.modules.anime;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.jobs.JobRegistry;
import dev.nexus.core.jobs.SyncJob;
import org.springframework.stereotype.Service;

/** Brings in a reader's AniList activity, which is the history their map is drawn from. */
@Service
public class AniListActivityService {

    private final AniListActivityRunner runner;
    private final JobRegistry jobs;

    public AniListActivityService(AniListActivityRunner runner, JobRegistry jobs) {
        this.runner = runner;
        this.jobs = jobs;
    }

    /**
     * Starts a sync and hands back a job to poll straight away.
     *
     * <p>Only one runs per reader at a time. A second would walk the same stream against a
     * rate budget shared by everyone using the app, and write nothing the first had not.
     */
    public SyncJob start(ExternalAccount account) {
        return jobs.runningFor(account.getUserId(), SyncJob.Kind.ACTIVITY, Provider.ANILIST)
                .orElseGet(() -> {
                    // No total: how far back a stream goes is only known by reaching the end
                    // of it, and a bar that invents a length is a bar that lies twice.
                    SyncJob job = jobs.start(account.getUserId(), SyncJob.Kind.ACTIVITY, Provider.ANILIST, 0);
                    runner.run(job, account.getUserId(), account.getAccessToken());
                    return job;
                });
    }
}
