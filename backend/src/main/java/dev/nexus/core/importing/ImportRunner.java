package dev.nexus.core.importing;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.jobs.SyncJob;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs an import on a background thread and reports its progress through the job.
 *
 * <p>A list of several hundred titles is minutes of work against someone else's rate limit,
 * which is far too long to hold a request open for — and a reader watching a spinner with no
 * number on it has no way to tell slow from broken.
 *
 * <p>Separate bean from the service that starts it, since {@code @Async} is applied by a
 * proxy and a method calling it on itself would simply run inline.
 */
@Component
public class ImportRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportRunner.class);

    private final LibraryImportService importService;
    private final Optional<PostImportSyncs> followUps;

    public ImportRunner(LibraryImportService importService, Optional<PostImportSyncs> followUps) {
        this.importService = importService;
        this.followUps = followUps;
    }

    @Async
    public void run(SyncJob job, ExternalAccount account) {
        try {
            ImportReport report = importService.importLibrary(account, job);
            job.setReport(report);

            if (job.isCancelled()) {
                job.markCancelled("Import stopped. What had already been imported was kept.");
                return;
            }

            // Follow-up work belongs to the provider that was imported, and to no other.
            followUps.ifPresent(sync -> sync.startAfter(account).ifPresent(job::setFollowUp));
            job.complete();
        } catch (RuntimeException e) {
            // A failure the reader can act on is repeated verbatim, because it tells them
            // what to change; anything else is ours to read in the log, not theirs.
            if (e instanceof UserFixableException fixable) {
                job.fail(fixable.advice());
                return;
            }
            // An outage is not theirs to fix either, but it is theirs to know about:
            // "please try again" against a service that is down is an instruction to
            // retry something that cannot succeed.
            if (e instanceof UpstreamUnavailableException down) {
                log.warn("Import failed for {}: upstream unavailable", account.getProvider(), e);
                job.failUpstream(down.serviceName(), outageMessage(down));
                return;
            }
            log.warn("Import failed for {}", account.getProvider(), e);
            job.fail("The import could not be completed. Please try again.");
        }
    }

    /**
     * Names the service, quotes what it said for itself when it said anything, and is
     * straight about the rollback: the import is one transaction, so unlike a cancel —
     * which stops between items and keeps them — a failure keeps nothing.
     */
    private static String outageMessage(UpstreamUnavailableException down) {
        String message = down.serviceName()
                + " is not answering right now, so the import could not finish and nothing "
                + "from this run was saved. Try again once it is back.";
        return down.serviceSays()
                .map(words -> message + " " + down.serviceName() + " says: “" + words + "”")
                .orElse(message);
    }
}
