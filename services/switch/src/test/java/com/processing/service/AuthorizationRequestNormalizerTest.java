package com.processing.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class AuthorizationRequestNormalizerTest {
    @ParameterizedTest
    @CsvSource(value = {"NULL|NULL", "''|''", "' '| ' '", "TERM|TERM0000", "TERM0001|TERM0001",
            "TERM000123|TERM0001"}, delimiter = '|', nullValues = "NULL")
    void shouldNormalizeTerminalWhenIdentifierHasDifferentLengths(String input, String expected) {
        // Arrange
        // Act
        var actual = AuthorizationRequestNormalizer.normalizeTerminalId(input);
        // Assert
        assertThat(actual).isEqualTo(expected);
    }
    @ParameterizedTest
    @CsvSource(value = {"NULL|NULL", "''|''", "' '|' '", "SHOP|SHOP00000000000",
            "MERCH0000000001|MERCH0000000001", "MERCH00000000001|MERCH0000000001",
            "MERCH000000000001|MERCH0000000000", "OTHER000000000001|OTHER0000000000",
            "MERCH100000000001|MERCH1000000000"}, delimiter = '|', nullValues = "NULL")
    void shouldNormalizeMerchantWhenIdentifierHasDifferentFormats(String input, String expected) {
        // Arrange
        // Act
        var actual = AuthorizationRequestNormalizer.normalizeMerchantId(input);
        // Assert
        assertThat(actual).isEqualTo(expected);
    }
}
