package dev.nexus.core.importing;

import dev.nexus.auth.CurrentUser;
import dev.nexus.config.NexusProperties;
import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.web.RateLimiter;
import dev.nexus.core.web.ServerTimings;
import dev.nexus.core.jobs.JobRegistry;
import dev.nexus.core.jobs.SyncJob;
import dev.nexus.modules.anime.AniListOAuthService;
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
            String id,
            String kind,
            String state,
            /** Which connection this run belongs to, so its progress is shown under that one. */
            String provider,
            /** Which stretch of the run the count belongs to; null for work with only one. */
            String phase,
            int total,
            int processed,
            int changed,
            String message,
            /** What the run produced, once it has. */
            Object report,
            /** Work that started when this finished — Steam's achievements after an import. */
            String followUpJobId) {

        static SyncJobResponse from(SyncJob job) {
            return new SyncJobResponse(
                    job.getId(),
                    job.getKind().name(),
                    job.getState().name(),
                    job.getProvider() == null ? null : job.getProvider().name(),
                    job.getPhase() == null ? null : job.getPhase().name(),
                    job.getTotal(),
                    job.getProcessed(),
                    job.getChanged(),
                    job.getMessage(),
                    job.getReport(),
                    job.getFollowUpJobId());
        }
    }

    /** The raw openid.* parameters, forwarded by the frontend for verification. */
    public record SteamCallbackRequest(@NotEmpty Map<String, String> params) {}

    /** The authorization code AniList hands back, forwarded for exchange server-side. */
    public record AniListCallbackRequest(@jakarta.validation.constraints.NotBlank String code) {}

    private final ExternalAccountService accounts;
    private final LibraryImportService importService;
    private final SteamOpenIdService steamOpenId;
    private final AniListOAuthService anilistOAuth;
    private final ImportRunner runner;
    private final JobRegistry jobs;
    private final RateLimiter rateLimiter;
    private final ServerTimings timings;
    private final String frontendUrl;
    private final int importsPerMinute;

    public IntegrationController(
            ExternalAccountService accounts,
            LibraryImportService importService,
            SteamOpenIdService steamOpenId,
            AniListOAuthService anilistOAuth,
            ImportRunner runner,
            JobRegistry jobs,
            RateLimiter rateLimiter,
            ServerTimings timings,
            NexusProperties properties) {
        this.accounts = accounts;
        this.importService = importService;
        this.steamOpenId = steamOpenId;
        this.anilistOAuth = anilistOAuth;
        this.runner = runner;
        this.jobs = jobs;
        this.rateLimiter = rateLimiter;
        this.timings = timings;
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

    /** Where to send the reader to approve the link. */
    @PostMapping("/anilist/authorize")
    public AuthorizeUrlResponse authorizeAniList(@AuthenticationPrincipal CurrentUser user) {
        return new AuthorizeUrlResponse(anilistOAuth.authorizationUrl(anilistRedirectUri()));
    }

    /**
     * Completes the link. AniList redirects the browser back with no Authorization header,
     * so the frontend forwards the code here on an authenticated request — which binds the
     * new link to the session that started it rather than to whoever opens the callback.
     */
    @PostMapping("/anilist/callback")
    public ConnectedAccount completeAniList(
            @AuthenticationPrincipal CurrentUser user,
            @org.springframework.validation.annotation.Validated @RequestBody AniListCallbackRequest request) {

        AniListOAuthService.Connection connection =
                anilistOAuth.exchangeCode(request.code(), anilistRedirectUri());

        return ConnectedAccount.from(accounts.connect(
                user.id(),
                Provider.ANILIST,
                connection.externalUserId(),
                connection.accessToken(),
                connection.refreshToken(),
                connection.expiresAt()));
    }

    private String anilistRedirectUri() {
        return frontendUrl + "/settings/anilist/callback";
    }

    /**
     * Starts an import and hands back a job to watch straight away.
     *
     * <p>A list of several hundred titles is minutes of work against someone else's rate
     * limit. Holding the request open for that invites a gateway timeout, and leaves a
     * reader with a spinner that cannot tell them whether anything is happening.
     *
     * <p>Only one import runs per user at a time: a second would repeat every upstream call
     * for no further result.
     */
    @PostMapping("/{provider}/import")
    public SyncJobResponse importLibrary(@AuthenticationPrincipal CurrentUser user, @PathVariable Provider provider) {
        // Throttled hard: each run costs external API budget and touches the whole library.
        rateLimiter.check("import:" + user.id(), importsPerMinute);

        ExternalAccount account = accounts.requireConnected(user.id(), provider);

        SyncJob job = jobs.runningFor(user.id(), SyncJob.Kind.IMPORT, provider)
                .orElseGet(() -> {
                    SyncJob started = jobs.start(user.id(), SyncJob.Kind.IMPORT, provider, 0);
                    runner.run(started, account);
                    return started;
                });

        return SyncJobResponse.from(job);
    }

    /** Whatever this reader has running, for an indicator that follows them across pages. */
    @GetMapping("/jobs/current")
    public SyncJobResponse currentJob(@AuthenticationPrincipal CurrentUser user) {
        return jobs.anyRunningFor(user.id()).map(SyncJobResponse::from).orElse(null);
    }

    /**
     * Calls off a running job. The work stops between items rather than mid-write, so
     * everything already imported stays imported.
     */
    @DeleteMapping("/jobs/{jobId}")
    public SyncJobResponse cancelJob(@AuthenticationPrincipal CurrentUser user, @PathVariable String jobId) {
        SyncJob job = jobs.find(jobId, user.id()).orElseThrow(SyncJobNotFoundException::new);
        job.cancel();
        return SyncJobResponse.from(job);
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
