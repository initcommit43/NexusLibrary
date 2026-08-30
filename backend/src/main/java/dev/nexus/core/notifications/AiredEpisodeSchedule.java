package dev.nexus.core.notifications;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Looks for aired episodes every quarter of an hour.
 *
 * <p>An episode airs on a timetable rather than on a request, so this is the one part of the
 * app that has to look without being asked.
 *
 * <p>Kept apart from the detector, and switched off under test: a sweep that starts itself
 * midway through a test writes rows the test is about to truncate, and the two deadlock over
 * it. A test that wants a sweep calls for one.
 */
@Component
@ConditionalOnProperty(name = "nexus.notifications.sweep", matchIfMissing = true)
public class AiredEpisodeSchedule {

    private static final long EVERY_QUARTER_HOUR_MS = 15 * 60 * 1000L;

    /**
     * Waited out before the first sweep, so a restart does not spend its first second on this.
     * A start-up is the busiest the app ever is, and an episode that aired can wait a minute.
     */
    private static final long AFTER_STARTUP_MS = 60 * 1000L;

    private final AiredEpisodeDetector detector;

    public AiredEpisodeSchedule(AiredEpisodeDetector detector) {
        this.detector = detector;
    }

    @Scheduled(initialDelay = AFTER_STARTUP_MS, fixedDelay = EVERY_QUARTER_HOUR_MS)
    public void sweep() {
        detector.sweep();
    }
}
