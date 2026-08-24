package dev.nexus.core.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Client-facing error shape. Never carries exception types, stack traces or SQL.
 *
 * <p>{@code unavailableService} is set only when the failure is an upstream outage, naming
 * the service in the reader's terms. It exists so the client can raise one banner for a
 * provider being down instead of parsing prose out of the message — structured for the
 * machine, worded for the person.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String message, Map<String, String> fieldErrors, String unavailableService) {

    public ApiError(String message) {
        this(message, Map.of(), null);
    }

    public ApiError(String message, Map<String, String> fieldErrors) {
        this(message, fieldErrors, null);
    }

    public static ApiError upstreamOutage(String message, String serviceName) {
        return new ApiError(message, Map.of(), serviceName);
    }
}
