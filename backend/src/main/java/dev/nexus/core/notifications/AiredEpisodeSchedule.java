package dev.nexus.core.notifications;

import dev.nexus.core.domain.TrackableItemRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Looks for aired episodes at the moment one is due, rather than every so often.
 *
 * <p>An episode airs on a timetable, and the timetable is already here: every ongoing title
 * carries the second its next episode lands, because a shelf counts down beside each row. So
 * the sweep is scheduled for that second instead of running on a fixed interval — a reader
 * whose episode aired at 19:00 is told at 19:00 and not at some point in the quarter hour
 * afterwards. It costs no more than the interval did: fewer wakings, and the same one query.
 *
 * <p>Kept apart from the detector, and switched off under test: a sweep that starts itself
 * midway through a test writes rows the test is about to truncate, and the two deadlock over
 * it. A test that wants a sweep calls for one.
 */
@Component
@ConditionalOnProperty(name = "nexus.notifications.sweep", matchIfMissing = true)
public class AiredEpisodeSchedule {

    private static final Logger log = LoggerFactory.getLogger(AiredEpisodeSchedule.class);

    /**
     * How far ahead a waking is allowed to be booked.
     *
     * <p>Nothing airing tonight does not mean nothing to do: a title tracked at midnight, or a
     * refresh that moves an airing time, both change the answer. Looking again on the hour
     * costs one query and keeps a long quiet stretch from going unattended.
     */
    private static final Duration LOOK_AHEAD = Duration.ofHours(1);

    /**
     * Never sooner than this, however overdue the next airing looks.
     *
     * <p>An episode whose moment has passed but whose item has not been refreshed yet would
     * otherwise reschedule the sweep onto itself as fast as the thread could run.
     */
    private static final Duration SOONEST = Duration.ofSeconds(30);

    /** Waited out so a restart does not spend its first second on this. */
    private static final Duration AFTER_STARTUP = Duration.ofSeconds(20);

    private final AiredEpisodeDetector detector;
    private final TrackableItemRepository items;
    private final TaskScheduler scheduler;

    public AiredEpisodeSchedule(
            AiredEpisodeDetector detector, TrackableItemRepository items, TaskScheduler scheduler) {
        this.detector = detector;
        this.items = items;
        this.scheduler = scheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.schedule(this::sweep, Instant.now().plus(AFTER_STARTUP));
    }

    /**
     * Sweeps, then books the next waking for whenever the next episode is due.
     *
     * <p>Rescheduled in a finally: a sweep that threw is still a sweep that has to happen
     * again, and a schedule that stops rescheduling itself stops for good.
     */
    public void sweep() {
        try {
            detector.sweep();
        } catch (RuntimeException e) {
            log.warn("Aired-episode sweep failed", e);
        } finally {
            scheduleNext();
        }
    }

    private void scheduleNext() {
        Instant now = Instant.now();
        Instant cap = now.plus(LOOK_AHEAD);

        Long due = items.nextAiringAfter(now.getEpochSecond());
        Instant next = due == null ? cap : Instant.ofEpochSecond(due);

        Instant at = next.isAfter(cap) ? cap : next;
        Instant soonest = now.plus(SOONEST);

        scheduler.schedule(this::sweep, at.isBefore(soonest) ? soonest : at);
    }
}
