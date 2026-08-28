package dev.nexus.core.preferences;

import dev.nexus.auth.CurrentUser;
import dev.nexus.core.domain.MediaType;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reader's own module switches.
 *
 * <p>Everything here is keyed by the authenticated reader and takes no id from the request,
 * so there is no id to tamper with: one reader cannot read or write another's settings.
 */
@Validated
@RestController
@RequestMapping("/settings/modules")
public class ModulePreferenceController {

    /** The media types switched off. Everything absent from this list is on. */
    public record ModulePreferences(@Size(max = 16) List<MediaType> disabled) {}

    private final ModulePreferenceService preferences;

    public ModulePreferenceController(ModulePreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    public ModulePreferences disabled(@AuthenticationPrincipal CurrentUser user) {
        return new ModulePreferences(preferences.asList(preferences.disabledFor(user.id())));
    }

    @PutMapping
    public ModulePreferences replace(
            @AuthenticationPrincipal CurrentUser user, @RequestBody ModulePreferences body) {

        return new ModulePreferences(
                preferences.asList(preferences.replaceFor(user.id(), body.disabled())));
    }
}
