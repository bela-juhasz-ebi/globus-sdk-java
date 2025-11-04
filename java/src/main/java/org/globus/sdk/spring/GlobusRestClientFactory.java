package org.globus.sdk.spring;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Convenience factory methods for creating {@link RestClient} instances targeting Globus services.
 */
public final class GlobusRestClientFactory {

    public static final String GLOBUS_AUTH_BASE_URL = "https://auth.globus.org";
    public static final String GLOBUS_TRANSFER_BASE_URL = "https://transfer.api.globus.org";

    private GlobusRestClientFactory() {
    }

    /**
     * Create a {@link RestClient} pre-configured for Globus Auth requests.
     */
    public static RestClient createAuthClient(RestClient.Builder builder) {
        return builder
                .baseUrl(GLOBUS_AUTH_BASE_URL)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Create a {@link RestClient} pre-configured for Globus Transfer requests.
     */
    public static RestClient createTransferClient(RestClient.Builder builder) {
        return builder
                .baseUrl(GLOBUS_TRANSFER_BASE_URL)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
