package dev.nexus.core.activity;

import dev.nexus.auth.CurrentUser;
import dev.nexus.core.domain.Activity;
import dev.nexus.core.domain.ActivityType;
import dev.nexus.core.domain.MediaType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
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

    public record ActivityResponse(
            Long id,
            ActivityType type,
            MediaType mediaType,
            String title,
            String coverUrl,
            String externalId,
            Map<String, Object> payload,
            Instant createdAt) {

        static ActivityResponse from(Activity activity) {
            return new ActivityResponse(
                    activity.getId(),
                    activity.getType(),
                    activity.getItem().getMediaType(),
                    activity.getItem().getTitle(),
                    activity.getItem().getCoverUrl(),
                    activity.getItem().getExternalId(),
                    activity.getPayload(),
                    activity.getCreatedAt());
        }
    }

    private final ActivityRecorder activity;

    public ActivityController(ActivityRecorder activity) {
        this.activity = activity;
    }

    @GetMapping
    public List<ActivityResponse> feed(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Positive @Max(200) int limit) {

        return activity.feedFor(user.id(), limit).stream()
                .map(ActivityResponse::from)
                .toList();
    }
}
