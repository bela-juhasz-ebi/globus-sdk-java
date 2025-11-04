package org.globus.sdk.spring;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
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

    /**
     * Create a new guest collection rooted at {@code collectionBasePath} beneath {@code mappedCollectionId}.
     *
     * @param accessToken        OAuth access token with {@code gcs.manage_collections} on the mapped collection.
     * @param mappedCollectionId Identifier for the mapped collection that will host the new guest collection.
     * @param collectionBasePath Filesystem path within the mapped collection to expose.
     * @param displayName        Human readable name for the guest collection.
     * @param isPublic           Optional flag to mark the collection as publicly visible.
     * @return representation of the created guest collection.
     */
    public GuestCollection createGuestCollection(String accessToken,
                                                 String mappedCollectionId,
                                                 String collectionBasePath,
                                                 String displayName,
                                                 Boolean isPublic) {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(mappedCollectionId, "mappedCollectionId");
        Objects.requireNonNull(collectionBasePath, "collectionBasePath");
        Objects.requireNonNull(displayName, "displayName");

        EndpointDetails mappedCollection = fetchEndpoint(accessToken, mappedCollectionId);
        String gcsManagerUrl = requireGcsManagerUrl(mappedCollection, mappedCollectionId);

        GuestCollectionRequest request = new GuestCollectionRequest(
                "collection#1.8.0",
                "guest",
                displayName,
                mappedCollectionId,
                collectionBasePath,
                isPublic
        );

        return transferClient.post()
                .uri(resolveManagerUri(gcsManagerUrl, "/api/collections"))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GuestCollection.class);
    }

    /**
     * Grant an access rule on an endpoint or guest collection.
     *
     * @param accessToken  OAuth access token with sufficient permissions on the endpoint.
     * @param endpointId   Endpoint or collection identifier.
     * @param principalType Type of principal (e.g. {@code identity}, {@code group}).
     * @param principal    Identifier of the principal to grant access to.
     * @param path         Path (relative to the collection root) the rule should apply to.
     * @param permissions  Permission string, typically {@code r} or {@code rw}.
     * @param notifyEmail  Optional email address for notification.
     * @param notifyMessage Optional custom notification message.
     * @return the created access rule.
     */
    public AccessRule createPermission(String accessToken,
                                       String endpointId,
                                       String principalType,
                                       String principal,
                                       String path,
                                       String permissions,
                                       String notifyEmail,
                                       String notifyMessage) {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(endpointId, "endpointId");
        Objects.requireNonNull(principalType, "principalType");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(permissions, "permissions");

        AccessRuleRequest request = new AccessRuleRequest(
                "access",
                principalType,
                principal,
                path,
                permissions,
                notifyEmail,
                notifyMessage
        );

        return transferClient.post()
                .uri("/v0.10/endpoint/{endpointId}/access", endpointId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AccessRule.class);
    }

    /**
     * Convenience wrapper for creating identity based access rules.
     */
    public AccessRule createIdentityPermission(String accessToken,
                                               String endpointId,
                                               String identityId,
                                               String path,
                                               String permissions) {
        return createPermission(accessToken, endpointId, "identity", identityId, path, permissions, null, null);
    }

    /**
     * Fetch existing permissions on an endpoint or guest collection.
     */
    public AccessRuleList listPermissions(String accessToken, String endpointId) {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(endpointId, "endpointId");

        return transferClient.get()
                .uri("/v0.10/endpoint/{endpointId}/access_list", endpointId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(AccessRuleList.class);
    }

    /**
     * Delete a specific permission rule from an endpoint or guest collection.
     */
    public OperationResult deletePermission(String accessToken, String endpointId, String ruleId) {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(endpointId, "endpointId");
        Objects.requireNonNull(ruleId, "ruleId");

        return transferClient.delete()
                .uri("/v0.10/endpoint/{endpointId}/access/{ruleId}", endpointId, ruleId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(OperationResult.class);
    }

    /**
     * Delete a collection (mapped or guest) via the owning GCS manager.
     */
    public OperationResult deleteCollection(String accessToken, String collectionId) {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(collectionId, "collectionId");

        EndpointDetails endpoint = fetchEndpoint(accessToken, collectionId);
        String gcsManagerUrl = requireGcsManagerUrl(endpoint, collectionId);

        return transferClient.delete()
                .uri(resolveManagerUri(gcsManagerUrl, "/api/collections/" + collectionId))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(OperationResult.class);
    }

    private EndpointDetails fetchEndpoint(String accessToken, String endpointId) {
        return transferClient.get()
                .uri("/v0.10/endpoint/{endpointId}", endpointId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(EndpointDetails.class);
    }

    private String requireGcsManagerUrl(EndpointDetails details, String endpointId) {
        if (details == null || !StringUtils.hasText(details.gcsManagerUrl())) {
            throw new IllegalStateException("Endpoint " + endpointId + " does not expose a GCS manager URL");
        }
        return details.gcsManagerUrl();
    }

    private URI resolveManagerUri(String baseUrl, String path) {
        String sanitizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(sanitizedBase + path);
    }

    private record GuestCollectionRequest(
            @JsonProperty("DATA_TYPE") String dataType,
            @JsonProperty("collection_type") String collectionType,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("mapped_collection_id") String mappedCollectionId,
            @JsonProperty("collection_base_path") String collectionBasePath,
            @JsonProperty("public") Boolean isPublic
    ) {
    }

    private record AccessRuleRequest(
            @JsonProperty("DATA_TYPE") String dataType,
            @JsonProperty("principal_type") String principalType,
            @JsonProperty("principal") String principal,
            @JsonProperty("path") String path,
            @JsonProperty("permissions") String permissions,
            @JsonProperty("notify_email") String notifyEmail,
            @JsonProperty("notify_message") String notifyMessage
    ) {
    }

}
