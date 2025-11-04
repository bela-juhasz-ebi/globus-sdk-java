package org.globus.sdk.spring;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representation of a Globus guest collection as returned by the GCS Manager API.
 */
public record GuestCollection(
        @JsonProperty("DATA_TYPE") String dataType,
        @JsonProperty("id") String id,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("collection_type") String collectionType,
        @JsonProperty("mapped_collection_id") String mappedCollectionId,
        @JsonProperty("collection_base_path") String collectionBasePath,
        @JsonProperty("user_credential_id") String userCredentialId,
        @JsonProperty("owner_id") String ownerId,
        @JsonProperty("owner_string") String ownerString,
        @JsonProperty("public") Boolean isPublic
) {}
