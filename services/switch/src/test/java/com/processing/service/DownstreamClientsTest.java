package com.processing.service;

import com.processing.exception.AuthorizationException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DownstreamClientsTest {
    private MockRestServiceServer server;
    private RestClient rest;
    private CircuitBreaker breaker;
    private AuthorizationClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        rest = builder.build();
        breaker = CircuitBreaker.ofDefaults("unit");
        client = new AuthorizationClient(SwitchFixtures.properties(), rest,
                Retry.of("unit", RetryConfig.custom().maxAttempts(1).build()), breaker);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "DECLINED"})
    void shouldReturnResponseWhenAuthorizationStatusIsRecognized(String status) {
        // Arrange
        server.expect(requestTo("http://authorization/api/internal/authorize"))
                .andRespond(withSuccess("{\"status\":\"" + status + "\"}", MediaType.APPLICATION_JSON));
        // Act
        var response = client.authorize(SwitchFixtures.request());
        // Assert
        assertThat(response.status()).isEqualTo(status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"status\":\"INVALID\"}"})
    void shouldFailWhenAuthorizationResponseIsInvalid(String body) {
        // Arrange
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        // Act
        var error = catchThrowable(() -> client.authorize(SwitchFixtures.request()));
        // Assert
        assertThat(error).isInstanceOf(AuthorizationException.class);
    }

    @Test
    void shouldFailWhenCircuitIsOpen() {
        // Arrange
        breaker.transitionToOpenState();
        // Act
        var error = catchThrowable(() -> client.authorize(SwitchFixtures.request()));
        // Assert
        assertThat(error).isInstanceOf(AuthorizationException.class).hasMessageContaining("Circuit breaker open");
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "DECLINED"})
    void shouldReturnRollbackWhenStatusIsRecognized(String status) {
        // Arrange
        server.expect(requestTo("http://authorization/api/internal/rollback"))
                .andRespond(withSuccess("{\"status\":\"" + status + "\"}", MediaType.APPLICATION_JSON));
        // Act
        var response = client.rollback(SwitchFixtures.request(), "123456789012");
        // Assert
        assertThat(response.status()).isEqualTo(status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"status\":\"INVALID\"}"})
    void shouldReturnNullWhenRollbackResponseIsInvalid(String body) {
        // Arrange
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        // Act
        var response = client.rollback(SwitchFixtures.request(), "123456789012");
        // Assert
        assertThat(response).isNull();
    }

    @Test
    void shouldReturnOkWhenHealthEndpointResponds() {
        // Arrange
        server.expect(requestTo("http://authorization/health")).andRespond(withSuccess());
        // Act
        var status = client.checkHealth();
        // Assert
        assertThat(status).isEqualTo("ok");
    }

    @Test
    void shouldReturnDownWhenHealthEndpointFails() {
        // Arrange
        server.expect(anything()).andRespond(withException(new IOException("offline")));
        // Act
        var status = client.checkHealth();
        // Assert
        assertThat(status).isEqualTo("down");
    }

    @ParameterizedTest
    @CsvSource(value = {"{\"acquirerFee\":2.50}|2.50", "{}|NULL", "''|NULL"},
            delimiter = '|', nullValues = "<null>")
    void shouldReturnFeeWhenAcquirerResponds(String body, String expected) {
        // Arrange
        var fees = new MerchantAcquirerClient(SwitchFixtures.properties(), rest);
        var request = SwitchFixtures.request();
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        // Act
        var fee = fees.fetchAcquiringFee(request.transmissionDateTime(), request.stan(), request.pan(),
                request.terminalId(), request.amount());
        // Assert
        assertThat(java.util.Objects.toString(fee, "NULL")).isEqualTo(expected);
    }

    @Test
    void shouldReturnNullWhenAcquirerFails() {
        // Arrange
        var fees = new MerchantAcquirerClient(SwitchFixtures.properties(), rest);
        var request = SwitchFixtures.request();
        server.expect(anything()).andRespond(withServerError());
        // Act
        var fee = fees.fetchAcquiringFee(request.transmissionDateTime(), request.stan(), request.pan(),
                request.terminalId(), request.amount());
        // Assert
        assertThat(fee).isNull();
    }

}
