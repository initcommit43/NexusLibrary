package dev.nexus.core.jobs;

import dev.nexus.core.domain.Provider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Where each walk left off, read before a run and written after every page of one. */
@Service
public class SyncProgressService {

    private final SyncProgressRepository progress;

    public SyncProgressService(SyncProgressRepository progress) {
        this.progress = progress;
    }

    /**
     * The page a run should start on.
     *
     * <p>Page 1 for a walk that has finished the stream before: what it is after now is what
     * is new, and that is at the top. Where it stopped, for one that has not.
     */
    @Transactional(readOnly = true)
    public int startPage(Long userId, Provider provider, SyncJob.Kind kind) {
        return progress.findByUserIdAndProviderAndKind(userId, provider, kind)
                .filter(held -> !held.isComplete())
                .map(SyncProgress::getNextPage)
                .orElse(1);
    }

    /**
     * Written in a transaction of its own, so a run's place is kept whatever becomes of the
     * page it was in the middle of.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stoppedAt(Long userId, Provider provider, SyncJob.Kind kind, int page) {
        row(userId, provider, kind).stoppedAt(page);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reachedTheEnd(Long userId, Provider provider, SyncJob.Kind kind) {
        row(userId, provider, kind).reachedTheEnd();
    }

    private SyncProgress row(Long userId, Provider provider, SyncJob.Kind kind) {
        return progress.findByUserIdAndProviderAndKind(userId, provider, kind)
                .orElseGet(() -> progress.save(new SyncProgress(userId, provider, kind)));
    }
}
