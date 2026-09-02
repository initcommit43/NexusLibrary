package dev.nexus.core.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What an installed app asks on launch, and the only way to make one stop.
 *
 * <p>A web client updates the moment the backend does, so a breaking change costs nothing.
 * A phone keeps the build it was installed with for as long as its owner leaves it there,
 * and an App Store review sits between a fix and the people who need it. Publishing the
 * oldest build still served is what lets the app say "update to carry on" for itself, which
 * is the only alternative to answering an old build with errors it cannot explain.
 *
 * <p>Public, and deliberately: a build too old to be served is also too old to be asked for
 * a token first.
 */
@RestController
public class ClientVersionController {

    /** Semantic version. The app compares its own build against it and decides. */
    public record ClientVersion(String minimumVersion) {}

    private final ClientVersionProperties properties;

    public ClientVersionController(ClientVersionProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/client-version")
    public ClientVersion clientVersion() {
        return new ClientVersion(properties.minimum());
    }
}
