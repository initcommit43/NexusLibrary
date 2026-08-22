package dev.nexus.core.web;

import dev.nexus.auth.AuthenticationFailedException;
import dev.nexus.auth.RegistrationConflictException;
import dev.nexus.core.adapter.MetadataAdapterNotAvailableException;
import dev.nexus.core.cache.ItemNotFoundException;
import dev.nexus.core.tracking.EntryNotFoundException;
import dev.nexus.modules.games.IgdbUnavailableException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult()
                .getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(new ApiError("Please check the highlighted fields.", fieldErrors));
    }

    @ExceptionHandler(RegistrationConflictException.class)
    public ResponseEntity<ApiError> handleConflict(RegistrationConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(e.getMessage(), e.getFieldErrors()));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiError> handleAuthFailure(AuthenticationFailedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler({EntryNotFoundException.class, ItemNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(MetadataAdapterNotAvailableException.class)
    public ResponseEntity<ApiError> handleModuleUnavailable(MetadataAdapterNotAvailableException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ApiError("That module is not available yet."));
    }

    /** An upstream outage is not the client's fault, and its detail is not their business. */
    @ExceptionHandler(IgdbUnavailableException.class)
    public ResponseEntity<ApiError> handleUpstreamUnavailable(IgdbUnavailableException e) {
        log.warn("IGDB unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("The game database is unavailable right now. Please try again."));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiError("Too many attempts. Please wait a moment and try again."));
    }

    /**
     * Catch-all so an unexpected failure cannot escape as a stack trace. Detail goes to
     * the server log; the client gets a generic message and nothing else.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Something went wrong. Please try again."));
    }
}
