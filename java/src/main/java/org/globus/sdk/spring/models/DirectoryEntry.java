package org.globus.sdk.spring;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Single entry returned in a Globus Transfer directory listing.
 */
public record DirectoryEntry(
        @JsonProperty("name") String name,
        @JsonProperty("type") String type,
        @JsonProperty("size") Long size,
        @JsonProperty("last_modified") String lastModified,
        @JsonProperty("link") Boolean link
) {}
