package com.processing.cardmanagement.services;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class LuhnValidatorTest {
    @ParameterizedTest
    @CsvSource({"4000000000000002,true", "4111111111111111,true", "5555555555554444,true",
            "4000000000000003,false", "4111111111111112,false"})
    void shouldValidateChecksumWhenPanIsProvided(String pan, boolean expected) {
        // Arrange
        var validator = new LuhnValidator();
        // Act
        var valid = validator.isValid(pan);
        // Assert
        assertThat(valid).isEqualTo(expected);
    }
}
