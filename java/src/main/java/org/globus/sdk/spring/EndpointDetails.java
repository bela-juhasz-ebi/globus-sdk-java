package org.globus.sdk.spring;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal endpoint metadata required for follow-up GCS manager calls.
 */
public record EndpointDetails(
        @JsonProperty("id") String id,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("collection_type") String collectionType,
        @JsonProperty("gcs_manager_url") String gcsManagerUrl
) {}
