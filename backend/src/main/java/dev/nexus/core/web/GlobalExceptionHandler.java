package dev.nexus.core.web;

import dev.nexus.auth.AuthenticationFailedException;
import dev.nexus.auth.RegistrationConflictException;
import dev.nexus.core.adapter.MetadataAdapterNotAvailableException;
import dev.nexus.core.cache.ItemNotFoundException;
import dev.nexus.core.importing.ExternalAccountNotConnectedException;
import dev.nexus.core.importing.ImportNotSupportedException;
import dev.nexus.core.importing.SteamVerificationFailedException;
import dev.nexus.core.importing.SyncJobNotFoundException;
import dev.nexus.core.importing.UpstreamUnavailableException;
import dev.nexus.core.review.ReviewNotFoundException;
import dev.nexus.core.tracking.EntryNotFoundException;
import dev.nexus.modules.anime.AniListNotConfiguredException;
import dev.nexus.modules.anime.AniListUnavailableException;
import dev.nexus.modules.film.TmdbUnavailableException;
import dev.nexus.modules.film.TraktNotConfiguredException;
import dev.nexus.modules.film.TraktReconnectRequiredException;
import dev.nexus.modules.film.TraktUnavailableException;
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

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiError> handleAuthFailure(AuthenticationFailedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(e.getMessage()));
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

    @ExceptionHandler(ImportNotSupportedException.class)
    public ResponseEntity<ApiError> handleImportUnsupported(ImportNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ApiError("Importing from that service is not available yet."));
    }

    @ExceptionHandler({
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
        TraktUnavailableException.class
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
    @ExceptionHandler(TraktNotConfiguredException.class)
    public ResponseEntity<ApiError> handleTraktNotConfigured(TraktNotConfiguredException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ApiError("Trakt is not configured on this server, so accounts cannot be connected."));
    }

    @ExceptionHandler(TraktReconnectRequiredException.class)
    public ResponseEntity<ApiError> handleTraktReconnectRequired(TraktReconnectRequiredException e) {
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
