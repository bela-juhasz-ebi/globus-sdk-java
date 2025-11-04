package org.globus.sdk.spring;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(classes = GlobusSdkIntegrationTest.TestApplication.class)
@ActiveProfiles("test")
class GlobusSdkIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(GlobusSdkIntegrationTest.class);

    @Autowired
    private RestClientBuilderFactory builderFactory;

    @Value("${globus.test.enabled:false}")
    private boolean integrationEnabled;

    @Value("${globus.auth.client-id:}")
    private String clientId;

    @Value("${globus.auth.scope:}")
    private String scope;

    @Value("${globus.auth.poll-timeout:PT5M}")
    private Duration pollTimeout;

    @Value("${globus.transfer.resource-server:transfer.api.globus.org}")
    private String transferResourceServer;

    @Value("${globus.transfer.mapped-collection-id:}")
    private String mappedCollectionId;

    @Value("${globus.transfer.list-path:}")
    private String listPath;

    @Value("${globus.transfer.guest-base-path:}")
    private String guestBasePath;

    @Value("${globus.transfer.guest-display-name-prefix:java-sdk-test}")
    private String guestDisplayNamePrefix;

    @Value("${globus.transfer.guest-public:false}")
    private boolean guestPublic;

    @Value("${globus.transfer.identity-principal:}")
    private String identityPrincipal;

    @Value("${globus.transfer.permission-path:/}")
    private String permissionPath;

    @Value("${globus.transfer.permission:r}")
    private String permission;

    @Test
    void completeWorkflowAgainstGlobus() {
        assumeTrue(integrationEnabled, "Set globus.test.enabled=true to execute the integration workflow");
        assumeTrue(hasText(clientId), "globus.auth.client-id must be configured");
        assumeTrue(hasText(scope), "globus.auth.scope must be configured");
        assumeTrue(hasText(mappedCollectionId), "globus.transfer.mapped-collection-id must be configured");
        assumeTrue(hasText(guestBasePath), "globus.transfer.guest-base-path must be configured");
        assumeTrue(hasText(identityPrincipal), "globus.transfer.identity-principal must be configured");

        GlobusAuthClient authClient = new GlobusAuthClient(builderFactory.newBuilder());
        DeviceAuthorizationResponse device = authClient.startDeviceAuthorization(clientId, scope);
        String verificationUrl = hasText(device.verificationUriComplete())
                ? device.verificationUriComplete()
                : device.verificationUri();
        log.info("Complete Globus login at {} using code {}", verificationUrl, device.userCode());

        OAuthTokenResponse tokenResponse = authClient.pollForToken(
                clientId,
                device.deviceCode(),
                Duration.ofSeconds(Math.max(1, device.interval())),
                pollTimeout
        );

        String transferToken = extractToken(tokenResponse, transferResourceServer);
        assertThat(transferToken)
                .as("Unable to find a token for resource server %s", transferResourceServer)
                .isNotBlank();

        GlobusTransferClient transferClient = new GlobusTransferClient(builderFactory.newBuilder());

        DirectoryListing listing = transferClient.listDirectory(
                transferToken,
                mappedCollectionId,
                hasText(listPath) ? listPath : null
        );
        assertThat(listing.endpoint()).isEqualTo(mappedCollectionId);

        String guestDisplayName = guestDisplayNamePrefix + "-" + Instant.now().truncatedTo(ChronoUnit.SECONDS);
        GuestCollection guestCollection = null;
        AccessRule grantedRule = null;
        String guestCollectionId = null;
        boolean permissionDeleted = false;
        try {
            guestCollection = transferClient.createGuestCollection(
                    transferToken,
                    mappedCollectionId,
                    guestBasePath,
                    guestDisplayName,
                    guestPublic
            );
            assertThat(guestCollection.id()).isNotBlank();
            guestCollectionId = guestCollection.id();

            grantedRule = transferClient.createIdentityPermission(
                    transferToken,
                    guestCollectionId,
                    identityPrincipal,
                    hasText(permissionPath) ? permissionPath : "/",
                    permission
            );
            assertThat(grantedRule.id()).isNotBlank();

            AccessRuleList permissions = transferClient.listPermissions(transferToken, guestCollectionId);
            assertThat(permissions.rules())
                    .anyMatch(rule -> Objects.equals(rule.id(), grantedRule.id()));

            OperationResult deletePermissionResult = transferClient.deletePermission(
                    transferToken,
                    guestCollectionId,
                    grantedRule.id()
            );
            assertThat(deletePermissionResult.code()).isNotBlank();
            permissionDeleted = true;
        } finally {
            if (!permissionDeleted && grantedRule != null && guestCollectionId != null) {
                try {
                    transferClient.deletePermission(transferToken, guestCollectionId, grantedRule.id());
                } catch (Exception ex) {
                    log.warn("Failed to delete access rule {} during cleanup", grantedRule.id(), ex);
                }
            }
            if (guestCollectionId != null) {
                try {
                    OperationResult deleteCollection = transferClient.deleteCollection(transferToken, guestCollectionId);
                    assertThat(deleteCollection.code()).isNotBlank();
                } catch (Exception ex) {
                    log.warn("Failed to delete guest collection {} during cleanup", guestCollectionId, ex);
                }
            }
        }
    }

    private String extractToken(OAuthTokenResponse response, String desiredResourceServer) {
        if (!hasText(desiredResourceServer)) {
            return response.accessToken();
        }
        if (desiredResourceServer.equals(response.resourceServer())) {
            return response.accessToken();
        }
        if (response.otherTokens() == null) {
            return null;
        }
        return response.otherTokens().stream()
                .filter(token -> desiredResourceServer.equals(token.resourceServer()))
                .map(OAuthTokenResponse::accessToken)
                .findFirst()
                .orElse(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestApplication {
        @Bean
        RestClientBuilderFactory restClientBuilderFactory(ObjectProvider<org.springframework.web.client.RestClient.Builder> provider) {
            return provider::getObject;
        }
    }

    interface RestClientBuilderFactory {
        org.springframework.web.client.RestClient.Builder newBuilder();
    }
}
