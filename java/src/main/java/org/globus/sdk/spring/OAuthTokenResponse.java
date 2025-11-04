package org.globus.sdk.spring;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Representation of the OAuth token payload returned by Globus Auth.
 */
public record OAuthTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("scope") String scope,
        @JsonProperty("state") String state,
        @JsonProperty("resource_server") String resourceServer,
        @JsonProperty("token_id") String tokenId,
        @JsonProperty("expires_at_seconds") Long expiresAtSeconds,
        @JsonProperty("other_tokens") List<OAuthTokenResponse> otherTokens
) {}
