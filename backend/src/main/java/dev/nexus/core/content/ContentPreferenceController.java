package dev.nexus.core.content;

import dev.nexus.auth.CurrentUser;
import dev.nexus.core.content.ContentPreferences.Visibility;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reader's own adult-content switches. Keyed by the authenticated reader and taking no id
 * from the request, like the module switches.
 */
@Validated
@RestController
@RequestMapping("/settings/content")
public class ContentPreferenceController {

    /** Either switch, or both. Null leaves a switch as it is, so the page sends only what moved. */
    public record ContentUpdate(Boolean showAdult, Boolean blurAdult) {}

    private final ContentPreferences preferences;

    public ContentPreferenceController(ContentPreferences preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    public Visibility current(@AuthenticationPrincipal CurrentUser user) {
        return preferences.forUser(user.id());
    }

    @PatchMapping
    public Visibility update(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ContentUpdate body) {
        return preferences.update(user.id(), body.showAdult(), body.blurAdult());
    }
}
