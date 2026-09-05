package dev.nexus.core.web;

import dev.nexus.auth.AuthenticationFailedException;
import dev.nexus.auth.PasswordResetLinkExpiredException;
import dev.nexus.auth.PasswordResetUnavailableException;
import dev.nexus.auth.RegistrationConflictException;
import dev.nexus.core.account.AccountNotFoundException;
import dev.nexus.core.account.PasswordMismatchException;
import dev.nexus.core.adapter.MetadataAdapterNotAvailableException;
import dev.nexus.core.cache.ItemNotFoundException;
import dev.nexus.core.importing.ExternalAccountNotConnectedException;
import dev.nexus.core.importing.CsvFormatException;
import dev.nexus.core.importing.ImportNotSupportedException;
import dev.nexus.core.importing.SteamVerificationFailedException;
import dev.nexus.core.importing.SyncJobNotFoundException;
import dev.nexus.core.importing.UpstreamUnavailableException;
import dev.nexus.core.preferences.BannerNotSetException;
import dev.nexus.core.preferences.NoBannerException;
import dev.nexus.core.review.ReviewNotFoundException;
import dev.nexus.core.review.ReviewNotStartedException;
import dev.nexus.auth.RegistrationClosedException;
import dev.nexus.core.activity.ActivityNotFoundException;
import dev.nexus.core.tracking.EntryNotFoundException;
import dev.nexus.modules.anime.AniListNotConfiguredException;
import dev.nexus.modules.anime.AniListUnavailableException;
import dev.nexus.modules.film.SimklNotConfiguredException;
import dev.nexus.modules.film.SimklReconnectRequiredException;
import dev.nexus.modules.film.SimklUnavailableException;
import dev.nexus.modules.film.TmdbUnavailableException;
import dev.nexus.modules.games.IgdbUnavailableException;
import dev.nexus.modules.games.SteamProfileNotPublicException;
import dev.nexus.modules.games.SteamProfilePrivateException;
import dev.nexus.modules.games.SteamUnavailableException;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Extends Spring's handler so its standard MVC exceptions keep their correct statuses.
 * Handling only {@code Exception} here would flatten a missing parameter or an unsupported
 * method into a 500 and report the caller's mistake as a server fault.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult()
                .getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(new ApiError("Please check the highlighted fields.", fieldErrors));
    }

    /** Malformed JSON, or a value outside an enum. The detail names internal types, so it stays server-side. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        log.debug("Rejected unreadable request body", e);
        return ResponseEntity.badRequest()
                .body(new ApiError("The request body is malformed or contains an invalid value."));
    }

    /** Raised by {@code @Validated} constraints on query parameters. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException e) {
        return ResponseEntity.badRequest().body(new ApiError("Please check the request parameters."));
    }

    @ExceptionHandler(RegistrationConflictException.class)
    public ResponseEntity<ApiError> handleConflict(RegistrationConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(e.getMessage(), e.getFieldErrors()));
    }

    /**
     * A wrong confirmation password on a change the caller is otherwise entitled to make.
     * Not a 401: the session is fine, and answering with one would sign them out of a page
     * they are still signed into.
     */
    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<ApiError> handlePasswordMismatch(PasswordMismatchException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(e.getMessage(), Map.of("password", e.getMessage())));
    }

    /** A token outliving its account: the session is real, the account behind it is not. */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiError> handleMissingAccount(AccountNotFoundException e) {
        log.warn("Request for an account that no longer exists: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("Please sign in again."));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiError> handleAuthFailure(AuthenticationFailedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(e.getMessage()));
    }

    /**
     * A dead reset link, and deliberately not a 401: nobody is signed in on that page, and the
     * client treats a 401 as its own session ending — it would try to refresh, fail, and report
     * a lost session instead of the one thing the reader needs to be told, which is to ask for
     * another link. The message is the same for unknown, spent and expired; which of the three
     * it was is not the caller's business.
     *
     * <p>No field error either: the token is in the URL, not in a box the reader can correct,
     * and a client that shows field errors inline would have nowhere to put it.
     */
    @ExceptionHandler(PasswordResetLinkExpiredException.class)
    public ResponseEntity<ApiError> handleExpiredResetLink(PasswordResetLinkExpiredException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
    }

    /** No sender is configured, so no link can be issued. A deployment fault, said plainly. */
    @ExceptionHandler(PasswordResetUnavailableException.class)
    public ResponseEntity<ApiError> handleResetUnavailable(PasswordResetUnavailableException e) {
        log.warn("A password reset was asked for but no mailer is configured on this deployment");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ApiError(e.getMessage()));
    }

    /**
     * A private Steam profile is a setting the user controls, not a fault. Say exactly what
     * to change rather than reporting a generic failure.
     */
    @ExceptionHandler(SteamProfilePrivateException.class)
    public ResponseEntity<ApiError> handlePrivateProfile(SteamProfilePrivateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("Steam returned no games. Set \"Game details\" to Public in your "
                        + "Steam privacy settings, then try again."));
    }

    /**
     * A different setting from the private-library case, so it names a different fix.
     * Sending someone to change "Game details" when the profile is the problem wastes
     * their time and makes the app look broken.
     */
    @ExceptionHandler(SteamProfileNotPublicException.class)
    public ResponseEntity<ApiError> handleProfileNotPublic(SteamProfileNotPublicException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("Steam only shares achievements for public profiles. Set "
                        + "\"My profile\" to Public in your Steam privacy settings, then try again."));
    }

    @ExceptionHandler(SteamVerificationFailedException.class)
    public ResponseEntity<ApiError> handleSteamVerification(SteamVerificationFailedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(e.getMessage()));
    }

    /**
     * A file the reader can replace, so it says which columns were wanted rather than only
     * that parsing failed — and it answers now, while they are still looking at the upload
     * button, instead of minutes later as a failed background job.
     */
    @ExceptionHandler(CsvFormatException.class)
    public ResponseEntity<ApiError> handleCsvFormat(CsvFormatException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.advice()));
    }

    @ExceptionHandler(ImportNotSupportedException.class)
    public ResponseEntity<ApiError> handleImportUnsupported(ImportNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ApiError("Importing from that service is not available yet."));
    }

    @ExceptionHandler(dev.nexus.core.exporting.ExportNotSupportedException.class)
    public ResponseEntity<ApiError> handleExportUnsupported(dev.nexus.core.exporting.ExportNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(ReviewNotStartedException.class)
    public ResponseEntity<ApiError> handleReviewNotStarted(ReviewNotStartedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(e.getMessage()));
    }

    /**
     * Closed rather than broken, and said plainly: someone who cannot sign up should be told
     * that the app is not taking accounts, not left guessing at a form that refuses them.
     */
    @ExceptionHandler(RegistrationClosedException.class)
    public ResponseEntity<ApiError> handleRegistrationClosed(RegistrationClosedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(NoBannerException.class)
    public ResponseEntity<ApiError> handleNoBanner(NoBannerException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler({
        ActivityNotFoundException.class,
        BannerNotSetException.class,
        EntryNotFoundException.class,
        ItemNotFoundException.class,
        ExternalAccountNotConnectedException.class,
        ReviewNotFoundException.class,
        SyncJobNotFoundException.class
    })
    public ResponseEntity<ApiError> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(MetadataAdapterNotAvailableException.class)
    public ResponseEntity<ApiError> handleModuleUnavailable(MetadataAdapterNotAvailableException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ApiError("That module is not available yet."));
    }

    /**
     * An upstream outage is not the client's fault, and the technical detail is not their
     * business — but whose outage it is, and what the service said for itself, are. One
     * handler for every provider, so a new module's outage speaks the same way by
     * implementing the interface rather than by editing core.
     */
    @ExceptionHandler({
        IgdbUnavailableException.class,
        AniListUnavailableException.class,
        SteamUnavailableException.class,
        dev.nexus.modules.anime.MalUnavailableException.class,
        TmdbUnavailableException.class,
        SimklUnavailableException.class
    })
    public ResponseEntity<ApiError> handleUpstreamUnavailable(RuntimeException e) {
        UpstreamUnavailableException down = (UpstreamUnavailableException) e;
        log.warn("{} unavailable: {}", down.serviceName(), e.getMessage());

        String message = down.serviceName() + " is not answering right now. Please try again later.";
        String withWords = down.serviceSays()
                .map(words -> message + " " + down.serviceName() + " says: “" + words + "”")
                .orElse(message);

        // The service travels as data too, so the client can raise its outage banner
        // without parsing the sentence it shows the reader.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.upstreamOutage(withWords, down.serviceName()));
    }

    /**
     * Missing credentials are a deployment problem, not a fault the reader can retry away —
     * so it says what is wrong instead of hiding behind a generic failure.
     */
    @ExceptionHandler(AniListNotConfiguredException.class)
    public ResponseEntity<ApiError> handleAniListNotConfigured(AniListNotConfiguredException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ApiError("AniList is not configured on this server, so accounts cannot be connected."));
    }

    @ExceptionHandler(dev.nexus.modules.anime.MalNotConfiguredException.class)
    public ResponseEntity<ApiError> handleMalNotConfigured(dev.nexus.modules.anime.MalNotConfiguredException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ApiError("MyAnimeList is not configured on this server, so accounts cannot be connected."));
    }

    /** The PKCE verifier is gone — expired or already spent. Starting over mints a new one. */
    @ExceptionHandler(dev.nexus.modules.anime.MalAuthorizationExpiredException.class)
    public ResponseEntity<ApiError> handleMalAuthorizationExpired(
            dev.nexus.modules.anime.MalAuthorizationExpiredException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("The MyAnimeList link attempt expired. Start it again from settings."));
    }

    /** Dead tokens are the reader's to renew: only they can go through the approval again. */
    @ExceptionHandler(SimklNotConfiguredException.class)
    public ResponseEntity<ApiError> handleSimklNotConfigured(SimklNotConfiguredException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ApiError("Simkl is not configured on this server, so accounts cannot be connected."));
    }

    @ExceptionHandler(SimklReconnectRequiredException.class)
    public ResponseEntity<ApiError> handleSimklReconnectRequired(SimklReconnectRequiredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(e.advice()));
    }

    @ExceptionHandler(dev.nexus.modules.anime.MalReconnectRequiredException.class)
    public ResponseEntity<ApiError> handleMalReconnectRequired(
            dev.nexus.modules.anime.MalReconnectRequiredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(e.advice()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiError("Too many attempts. Please wait a moment and try again."));
    }

    /**
     * Catch-all so an unexpected failure cannot escape as a stack trace. Detail goes to the
     * server log; the client gets a generic message and nothing else.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Something went wrong. Please try again."));
    }
}
