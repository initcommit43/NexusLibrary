package dev.nexus.modules.games;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Phone games stay out; anything that also exists elsewhere stays in. */
class IgdbMobileTest {

    @Test
    void aGameOnlyOnPhonesIsDropped() {
        assertThat(IgdbMobile.isNotMobileOnly(on(34, 39))).isFalse();
        assertThat(IgdbMobile.isNotMobileOnly(on(39))).isFalse();
        assertThat(IgdbMobile.isNotMobileOnly(on(74))).isFalse();
    }

    /** A phone port is not a phone game: half the shelf would go with it. */
    @Test
    void aGameWithAPhonePortIsKept() {
        assertThat(IgdbMobile.isNotMobileOnly(on(6, 34, 39))).isTrue();
    }

    @Test
    void aGameOnConsolesOrPcIsKept() {
        assertThat(IgdbMobile.isNotMobileOnly(on(48))).isTrue();
        assertThat(IgdbMobile.isNotMobileOnly(on(6))).isTrue();
    }

    /** Unknown is not mobile: an unannounced game often lists no platform at all. */
    @Test
    void aGameWithNoPlatformsAtAllIsKept() {
        assertThat(IgdbMobile.isNotMobileOnly(Map.of("name", "Something coming"))).isTrue();
        assertThat(IgdbMobile.isNotMobileOnly(Map.of("platforms", List.of()))).isTrue();
    }

    /** IGDB answers with ids alone where the caller did not expand the platform. */
    @Test
    void platformsAreReadAsIdsOrAsObjects() {
        assertThat(IgdbMobile.isNotMobileOnly(Map.of("platforms", List.of(34, 39)))).isFalse();
        assertThat(IgdbMobile.isNotMobileOnly(Map.of("platforms", List.of(34, 6)))).isTrue();
    }

    private static Map<String, Object> on(int... platformIds) {
        return Map.of(
                "platforms",
                java.util.Arrays.stream(platformIds)
                        .mapToObj(id -> Map.<String, Object>of("id", id, "name", "Platform " + id))
                        .toList());
    }
}
