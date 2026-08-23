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
            followUps.ifPresent(sync -> sync.startAfter(account).ifPresent(job::setFollowUp));
            job.complete();
        } catch (RuntimeException e) {
            // A failure the reader can act on is repeated verbatim, because it tells them
            // what to change; anything else is ours to read in the log, not theirs.
            if (e instanceof UserFixableException fixable) {
                job.fail(fixable.advice());
                return;
            }
            log.warn("Import failed for {}", account.getProvider(), e);
            job.fail("The import could not be completed. Please try again.");
        }
    }
}
