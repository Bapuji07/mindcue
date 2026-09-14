package com.secondmemory.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test void providerFailureIsActionableAndDoesNotExposeResponseBody() {
        var error = HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", new HttpHeaders(),
                "private-provider-response".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        var response = new GlobalExceptionHandler().handleProviderError(error);
        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody().message()).contains("403", "API key")
                .doesNotContain("private-provider-response");
    }

    @Test void connectionFailureIsReportedAsGatewayTimeout() {
        var response = new GlobalExceptionHandler().handleProviderConnection(new ResourceAccessException("timeout"));
        assertThat(response.getStatusCode().value()).isEqualTo(504);
    }
}
