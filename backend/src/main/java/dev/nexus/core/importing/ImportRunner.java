package dev.nexus.core.importing;

import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.jobs.SyncJob;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
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
        execute(job, account.getProvider(), () -> importService.importLibrary(account, job), account);
    }

    /**
     * The same run for a library that came from an uploaded export rather than from the
     * provider. No follow-up work is started: those belong to a connected account, and an
     * upload is not one — Steam's achievement sync has no token to walk a library with.
     */
    @Async
    public void run(SyncJob job, Long userId, Provider provider, List<ImportedEntry> library) {
        execute(job, provider, () -> importService.importEntries(userId, provider, library, job), null);
    }

    private void execute(SyncJob job, Provider provider, Supplier<ImportReport> work, ExternalAccount account) {
        try {
            ImportReport report = work.get();
            job.setReport(report);

            if (job.isCancelled()) {
                job.markCancelled("Import stopped. What had already been imported was kept.");
                return;
            }

            // Follow-up work belongs to the provider that was imported, and to no other.
            if (account != null) {
                followUps.ifPresent(sync -> sync.startAfter(account).ifPresent(job::setFollowUp));
            }
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
                log.warn("Import failed for {}: upstream unavailable", provider, e);
                job.failUpstream(down.serviceName(), outageMessage(down));
                return;
            }
            log.warn("Import failed for {}", provider, e);
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
