package org.globus.sdk.spring;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Minimal Globus Auth helper backed by Spring's {@link RestClient}.
 */
public class GlobusAuthClient {

    private static final String DEVICE_CODE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code";
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(2);

    private final RestClient authClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public GlobusAuthClient(RestClient.Builder builder) {
        this(GlobusRestClientFactory.createAuthClient(builder));
    }

    public GlobusAuthClient(RestClient authClient) {
        this.authClient = Objects.requireNonNull(authClient, "authClient");
    }

    /**
     * Kick off the device authorization flow (equivalent to {@code globus login}).
     *
     * @param clientId Globus Auth client identifier.
     * @param scope    space-separated set of scopes to request.
     * @return device authorization metadata describing how the user should complete the login.
     */
    public DeviceAuthorizationResponse startDeviceAuthorization(String clientId, String scope) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        if (scope != null && !scope.isBlank()) {
            body.add("scope", scope);
        }

        return authClient.post()
                .uri("/v2/oauth2/device/code")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(DeviceAuthorizationResponse.class);
    }

    /**
     * Poll Globus Auth until the user has approved the requested scopes and the issued tokens are ready.
     *
     * @param clientId    Globus Auth client identifier.
     * @param deviceCode  code returned by {@link #startDeviceAuthorization}.
     * @param pollEvery   preferred polling cadence; defaults to the Globus recommended interval.
     * @param timeout     maximum amount of time to poll before aborting.
     * @return the tokens granted by Globus Auth.
     */
    public OAuthTokenResponse pollForToken(String clientId,
                                           String deviceCode,
                                           Duration pollEvery,
                                           Duration timeout) {
        Duration interval = pollEvery != null && !pollEvery.isNegative() && !pollEvery.isZero()
                ? pollEvery
                : DEFAULT_POLL_INTERVAL;
        Instant deadline = timeout == null ? null : Instant.now().plus(timeout);

        while (true) {
            if (deadline != null && Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("Timed out waiting for Globus user authorization");
            }

            try {
                return requestToken(clientId, deviceCode);
            } catch (RestClientResponseException error) {
                GlobusAuthError authError = parseError(error);
                if (authError == null) {
                    throw error;
                }

                switch (authError.error()) {
                    case "authorization_pending" -> sleep(interval);
                    case "slow_down" -> {
                        interval = interval.plusSeconds(5);
                        sleep(interval);
                    }
                    case "expired_token" -> throw new IllegalStateException("The device code has expired. Restart the login flow.");
                    case "access_denied" -> throw new IllegalStateException("The user denied the requested Globus scopes.");
                    default -> throw error;
                }
            }
        }
    }

    private OAuthTokenResponse requestToken(String clientId, String deviceCode) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("device_code", deviceCode);
        body.add("grant_type", DEVICE_CODE_GRANT_TYPE);

        return authClient.post()
                .uri("/v2/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(OAuthTokenResponse.class);
    }

    private GlobusAuthError parseError(RestClientResponseException error) {
        try {
            return mapper.readValue(error.getResponseBodyAsByteArray(), GlobusAuthError.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private void sleep(Duration interval) {
        try {
            Thread.sleep(interval.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RestClientException("Polling interrupted", interrupted) {
            };
        }
    }

    private record GlobusAuthError(String error, @JsonProperty("error_description") String description) {
    }
}
