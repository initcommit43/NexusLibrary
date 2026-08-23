package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.core.web.ServerTimings;
import org.junit.jupiter.api.Test;

class ServerTimingsTest {

    @Test
    void nothingMeasuredMeansNoHeaderAtAll() {
        assertThat(new ServerTimings().header()).isEmpty();
    }

    @Test
    void marksAreReportedInTheOrderTheyHappened() {
        ServerTimings timings = new ServerTimings();
        timings.record("item", 12);
        timings.record("detailFetch", 540);

        assertThat(timings.header()).contains("item;dur=12, detailFetch;dur=540");
    }

    /** Three calls to one source are one wait as far as reading the number goes. */
    @Test
    void repeatedMarksUnderOneNameAddUp() {
        ServerTimings timings = new ServerTimings();
        timings.record("detailFetch", 100);
        timings.record("detailFetch", 250);

        assertThat(timings.header()).contains("detailFetch;dur=350");
    }

    @Test
    void timingAStepHandsBackWhatItProduced() {
        ServerTimings timings = new ServerTimings();

        String result = timings.time("work", () -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(timings.header()).isPresent();
    }
}
