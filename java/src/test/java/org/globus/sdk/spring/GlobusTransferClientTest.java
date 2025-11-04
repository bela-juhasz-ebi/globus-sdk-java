package org.globus.sdk.spring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class GlobusTransferClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private GlobusTransferClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder)
                .ignoreExpectOrder(true)
                .build();
        client = new GlobusTransferClient(builder);
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void listDirectoryReturnsEntries() {
        String endpointId = "14a0be5f-226c-49fe-b65f-dba083d67fc3";
        server.expect(ExpectedCount.once(),
                        MockRestRequestMatchers.requestTo("https://transfer.api.globus.org/v0.10/endpoint/" + endpointId + "/ls?path=%2Ftest"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer access-token"))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "path": "/test",
                          "endpoint": "%s",
                          "length": 1,
                          "DATA": [
                            {
                              "name": "subdir",
                              "type": "dir",
                              "size": null,
                              "last_modified": "2024-04-25 14:42:11+00:00",
                              "link": false
                            }
                          ]
                        }
                        """.formatted(endpointId), MediaType.APPLICATION_JSON));

        DirectoryListing listing = client.listDirectory("access-token", endpointId, "/test");

        assertThat(listing.endpoint()).isEqualTo(endpointId);
        assertThat(listing.entries()).singleElement()
                .extracting(DirectoryEntry::name)
                .isEqualTo("subdir");
    }

    @Test
    void createGuestCollectionUsesManagerApi() {
        String mappedCollectionId = "14a0be5f-226c-49fe-b65f-dba083d67fc3";
        server.expect(ExpectedCount.once(),
                        MockRestRequestMatchers.requestTo("https://transfer.api.globus.org/v0.10/endpoint/" + mappedCollectionId))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer admin-token"))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "id": "%s",
                          "gcs_manager_url": "https://gcs.example"
                        }
                        """.formatted(mappedCollectionId), MediaType.APPLICATION_JSON));

        server.expect(ExpectedCount.once(),
                        MockRestRequestMatchers.requestTo("https://gcs.example/api/collections"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer admin-token"))
                .andExpect(MockRestRequestMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockRestRequestMatchers.content().json("""
                        {
                          "DATA_TYPE": "collection#1.8.0",
                          "collection_type": "guest",
                          "display_name": "Test Guest",
                          "mapped_collection_id": "%s",
                          "collection_base_path": "/nfs/path",
                          "public": true
                        }
                        """.formatted(mappedCollectionId), false))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "DATA_TYPE": "collection#1.8.0",
                          "id": "guest-collection-id",
                          "display_name": "Test Guest",
                          "collection_type": "guest",
                          "mapped_collection_id": "%s",
                          "collection_base_path": "/nfs/path"
                        }
                        """.formatted(mappedCollectionId), MediaType.APPLICATION_JSON));

        GuestCollection collection = client.createGuestCollection(
                "admin-token",
                mappedCollectionId,
                "/nfs/path",
                "Test Guest",
                true
        );

        assertThat(collection.id()).isEqualTo("guest-collection-id");
        assertThat(collection.displayName()).isEqualTo("Test Guest");
    }

    @Test
    void createPermissionPostsRule() {
        String endpointId = "7667d448-6893-4716-83f0-51b35cb69427";
        server.expect(ExpectedCount.once(),
                        MockRestRequestMatchers.requestTo("https://transfer.api.globus.org/v0.10/endpoint/" + endpointId + "/access"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer transfer-token"))
                .andExpect(MockRestRequestMatchers.content().json("""
                        {
                          "DATA_TYPE": "access",
                          "principal_type": "identity",
                          "principal": "asilva@ebi.ac.uk",
                          "path": "/",
                          "permissions": "r"
                        }
                        """, false))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "DATA_TYPE": "access#1.0.0",
                          "id": "d971bb8a-a363-11f0-b7bd-0e1cc5cf4f03",
                          "principal_type": "identity",
                          "principal": "asilva@ebi.ac.uk",
                          "path": "/",
                          "permissions": "r"
                        }
                        """, MediaType.APPLICATION_JSON));

        AccessRule rule = client.createIdentityPermission(
                "transfer-token",
                endpointId,
                "asilva@ebi.ac.uk",
                "/",
                "r"
        );

        assertThat(rule.id()).isEqualTo("d971bb8a-a363-11f0-b7bd-0e1cc5cf4f03");
        assertThat(rule.permissions()).isEqualTo("r");
    }

    @Test
    void listPermissionsFetchesRules() {
        String endpointId = "7667d448-6893-4716-83f0-51b35cb69427";
        server.expect(ExpectedCount.once(),
                        MockRestRequestMatchers.requestTo("https://transfer.api.globus.org/v0.10/endpoint/" + endpointId + "/access_list"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer transfer-token"))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "DATA_TYPE": "access#list",
                          "endpoint": "%s",
                          "length": 1,
                          "DATA": [
                            {
                              "id": "d971bb8a-a363-11f0-b7bd-0e1cc5cf4f03",
                              "principal_type": "identity",
                              "principal": "asilva@ebi.ac.uk",
                              "path": "/",
                              "permissions": "r"
                            }
                          ]
                        }
                        """.formatted(endpointId), MediaType.APPLICATION_JSON));

        AccessRuleList rules = client.listPermissions("transfer-token", endpointId);

        assertThat(rules.length()).isEqualTo(1);
        assertThat(rules.rules()).singleElement()
                .extracting(AccessRule::principal)
                .isEqualTo("asilva@ebi.ac.uk");
    }

    @Test
    void deletePermissionRemovesRule() {
        String endpointId = "7667d448-6893-4716-83f0-51b35cb69427";
        String ruleId = "d971bb8a-a363-11f0-b7bd-0e1cc5cf4f03";
        server.expect(ExpectedCount.once(),
                        MockRestRequestMatchers.requestTo("https://transfer.api.globus.org/v0.10/endpoint/" + endpointId + "/access/" + ruleId))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.DELETE))
                .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer transfer-token"))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "DATA_TYPE": "result#1.0.0",
                          "code": "Deleted",
                          "message": "Access rule deleted successfully",
                          "resource": "%s:%s"
                        }
                        """.formatted(endpointId, ruleId), MediaType.APPLICATION_JSON));

        OperationResult result = client.deletePermission("transfer-token", endpointId, ruleId);

        assertThat(result.code()).isEqualTo("Deleted");
        assertThat(result.resource()).contains(ruleId);
    }

    @Test
    void deleteCollectionUsesManager() {
        String collectionId = "7667d448-6893-4716-83f0-51b35cb69427";
        server.expect(ExpectedCount.once(),
                        MockRestRequestMatchers.requestTo("https://transfer.api.globus.org/v0.10/endpoint/" + collectionId))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer admin-token"))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "id": "%s",
                          "gcs_manager_url": "https://gcs.example"
                        }
                        """.formatted(collectionId), MediaType.APPLICATION_JSON));

        server.expect(ExpectedCount.once(),
                        MockRestRequestMatchers.requestTo("https://gcs.example/api/collections/" + collectionId))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.DELETE))
                .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer admin-token"))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "DATA_TYPE": "result#1.0.0",
                          "code": "Deleted",
                          "message": "Collection deleted successfully",
                          "resource": "%s"
                        }
                        """.formatted(collectionId), MediaType.APPLICATION_JSON));

        OperationResult result = client.deleteCollection("admin-token", collectionId);

        assertThat(result.code()).isEqualTo("Deleted");
        assertThat(result.resource()).isEqualTo(collectionId);
    }
}
