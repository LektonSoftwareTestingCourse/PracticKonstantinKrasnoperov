package com.processing.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class RetryFactoryTest {
    @ParameterizedTest
    @MethodSource("backoffs")
    void shouldChooseBackoffWhenAttemptIsOutsideConfiguredRange(List<Long> backoff, int attempt, long expected) {
        // Arrange
        var properties = new SwitchProperties("1", Map.of(), "auth", "logger", "acquirer", null,
                new SwitchProperties.RetryProperties(3, backoff), null);
        var retry = RetryFactory.authorizationRetry(properties);
        // Act
        var interval = retry.getRetryConfig().getIntervalBiFunction().apply(attempt,
                io.github.resilience4j.core.functions.Either.left(new IllegalStateException("offline")));
        // Assert
        assertThat(interval).isEqualTo(expected);
    }

    static Stream<Arguments> backoffs() {
        return Stream.of(
                Arguments.of(null, 1, 1L),
                Arguments.of(List.of(), 1, 1L),
                Arguments.of(List.of(0L, 20L), 1, 1L),
                Arguments.of(List.of(10L, 20L), 2, 20L),
                Arguments.of(List.of(10L, 20L), 5, 20L));
    }
}
