package org.globus.sdk.spring;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response container for Globus Transfer directory listings.
 */
public record DirectoryListing(
        @JsonProperty("path") String path,
        @JsonProperty("endpoint") String endpoint,
        @JsonProperty("length") int length,
        @JsonProperty("DATA") List<DirectoryEntry> entries
) {}
