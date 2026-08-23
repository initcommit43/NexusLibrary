package dev.nexus.core.jobs;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** A long-running background sync, and how far it has got. */
public class SyncJob {

    public enum State {
        RUNNING,
        COMPLETE,
        FAILED
    }

    public enum Kind {
        IMPORT,
        ACHIEVEMENTS
    }

    private final String id = UUID.randomUUID().toString();
    private final Long userId;
    private final Kind kind;
    private final Instant startedAt = Instant.now();
    private final AtomicInteger processed = new AtomicInteger();
    private final AtomicInteger changed = new AtomicInteger();

    private volatile int total;
    private volatile State state = State.RUNNING;
    private volatile Object report;
    private volatile String followUpJobId;
    private volatile String message;
    private volatile Instant finishedAt;

    public SyncJob(Long userId, Kind kind, int total) {
        this.userId = userId;
        this.kind = kind;
        this.total = total;
    }

    public Kind getKind() {
        return kind;
    }

    public void advance(boolean didChange) {
        processed.incrementAndGet();
        if (didChange) {
            changed.incrementAndGet();
        }
    }

    public void complete() {
        this.state = State.COMPLETE;
        this.finishedAt = Instant.now();
    }

    /** The message is user-facing, so callers pass something a person can act on. */
    public void fail(String message) {
        this.state = State.FAILED;
        this.message = message;
        this.finishedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public State getState() {
        return state;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getProcessed() {
        return processed.get();
    }

    public int getChanged() {
        return changed.get();
    }

    public String getMessage() {
        return message;
    }

    /** What the run produced, for the kinds of job that produce something. */
    public Object getReport() {
        return report;
    }

    public void setReport(Object report) {
        this.report = report;
    }

    /** Work another module started once this finished — Steam's achievements. */
    public String getFollowUpJobId() {
        return followUpJobId;
    }

    public void setFollowUp(SyncJob followUp) {
        this.followUpJobId = followUp.getId();
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
