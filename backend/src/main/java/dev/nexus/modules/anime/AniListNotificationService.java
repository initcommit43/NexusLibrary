package dev.nexus.modules.anime;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.jobs.JobRegistry;
import dev.nexus.core.jobs.SyncJob;
import org.springframework.stereotype.Service;

/**
 * Brings in what AniList noticed about a reader's titles while they were not looking.
 *
 * <p>A backfill rather than a subscription. Going forward an episode airing is noticed here
 * without asking anyone — the next episode and the moment it lands already ride on every
 * ongoing item — so this is for the history the app was not running for.
 */
@Service
public class AniListNotificationService {

    private final AniListNotificationRunner runner;
    private final JobRegistry jobs;

    public AniListNotificationService(AniListNotificationRunner runner, JobRegistry jobs) {
        this.runner = runner;
        this.jobs = jobs;
    }

    /**
     * Starts a walk and hands back a job to poll straight away.
     *
     * <p>Only one runs per reader at a time, as with activity: a second would walk the same
     * stream against a rate budget shared by everyone, and write nothing the first had not.
     */
    public SyncJob start(ExternalAccount account) {
        return start(account.getUserId(), account.getAccessToken());
    }

    public SyncJob start(Long userId, String accessToken) {
        return jobs.runningFor(userId, SyncJob.Kind.NOTIFICATIONS, Provider.ANILIST)
                .orElseGet(() -> {
                    // No total, for the reason the activity walk gives: how far back a stream
                    // goes is only known by reaching the end of it.
                    SyncJob job = jobs.start(userId, SyncJob.Kind.NOTIFICATIONS, Provider.ANILIST, 0);
                    runner.run(job, userId, accessToken);
                    return job;
                });
    }
}
