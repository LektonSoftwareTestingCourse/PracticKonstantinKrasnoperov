package com.processing.gateway;

import com.processing.gateway.circuitbreaker.InMemoryCircuitBreaker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InMemoryCircuitBreakerTest {
    @ParameterizedTest
    @CsvSource({"9,false", "10,true", "11,true"})
    void shouldAllowProbeWhenOpenDurationHasElapsed(long seconds, boolean expected) {
        // Arrange
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.EPOCH);
        var breaker = InMemoryCircuitBreaker.forTesting(Duration.ofSeconds(10), 1, clock);
        breaker.recordFailure("authorization");
        when(clock.instant()).thenReturn(Instant.EPOCH.plusSeconds(seconds));
        // Act
        var allowed = breaker.allowRequest("authorization");
        // Assert
        assertThat(allowed).isEqualTo(expected);
    }
    @Test
    void shouldRejectSecondProbeWhenFirstProbeIsInProgress() {
        // Arrange
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.EPOCH);
        var breaker = InMemoryCircuitBreaker.forTesting(Duration.ofSeconds(10), 1, clock);
        breaker.recordFailure("authorization");
        when(clock.instant()).thenReturn(Instant.EPOCH.plusSeconds(10));
        breaker.allowRequest("authorization");
        // Act
        var allowed = breaker.allowRequest("authorization");
        // Assert
        assertThat(allowed).isFalse();
    }
    @Test
    void shouldCloseCircuitWhenProbeSucceeds() {
        // Arrange
        var breaker = InMemoryCircuitBreaker.forTesting(Duration.ZERO, 1, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        breaker.recordFailure("authorization");
        breaker.allowRequest("authorization");
        // Act
        breaker.recordSuccess("authorization");
        // Assert
        assertThat(breaker.allowRequest("authorization")).isTrue();
    }
}
