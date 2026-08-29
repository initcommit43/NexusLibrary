package dev.nexus.core.activity;

import dev.nexus.auth.CurrentUser;
import dev.nexus.core.domain.Activity;
import dev.nexus.core.domain.ActivityType;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.TrackableItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/activity")
public class ActivityController {

    private static final int DEFAULT_LIMIT = 50;

    /** About seven months, which is as much as fits a map read across a column of a page. */
    private static final int DEFAULT_WEEKS = 30;

    /** One square of the map: the day, and how much happened on it. */
    public record DayResponse(LocalDate date, long amount) {}

    public record ActivityResponse(
            Long id,
            ActivityType type,
            MediaType mediaType,
            String title,
            String coverUrl,
            String externalId,
            Map<String, Object> payload,
            Instant createdAt) {

        /**
         * An import or a sync happened to a run rather than to a title, so it carries no item
         * and answers with nulls where a title's own event answers with its cover and name.
         * What it touched is in the payload, which every event has.
         */
        static ActivityResponse from(Activity activity) {
            TrackableItem item = activity.getItem();

            return new ActivityResponse(
                    activity.getId(),
                    activity.getType(),
                    item == null ? null : item.getMediaType(),
                    item == null ? null : item.getTitle(),
                    item == null ? null : item.getCoverUrl(),
                    item == null ? null : item.getExternalId(),
                    activity.getPayload(),
                    activity.getCreatedAt());
        }
    }

    private final ActivityRecorder activity;
    private final HistoryService history;

    public ActivityController(ActivityRecorder activity, HistoryService history) {
        this.activity = activity;
        this.history = history;
    }

    @GetMapping
    public List<ActivityResponse> feed(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Positive @Max(200) int limit) {

        return activity.feedFor(user.id(), limit).stream()
                .map(ActivityResponse::from)
                .toList();
    }

    /**
     * The days behind today that saw anything, for the map at the head of a profile. Only
     * days with something on them are sent; the map draws the blanks itself, and a year of
     * mostly-empty squares is not worth the wire.
     */
    @GetMapping("/history")
    public List<DayResponse> history(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "" + DEFAULT_WEEKS) @Positive @Max(104) int weeks,
            @RequestParam(required = false) List<MediaType> mediaTypes) {

        return history.since(user.id(), LocalDate.now().minusWeeks(weeks), mediaTypes).stream()
                .map(day -> new DayResponse(day.getDay(), day.getAmount()))
                .toList();
    }
}
