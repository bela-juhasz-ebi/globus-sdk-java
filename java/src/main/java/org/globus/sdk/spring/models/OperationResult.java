package org.globus.sdk.spring;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic Globus API result envelope for mutation operations.
 */
public record OperationResult(
        @JsonProperty("DATA_TYPE") String dataType,
        @JsonProperty("code") String code,
        @JsonProperty("message") String message,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("resource") String resource
) {}
