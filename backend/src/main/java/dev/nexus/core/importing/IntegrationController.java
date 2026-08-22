package dev.nexus.core.importing;

import dev.nexus.auth.CurrentUser;
import dev.nexus.config.NexusProperties;
import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.web.RateLimiter;
import dev.nexus.core.jobs.JobRegistry;
import dev.nexus.core.jobs.SyncJob;
import dev.nexus.modules.games.AchievementSyncService;
import dev.nexus.modules.games.SteamOpenIdService;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integrations")
public class IntegrationController {

    public record ConnectedAccount(Provider provider, String externalUserId, Instant connectedAt, Instant lastSyncedAt) {

        static ConnectedAccount from(ExternalAccount account) {
            return new ConnectedAccount(
                    account.getProvider(),
                    account.getExternalUserId(),
                    account.getConnectedAt(),
                    account.getLastSyncedAt());
        }
    }

    public record AuthorizeUrlResponse(String url) {}

    public record SyncJobResponse(
            String id, String state, int total, int processed, int changed, String message) {

        static SyncJobResponse from(SyncJob job) {
            return new SyncJobResponse(
                    job.getId(),
                    job.getState().name(),
                    job.getTotal(),
                    job.getProcessed(),
                    job.getChanged(),
                    job.getMessage());
        }
    }

    /** The raw openid.* parameters, forwarded by the frontend for verification. */
    public record SteamCallbackRequest(@NotEmpty Map<String, String> params) {}

    private final ExternalAccountService accounts;
    private final LibraryImportService importService;
    private final SteamOpenIdService steamOpenId;
    private final AchievementSyncService achievements;
    private final JobRegistry jobs;
    private final RateLimiter rateLimiter;
    private final String frontendUrl;
    private final int importsPerMinute;

    public IntegrationController(
            ExternalAccountService accounts,
            LibraryImportService importService,
            SteamOpenIdService steamOpenId,
            AchievementSyncService achievements,
            JobRegistry jobs,
            RateLimiter rateLimiter,
            NexusProperties properties) {
        this.accounts = accounts;
        this.importService = importService;
        this.steamOpenId = steamOpenId;
        this.achievements = achievements;
        this.jobs = jobs;
        this.rateLimiter = rateLimiter;
        this.frontendUrl = properties.security().frontendUrl();
        this.importsPerMinute = properties.rateLimit().importRequestsPerMinute();
    }

    @GetMapping
    public List<ConnectedAccount> list(@AuthenticationPrincipal CurrentUser user) {
        return accounts.listFor(user.id()).stream().map(ConnectedAccount::from).toList();
    }

    /**
     * Returns the Steam sign-in URL. The return address is built from configuration rather
     * than taken from the caller, so this cannot be used to bounce a victim somewhere else.
     */
    @PostMapping("/steam/authorize")
    public AuthorizeUrlResponse authorizeSteam(@AuthenticationPrincipal CurrentUser user) {
        return new AuthorizeUrlResponse(
                steamOpenId
                        .authenticationUrl(frontendUrl + "/settings/steam/callback", frontendUrl)
                        .toString());
    }

    /**
     * Completes the sign-in. Steam redirects the browser back with no Authorization header,
     * so the frontend forwards the parameters here on an authenticated request instead —
     * which keeps the link bound to the session that started it rather than to whoever
     * happens to open the callback URL.
     */
    @PostMapping("/steam/callback")
    public ConnectedAccount completeSteam(
            @AuthenticationPrincipal CurrentUser user, @RequestBody SteamCallbackRequest request) {

        String steamId = steamOpenId
                .verifyCallback(request.params())
                .orElseThrow(() -> new SteamVerificationFailedException());

        return ConnectedAccount.from(accounts.connect(user.id(), Provider.STEAM, steamId));
    }

    @PostMapping("/{provider}/import")
    public ImportReport importLibrary(@AuthenticationPrincipal CurrentUser user, @PathVariable Provider provider) {
        // Throttled hard: each run costs external API budget and touches the whole library.
        rateLimiter.check("import:" + user.id(), importsPerMinute);

        return importService.importLibrary(accounts.requireConnected(user.id(), provider));
    }

    /**
     * Kicks off an achievement sync and returns immediately. Steam has no bulk endpoint
     * here, so this is one request per game and far too slow to hold a connection open for.
     */
    @PostMapping("/steam/achievements")
    public SyncJobResponse syncAchievements(@AuthenticationPrincipal CurrentUser user) {
        rateLimiter.check("achievements:" + user.id(), importsPerMinute);

        return SyncJobResponse.from(achievements.start(accounts.requireConnected(user.id(), Provider.STEAM)));
    }

    @GetMapping("/jobs/{jobId}")
    public SyncJobResponse job(@AuthenticationPrincipal CurrentUser user, @PathVariable String jobId) {
        return jobs.find(jobId, user.id())
                .map(SyncJobResponse::from)
                .orElseThrow(SyncJobNotFoundException::new);
    }

    @DeleteMapping("/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal CurrentUser user, @PathVariable Provider provider) {
        accounts.disconnect(user.id(), provider);
    }
}
