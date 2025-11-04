package org.globus.sdk.spring;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Container for a collection's access rules.
 */
public record AccessRuleList(
        @JsonProperty("DATA_TYPE") String dataType,
        @JsonProperty("DATA") List<AccessRule> rules,
        @JsonProperty("length") int length,
        @JsonProperty("endpoint") String endpoint
) {}
