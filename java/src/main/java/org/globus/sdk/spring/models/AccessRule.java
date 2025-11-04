package org.globus.sdk.spring;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Access control rule associated with a Globus collection.
 */
public record AccessRule(
        @JsonProperty("DATA_TYPE") String dataType,
        @JsonProperty("id") String id,
        @JsonProperty("role_id") String roleId,
        @JsonProperty("role_type") String roleType,
        @JsonProperty("principal_type") String principalType,
        @JsonProperty("principal") String principal,
        @JsonProperty("path") String path,
        @JsonProperty("permissions") String permissions,
        @JsonProperty("create_time") String createTime,
        @JsonProperty("expiration_date") String expirationDate,
        @JsonProperty("notify_email") String notifyEmail,
        @JsonProperty("notify_message") String notifyMessage
) {}
