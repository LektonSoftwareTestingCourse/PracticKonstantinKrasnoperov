package com.processing.gateway;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.gateway.validation.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class TransactionRequestValidatorTest {
    @Test
    void shouldAcceptWhenRequestIsValid() {
        // Arrange
        var validator = new TransactionRequestValidator();
        var request = request("4000000000000002", "0.01");
        // Act
        var error = catchThrowable(() -> validator.validate(request));
        // Assert
        assertThat(error).isNull();
    }
    @ParameterizedTest
    @CsvSource({"4000000000000002,0,amount", "4000000000000002,-1,amount",
            "400000000000000,1,pan", "40000000000000000,1,pan", "abcdefghijklmnop,1,pan"})
    void shouldRejectWhenPanOrAmountIsInvalid(String pan, String amount, String field) {
        // Arrange
        var validator = new TransactionRequestValidator();
        var request = request(pan, amount);
        // Act
        var error = catchThrowable(() -> validator.validate(request));
        // Assert
        assertThat(error).isInstanceOf(TransactionValidationException.class).hasMessageContaining(field);
    }
    @Test
    void shouldRejectWhenBodyIsMissing() {
        // Arrange
        var validator = new TransactionRequestValidator();
        // Act
        var error = catchThrowable(() -> validator.validate(null));
        // Assert
        assertThat(error).isInstanceOf(TransactionValidationException.class).hasMessage("Request body is required");
    }
    private AuthorizationRequest request(String pan, String amount) {
        return new AuthorizationRequest("0100", "000001", pan, "000000", new BigDecimal(amount), "643",
                Instant.parse("2026-06-30T23:59:59Z"), "TERM0001", "POS", "MERCH0000000001", "5411", "ACQ001", null);
    }
}
