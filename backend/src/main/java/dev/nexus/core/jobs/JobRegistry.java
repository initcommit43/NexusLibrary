package dev.nexus.core.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tracks running background syncs so a client can poll one it started.
 *
 * <p>In memory, and therefore per instance — the same trade-off as the inbound rate
 * limiter. That is correct for a single-instance deployment; running several replicas
 * would need shared storage, since a poll could otherwise land on an instance that never
 * saw the job.
 */
@Component
public class JobRegistry {

    private static final Duration KEEP_FINISHED_FOR = Duration.ofMinutes(30);

    private final Map<String, SyncJob> jobs = new ConcurrentHashMap<>();

    public SyncJob start(Long userId, SyncJob.Kind kind, int total) {
        SyncJob job = new SyncJob(userId, kind, total);
        jobs.put(job.getId(), job);
        return job;
    }

    /** Scoped by user, so one person cannot watch another's sync. */
    public Optional<SyncJob> find(String jobId, Long userId) {
        return Optional.ofNullable(jobs.get(jobId)).filter(job -> job.getUserId().equals(userId));
    }

    /** Scoped by kind: an import running is no reason to refuse an achievement sync. */
    public Optional<SyncJob> runningFor(Long userId, SyncJob.Kind kind) {
        return jobs.values().stream()
                .filter(job -> job.getUserId().equals(userId)
                        && job.getKind() == kind
                        && job.getState() == SyncJob.State.RUNNING)
                .findFirst();
    }

    /** Finished jobs are kept briefly so a client can read the outcome, then dropped. */
    @Scheduled(fixedDelay = 300_000)
    public void evictFinished() {
        Instant cutoff = Instant.now().minus(KEEP_FINISHED_FOR);
        jobs.values()
                .removeIf(job -> job.getFinishedAt() != null && job.getFinishedAt().isBefore(cutoff));
    }
}
