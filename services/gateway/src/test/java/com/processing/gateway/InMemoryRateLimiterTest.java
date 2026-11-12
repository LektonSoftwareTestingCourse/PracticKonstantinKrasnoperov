package com.processing.gateway;

import com.processing.gateway.ratelimit.InMemoryRateLimiter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

class InMemoryRateLimiterTest {
    @ParameterizedTest
    @CsvSource({"0,false", "499999999,false", "500000000,true", "1000000000,true"})
    void shouldRefillTokenWhenEnoughTimeHasElapsed(long elapsedNanos, boolean expected) {
        // Arrange
        var time = new AtomicLong();
        var limiter = InMemoryRateLimiter.forTesting(1, 2, Duration.ofMinutes(1), 100, time::get);
        limiter.allowRequest("client");
        time.set(elapsedNanos);
        // Act
        var allowed = limiter.allowRequest("client");
        // Assert
        assertThat(allowed).isEqualTo(expected);
    }
    @ParameterizedTest
    @CsvSource({"first,false", "second,true"})
    void shouldIsolateBucketWhenClientKeyChanges(String key, boolean expected) {
        // Arrange
        var limiter = InMemoryRateLimiter.forTesting(1, 0, Duration.ofMinutes(1), 100, () -> 0);
        limiter.allowRequest("first");
        // Act
        var allowed = limiter.allowRequest(key);
        // Assert
        assertThat(allowed).isEqualTo(expected);
    }
}
