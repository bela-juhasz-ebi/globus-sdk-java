package org.globus.sdk.spring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GlobusAuthClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private GlobusAuthClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder)
                .ignoreExpectOrder(true)
                .build();
        client = new GlobusAuthClient(builder);
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void startDeviceAuthorizationPostsForm() {
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("client_id", "client-id");
        expectedForm.add("scope", "openid profile");

        server.expect(ExpectedCount.once(),
                        MockRestRequestMatchers.requestTo("https://auth.globus.org/v2/oauth2/device/code"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(MockRestRequestMatchers.content().formData(expectedForm))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "device_code": "device-code",
                          "user_code": "user-code",
                          "verification_uri": "https://auth.globus.org/device",
                          "verification_uri_complete": "https://auth.globus.org/device?user_code=user-code",
                          "expires_in": 600,
                          "interval": 5
                        }
                        """, MediaType.APPLICATION_JSON));

        DeviceAuthorizationResponse response = client.startDeviceAuthorization("client-id", "openid profile");

        assertThat(response.deviceCode()).isEqualTo("device-code");
        assertThat(response.verificationUri()).contains("device");
    }

    @Test
    void pollForTokenRetriesUntilAuthorized() {
        server.expect(ExpectedCount.once(),
                        MockRestRequestMatchers.requestTo("https://auth.globus.org/v2/oauth2/token"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"authorization_pending\"}"));

        server.expect(ExpectedCount.once(),
                        MockRestRequestMatchers.requestTo("https://auth.globus.org/v2/oauth2/token"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "access_token": "access-token",
                          "refresh_token": "refresh-token",
                          "token_type": "bearer",
                          "expires_in": 3600,
                          "scope": "openid profile",
                          "resource_server": "auth.globus.org",
                          "other_tokens": [
                            {
                              "access_token": "transfer-token",
                              "resource_server": "transfer.api.globus.org"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        OAuthTokenResponse tokens = client.pollForToken(
                "client-id",
                "device-code",
                Duration.ofMillis(10),
                Duration.ofSeconds(2)
        );

        assertThat(tokens.accessToken()).isEqualTo("access-token");
        assertThat(tokens.otherTokens()).anySatisfy(token ->
                assertThat(token.resourceServer()).isEqualTo("transfer.api.globus.org"));
    }
}
