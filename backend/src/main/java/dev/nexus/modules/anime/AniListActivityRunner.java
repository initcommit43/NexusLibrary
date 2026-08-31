package dev.nexus.modules.anime;

import dev.nexus.core.jobs.SyncJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Walks a reader's AniList activity, newest first, on a background thread.
 *
 * <p>Separate from the service that starts it: {@code @Async} is applied by a proxy, so a
 * method calling it on itself would simply run inline and block the very request it was meant
 * to free.
 */
@Component
public class AniListActivityRunner {

    private static final Logger log = LoggerFactory.getLogger(AniListActivityRunner.class);

    /**
     * As far back as one run will walk: fifty events a page, so ten thousand events.
     *
     * <p>A ceiling rather than a limit anyone should reach — it is there so a stream that
     * never says it has ended cannot spend a reader's whole rate budget proving it.
     */
    static final int MAX_PAGES = 200;

    private final AniListClient client;
    private final AniListActivityWriter writer;
    private final AniListNotificationService notifications;

    public AniListActivityRunner(
            AniListClient client, AniListActivityWriter writer, AniListNotificationService notifications) {
        this.client = client;
        this.writer = writer;
        this.notifications = notifications;
    }

    @Async
    public void run(SyncJob job, Long userId, String accessToken) {
        try {
            int viewer = client.viewerId(accessToken);

            for (int page = 1; page <= MAX_PAGES; page++) {
                if (job.isCancelled()) {
                    job.markCancelled("Stopped. The activity brought in so far has been kept.");
                    return;
                }

                AniListClient.ActivityPage answer = client.fetchActivity(viewer, page, accessToken);
                if (answer.activities().isEmpty()) {
                    break;
                }

                AniListActivityWriter.Written written = writer.save(userId, answer.activities());
                for (int at = 0; at < written.seen(); at++) {
                    job.advance(at < written.stored());
                }

                log.debug(
                        "AniList activity page {}: {} seen, {} stored, {} already held, {} off-shelf,"
                                + " next page {}",
                        page,
                        written.seen(),
                        written.stored(),
                        written.known(),
                        written.unmatched(),
                        answer.hasNextPage());

                /*
                 * The end of the stream is the only thing that stops this walk.
                 *
                 * <p>Stopping at the first page holding nothing new looks like the obvious
                 * saving and is wrong: a run that ended early — an outage, a rate limit, a
                 * reader closing the tab — leaves the newest events here and years of older
                 * ones still there, and every later run would stop on that same first page and
                 * never reach them. What is already held costs one query a page to skip.
                 *
                 * <p>A full page with no next flag is treated as maybe-more too: the flag is
                 * AniList's, and being wrong about it costs one request that comes back empty.
                 */
                if (!answer.hasNextPage() && answer.activities().size() < AniListClient.MAX_BATCH) {
                    break;
                }
            }
            log.debug("AniList activity sync finished after {} events", job.getProcessed());

            /*
             * The notification backfill is chained here rather than registered as a second
             * follow-up of the import: core picks one PostImportSync per provider and a job
             * carries one follow-up, so a second hook for AniList would never be reached.
             *
             * <p>After rather than alongside, for the reason the import gives about its own
             * follow-ups: both walks spend the same reader's rate budget, and racing them
             * spends it twice as fast for no gain.
             */
            job.setFollowUp(notifications.start(userId, accessToken));
            job.complete();
        } catch (AniListUnavailableException e) {
            // Every page committed on its own, so what landed stays landed.
            log.warn("AniList activity sync lost AniList for job {}", job.getId(), e);
            job.failUpstream(
                    e.serviceName(),
                    "AniList stopped answering. The activity brought in so far has been kept — "
                            + "run the import again once AniList is back to finish the rest.");
        } catch (RuntimeException e) {
            log.warn("AniList activity sync failed for job {}", job.getId(), e);
            job.fail("Your AniList activity could not be brought in. Please try again.");
        }
    }
}
