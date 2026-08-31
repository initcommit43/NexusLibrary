package dev.nexus.modules.anime;

import dev.nexus.core.domain.Provider;
import dev.nexus.core.jobs.SyncJob;
import dev.nexus.core.jobs.SyncProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Walks what AniList noticed about a reader's titles, newest first, on a background thread.
 *
 * <p>Separate from the service that starts it for the same reason the activity walk is:
 * {@code @Async} is applied by a proxy, so a method calling it on itself runs inline.
 */
@Component
public class AniListNotificationRunner {

    private static final Logger log = LoggerFactory.getLogger(AniListNotificationRunner.class);

    /**
     * As far back as one run will walk: fifty a page, so a thousand notifications.
     *
     * <p>The same limit the activity walk has, for the same reason: it runs straight after
     * that one, against the same budget, on the same press of the same button.
     */
    static final int MAX_PAGES = 20;

    private final SyncProgressService progress;
    private final AniListClient client;
    private final AniListNotificationWriter writer;

    public AniListNotificationRunner(
            SyncProgressService progress, AniListClient client, AniListNotificationWriter writer) {
        this.progress = progress;
        this.client = client;
        this.writer = writer;
    }

    @Async
    public void run(SyncJob job, Long userId, String accessToken) {
        // Where the last run stopped, so the cap is a pause rather than a ceiling.
        int from = progress.startPage(userId, Provider.ANILIST, SyncJob.Kind.NOTIFICATIONS);
        int page = from;
        boolean reachedTheEnd = false;

        try {
            for (; page < from + MAX_PAGES; page++) {
                if (job.isCancelled()) {
                    progress.stoppedAt(userId, Provider.ANILIST, SyncJob.Kind.NOTIFICATIONS, page);
                    job.markCancelled("Stopped. What had already been brought in has been kept.");
                    return;
                }

                AniListClient.NotificationPage answer = client.fetchNotifications(page, accessToken);
                if (answer.notifications().isEmpty()) {
                    reachedTheEnd = true;
                    break;
                }

                AniListNotificationWriter.Written written = writer.save(userId, answer.notifications());
                for (int at = 0; at < written.seen(); at++) {
                    job.advance(at < written.stored());
                }

                log.debug(
                        "AniList notification page {}: {} seen, {} stored, {} already held,"
                                + " {} off-shelf, next page {}",
                        page,
                        written.seen(),
                        written.stored(),
                        written.known(),
                        written.unmatched(),
                        answer.hasNextPage());

                // The end of the stream is what stops the walk, for the reason the activity
                // walk gives: a run that ended early leaves the newest here and the rest
                // still there, and stopping at the first familiar page never reaches them.
                if (!answer.hasNextPage() && answer.notifications().size() < AniListClient.MAX_BATCH) {
                    reachedTheEnd = true;
                    break;
                }
            }

            if (reachedTheEnd) {
                progress.reachedTheEnd(userId, Provider.ANILIST, SyncJob.Kind.NOTIFICATIONS);
            } else {
                progress.stoppedAt(userId, Provider.ANILIST, SyncJob.Kind.NOTIFICATIONS, page);
                job.completePartly("Brought in " + job.getProcessed()
                        + " notifications, as much as one run does. Run it again to carry on.");
            }
            log.debug(
                    "AniList notification sync finished after {} notifications, reached the end: {}",
                    job.getProcessed(),
                    reachedTheEnd);
            if (job.getState() != SyncJob.State.COMPLETE) {
                job.complete();
            }
        } catch (AniListUnavailableException e) {
            progress.stoppedAt(userId, Provider.ANILIST, SyncJob.Kind.NOTIFICATIONS, page);
            // Every page committed on its own, so what landed stays landed.
            log.warn("AniList notification sync lost AniList for job {}", job.getId(), e);
            job.failUpstream(
                    e.serviceName(),
                    "AniList stopped answering. What was brought in has been kept — run the "
                            + "import again once AniList is back to finish the rest.");
        } catch (RuntimeException e) {
            progress.stoppedAt(userId, Provider.ANILIST, SyncJob.Kind.NOTIFICATIONS, page);
            log.warn("AniList notification sync failed for job {}", job.getId(), e);
            job.fail("Your AniList notifications could not be brought in. Please try again.");
        }
    }
}
