package dev.nexus.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where a native client says which device it is on, for push notifications to come.
 *
 * <p>The token travels in the body on both routes, never in the path: a path ends up in access
 * logs, and a push token is not something to leave in one.
 */
@RestController
@RequestMapping("/devices")
public class DeviceTokenController {

    /** Room for either platform's token as it stands today with a wide margin; APNs's is 64 hex. */
    private static final int MAX_TOKEN = 512;

    public record DeviceRegistration(
            @NotBlank @Size(max = MAX_TOKEN) String token, @NotNull DevicePlatform platform) {}

    public record DeviceUnregistration(@NotBlank @Size(max = MAX_TOKEN) String token) {}

    private final DeviceTokenService devices;

    public DeviceTokenController(DeviceTokenService devices) {
        this.devices = devices;
    }

    /** Idempotent: the app calls this on every cold start, and a repeat only refreshes the row. */
    @PutMapping
    public ResponseEntity<Void> register(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody DeviceRegistration registration) {
        devices.register(user.id(), registration.token(), registration.platform());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unregister(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody DeviceUnregistration unregistration) {
        devices.unregister(user.id(), unregistration.token());
        return ResponseEntity.noContent().build();
    }
}
