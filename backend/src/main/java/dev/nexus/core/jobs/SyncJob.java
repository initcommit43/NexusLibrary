package dev.nexus.core.jobs;

import dev.nexus.core.domain.Provider;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** A long-running background sync, and how far it has got. */
public class SyncJob {

    public enum State {
        RUNNING,
        COMPLETE,
        FAILED,
        CANCELLED
    }

    public enum Kind {
        IMPORT,
        ACHIEVEMENTS,
        /** A provider's own record of what a reader did, day by day, behind their library. */
        ACTIVITY
    }

    /**
     * Which stretch of an import a run is in. The three cost wildly different amounts of
     * time and none of them can stand for the others: pulling a list is two calls, matching
     * it against the catalogue is one call per fifty unseen titles, and writing the entries
     * is local. A single bar over all three would spend a minute at nothing and then cross
     * the finish in one step, which is what a reader would call frozen.
     */
    public enum Phase {
        FETCHING,
        MATCHING,
        IMPORTING
    }

    private final String id = UUID.randomUUID().toString();
    private final Long userId;
    private final Kind kind;
    /** Which service this run is talking to: two providers' imports are separate work. */
    private final Provider provider;
    private final Instant startedAt = Instant.now();
    private final AtomicInteger processed = new AtomicInteger();
    private final AtomicInteger changed = new AtomicInteger();

    private volatile int total;
    private volatile Phase phase;
    private volatile State state = State.RUNNING;
    private volatile Object report;
    private volatile String followUpJobId;
    private volatile String message;
    /** Set when the failure was an upstream outage: the service's name, in the reader's terms. */
    private volatile String unavailableService;
    private volatile Instant finishedAt;

    private volatile boolean cancelled;

    public SyncJob(Long userId, Kind kind, Provider provider, int total) {
        this.userId = userId;
        this.kind = kind;
        this.provider = provider;
        this.total = total;
    }

    public Provider getProvider() {
        return provider;
    }

    /**
     * Asks the run to stop. It is a request rather than an interruption: the work checks
     * between items, so whatever already landed stays landed and nothing is half-written.
     */
    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void markCancelled(String message) {
        this.state = State.CANCELLED;
        this.message = message;
        this.finishedAt = Instant.now();
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

    /**
     * A failure that was the upstream's outage rather than anyone's mistake. Recorded as
     * data alongside the message so a client can raise its banner without parsing prose.
     */
    public void failUpstream(String serviceName, String message) {
        this.unavailableService = serviceName;
        fail(message);
    }

    public String getUnavailableService() {
        return unavailableService;
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

    public Phase getPhase() {
        return phase;
    }

    /**
     * Moves the run into a phase and starts its count again from nothing. Each phase counts
     * its own units — titles pulled, titles matched, entries written — so carrying the
     * previous count over would only misreport the new one.
     */
    public void beginPhase(Phase phase, int total) {
        reportPhase(phase, 0, total);
    }

    /** Where a phase has got to, for work counted somewhere other than {@link #advance}. */
    public void reportPhase(Phase phase, int processed, int total) {
        this.phase = phase;
        this.total = total;
        this.processed.set(processed);
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
