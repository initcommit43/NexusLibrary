package dev.nexus.core.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Client-facing error shape. Never carries exception types, stack traces or SQL. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String message, Map<String, String> fieldErrors) {

    public ApiError(String message) {
        this(message, Map.of());
    }
}
