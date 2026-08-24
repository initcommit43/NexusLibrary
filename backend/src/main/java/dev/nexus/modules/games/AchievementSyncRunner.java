package dev.nexus.modules.games;

import dev.nexus.core.jobs.SyncJob;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Walks a user's library one game at a time on a background thread.
 *
 * <p>Separate from the service that starts it: {@code @Async} is applied by a proxy, so a
 * method calling it on itself would simply run inline and block the very request it was
 * meant to free.
 */
@Component
public class AchievementSyncRunner {

    private static final Logger log = LoggerFactory.getLogger(AchievementSyncRunner.class);

    static final String PROFILE_NOT_PUBLIC_ADVICE =
            "Steam only shares achievements for public profiles. Set your profile visibility "
                    + "to Public in Steam's privacy settings, then try again.";

    private final AchievementItemSyncer syncer;

    public AchievementSyncRunner(AchievementItemSyncer syncer) {
        this.syncer = syncer;
    }

    @Async
    public void run(SyncJob job, String steamId, List<Long> entryIds) {
        try {
            for (Long entryId : entryIds) {
                job.advance(syncer.syncOne(entryId, steamId));
            }
            job.complete();
        } catch (SteamProfileNotPublicException e) {
            // The one failure a user can actually fix, so it survives as the job's message
            // rather than being flattened into a generic error.
            job.fail(PROFILE_NOT_PUBLIC_ADVICE);
        } catch (SteamThrottledException e) {
            // Every game synced so far is already committed, so say what was kept rather
            // than implying the whole run was lost.
            job.fail("Steam started rate limiting. Progress so far has been saved — run it "
                    + "again in a few minutes to finish the rest.");
        } catch (SteamUnavailableException e) {
            // An outage, not a fault to retry into: each game commits on its own, so what
            // landed stays landed and the honest advice is to wait, not to try again now.
            log.warn("Achievement sync lost Steam for job {}", job.getId(), e);
            job.fail("Steam stopped answering. Progress so far has been saved — run it "
                    + "again once Steam is back to finish the rest.");
        } catch (RuntimeException e) {
            log.warn("Achievement sync failed for job {}", job.getId(), e);
            job.fail("The achievement sync could not be completed. Please try again.");
        }
    }
}
