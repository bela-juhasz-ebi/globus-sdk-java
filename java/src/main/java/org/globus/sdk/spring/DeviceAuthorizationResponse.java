package org.globus.sdk.spring;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representation of the response from the Globus Auth device authorization endpoint.
 */
public record DeviceAuthorizationResponse(
        @JsonProperty("device_code") String deviceCode,
        @JsonProperty("user_code") String userCode,
        @JsonProperty("verification_uri") String verificationUri,
        @JsonProperty("verification_uri_complete") String verificationUriComplete,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("interval") long interval,
        @JsonProperty("polling_message") String pollingMessage
) {}
