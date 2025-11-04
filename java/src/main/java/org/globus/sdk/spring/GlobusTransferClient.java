package org.globus.sdk.spring;

import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Objects;

/**
 * Thin wrapper around the Globus Transfer API.
 */
public class GlobusTransferClient {

    private final RestClient transferClient;

    public GlobusTransferClient(RestClient.Builder builder) {
        this(GlobusRestClientFactory.createTransferClient(builder));
    }

    public GlobusTransferClient(RestClient transferClient) {
        this.transferClient = Objects.requireNonNull(transferClient, "transferClient");
    }

    /**
     * List directory contents for a mapped collection.
     *
     * @param accessToken OAuth access token with {@code transfer.api.globus.org} scope.
     * @param endpointId  Globus collection (endpoint) identifier.
     * @param path        directory to list; omit or {@code null} to list the default path.
     * @return parsed directory listing.
     */
    public DirectoryListing listDirectory(String accessToken, String endpointId, String path) {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(endpointId, "endpointId");

        return transferClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/v0.10/endpoint/{endpointId}/ls");
                    if (StringUtils.hasText(path)) {
                        builder.queryParam("path", path);
                    }
                    return builder.build(endpointId);
                })
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(DirectoryListing.class);
    }
}
