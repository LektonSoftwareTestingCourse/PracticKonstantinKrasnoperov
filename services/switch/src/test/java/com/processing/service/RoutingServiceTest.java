package com.processing.service;

import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RoutingServiceTest {
    @Test
    void shouldReturnIssuerWhenBinIsKnown() {
        // Arrange
        var service = new RoutingService(SwitchFixtures.properties());
        // Act
        var issuer = service.getIssuerIdByPan("4000000000000002");
        // Assert
        assertThat(issuer).isEqualTo("ISS001");
    }
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345", "9999990000000000"})
    void shouldRejectWhenPanHasNoKnownBin(String pan) {
        // Arrange
        var service = new RoutingService(SwitchFixtures.properties());
        // Act
        var error = catchThrowable(() -> service.getIssuerIdByPan(pan));
        // Assert
        assertThat(error).isInstanceOf(UnknownBinException.class);
    }
}
