package dev.nexus.core.web;

/**
 * A JSON body read past the configured cap. Raised while the body is being read, which is
 * what covers chunked transfer and any other request that never declares a {@code
 * Content-Length} — a missing header must not read as "unlimited".
 *
 * <p>A request whose declared length is already over the cap never reaches here: {@link
 * RequestSizeLimitFilter} refuses those before a byte of the body is read.
 */
public class RequestTooLargeException extends RuntimeException {

    public RequestTooLargeException() {
        super("Request body exceeds the configured limit");
    }
}
