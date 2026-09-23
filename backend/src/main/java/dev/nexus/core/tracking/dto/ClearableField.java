package dev.nexus.core.tracking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** An entry field an update can empty, named on the wire as the request names the field itself. */
public enum ClearableField {
    @JsonProperty("startedAt")
    STARTED_AT,
    @JsonProperty("finishedAt")
    FINISHED_AT
}
