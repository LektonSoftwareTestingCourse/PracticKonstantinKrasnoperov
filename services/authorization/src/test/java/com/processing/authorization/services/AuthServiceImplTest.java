package com.processing.authorization.services;

import com.processing.authorization.client.*;
import com.processing.authorization.events.AuthorizationEventNotifier;
import com.processing.authorization.exceptions.*;
import com.processing.authorization.repositories.LimitUsageRepository;
import com.processing.common.dto.authorization.*;
import com.processing.common.dto.cardmanagement.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AuthServiceImplTest {
    private LimitUsageRepository limits;
    private CardManagementClient cards;
    private BinLookupClient bins;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        limits = mock(LimitUsageRepository.class);
        cards = mock(CardManagementClient.class);
        bins = mock(BinLookupClient.class);
        service = new AuthServiceImpl(limits, mock(AuthorizationEventNotifier.class), bins, cards);
    }

    @ParameterizedTest
    @CsvSource({"BLOCKED,05,CARD_BLOCKED", "INACTIVE,05,CARD_INACTIVE", "EXPIRED,54,CARD_EXPIRED"})
    void shouldDeclineWhenCardIsNotActive(CardModelStatus status, String code, String reason) {
        // Arrange
        var request = request("4000000000000002", "100");
        when(cards.getCard(request.pan())).thenReturn(card(status, "2026-06", "100"));
        when(bins.getIssuerId(request.pan())).thenReturn(Optional.empty());
        // Act
        var response = service.authorize(request, Instant.now());
        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.responseCode()).isEqualTo(code);
        assertThat(response.declineReason()).isEqualTo(reason);
        verify(cards, never()).reserve(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"2026-05,100,CARD_EXPIRED", "2026-06,99.99,INSUFFICIENT_FUNDS"})
    void shouldDeclineWhenExpiryOrBalancePreventsPayment(String expiry, String balance, String reason) {
        // Arrange
        var request = request("4000000000000002", "100");
        when(cards.getCard(request.pan())).thenReturn(card(CardModelStatus.ACTIVE, expiry, balance));
        when(bins.getIssuerId(request.pan())).thenReturn(Optional.empty());
        // Act
        var response = service.authorize(request, Instant.now());
        // Assert
        assertThat(response.declineReason()).isEqualTo(reason);
        verify(cards, never()).reserve(any(), any(), any());
    }

    @Test
    void shouldReserveWhenBalanceEqualsAmountAndExpiryMonthHasNotEnded() {
        // Arrange
        var request = request("4000000000000002", "100");
        when(cards.getCard(request.pan())).thenReturn(activeCard());
        when(bins.getIssuerId(request.pan())).thenReturn(Optional.empty());
        when(limits.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);
        when(limits.fetchRrnBlock()).thenReturn(1L);
        // Act
        var response = service.authorize(request, Instant.now());
        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.responseCode()).isEqualTo("00");
        assertThat(response.rrn()).matches("[0-9]{12}");
        assertThat(response.authCode()).matches("[A-Z0-9]{6}");
        verify(cards).reserve(request.amount(), response.rrn(), request.pan());
    }

    @Test
    void shouldDeclineWhenLimitsAreExceeded() {
        // Arrange
        var request = request("4000000000000002", "100");
        when(cards.getCard(request.pan())).thenReturn(activeCard());
        when(bins.getIssuerId(request.pan())).thenReturn(Optional.of("ISS001"));
        when(limits.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(0);
        // Act
        var response = service.authorize(request, Instant.now());
        // Assert
        assertThat(response.declineReason()).isEqualTo("EXCEEDS_AMOUNT_LIMIT");
        verify(cards, never()).reserve(any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("cardFailures")
    void shouldTranslateDeclineWhenCardLookupFails(RuntimeException failure, String reason) {
        // Arrange
        var request = request("4000000000000002", "100");
        when(cards.getCard(request.pan())).thenThrow(failure);
        // Act
        var response = service.authorize(request, Instant.now());
        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo(reason);
        verify(cards, never()).reserve(any(), any(), any());
    }

    static Stream<Arguments> cardFailures() {
        return Stream.of(
                Arguments.of(new CardNotFoundException("missing"), "CARD_NOT_FOUND"),
                Arguments.of(new ServiceUnavailableException("offline"), "SERVICE_UNAVAILABLE"),
                Arguments.of(new PaymentRequiredException("blocked"), "CARD_BLOCKED"),
                Arguments.of(new IllegalStateException("unexpected"), "UNKNOWN_REASON"));
    }

    @Test
    void shouldRollbackWhenReversalIsRequested() {
        // Arrange
        var request = new RollbackRequest("123456789012", "4000000000000002", new BigDecimal("100"));
        // Act
        var response = service.rollback(request, Instant.now());
        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.rrn()).isEqualTo(request.rrn());
        verify(cards).rollback(request);
    }

    @ParameterizedTest
    @MethodSource("reservationFailures")
    void shouldDeclineWhenReservationFails(RuntimeException failure, String reason) {
        // Arrange
        var request = request("4000000000000002", "100");
        when(cards.getCard(request.pan())).thenReturn(activeCard());
        when(bins.getIssuerId(request.pan())).thenReturn(Optional.empty());
        when(limits.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);
        doThrow(failure).when(cards).reserve(any(), any(), any());
        // Act
        var response = service.authorize(request, Instant.now());
        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo(reason);
    }

    static Stream<Arguments> reservationFailures() {
        return Stream.of(
                Arguments.of(new CardNotFoundException("missing"), "CARD_NOT_FOUND"),
                Arguments.of(new ServiceUnavailableException("offline"), "SERVICE_UNAVAILABLE"),
                Arguments.of(new InvalidReserveRequestException("invalid"), "CARD_NOT_FOUND"),
                Arguments.of(new InsufficientFundsException("balance changed"), "INSUFFICIENT_FUNDS"),
                Arguments.of(new ReserveException("failed"), "RESERVATION_FAILED"),
                Arguments.of(new IllegalStateException("unexpected"), "UNKNOWN_REASON"));
    }

    @ParameterizedTest
    @MethodSource("rollbackFailures")
    void shouldDeclineWhenRollbackFails(RuntimeException failure, String reason) {
        // Arrange
        var request = new RollbackRequest("123456789012", "4000000000000002", BigDecimal.TEN);
        doThrow(failure).when(cards).rollback(request);
        // Act
        var response = service.rollback(request, Instant.now());
        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.declineReason()).isEqualTo(reason);
        assertThat(response.rrn()).isEqualTo(request.rrn());
    }

    static Stream<Arguments> rollbackFailures() {
        return Stream.of(
                Arguments.of(new CardNotFoundException("missing"), "TRANSACTION_NOT_FOUND"),
                Arguments.of(new ServiceUnavailableException("offline"), "SERVICE_UNAVAILABLE"),
                Arguments.of(new RollbackConflictException("duplicate"), "ALREADY_ROLLED_BACK"),
                Arguments.of(new RollbackFailureException("failed"), "ROLLBACK_FAILED"),
                Arguments.of(new IllegalStateException("unexpected"), "UNKNOWN_REASON"));
    }

    @Test
    void shouldRetryLimitUpdateWhenConcurrentInsertConflicts() {
        // Arrange
        var request = request("4000000000000002", "100");
        when(cards.getCard(request.pan())).thenReturn(activeCard());
        when(bins.getIssuerId(request.pan())).thenReturn(Optional.empty());
        when(limits.upsertLimitUsage(any(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("concurrent insert"))
                .thenReturn(1);
        // Act
        var response = service.authorize(request, Instant.now());
        // Assert
        assertThat(response.status()).isEqualTo("APPROVED");
        verify(limits, times(2)).upsertLimitUsage(request.pan(), LocalDate.of(2026, 6, 30), request.amount(),
                activeCard().dailyLimit(), activeCard().monthlyLimit());
    }

    @Test
    void shouldDeclineWhenLimitDatabaseIsUnavailable() {
        // Arrange
        var request = request("4000000000000002", "100");
        when(cards.getCard(request.pan())).thenReturn(activeCard());
        when(bins.getIssuerId(request.pan())).thenReturn(Optional.empty());
        when(limits.upsertLimitUsage(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("database offline"));
        // Act
        var response = service.authorize(request, Instant.now());
        // Assert
        assertThat(response.declineReason()).isEqualTo("DB_UNAVAILABLE");
        verify(cards, never()).reserve(any(), any(), any());
    }

    private CardModel activeCard() {
        return card(CardModelStatus.ACTIVE, "2026-06", "100");
    }

    private CardModel card(CardModelStatus status, String expiry, String balance) {
        return new CardModel(new UUID(0, 1), "4000000000000002", "400000", "IVAN IVANOV",
                YearMonth.parse(expiry), status, "643", new BigDecimal("1000"), new BigDecimal("10000"),
                new BigDecimal(balance), "ISS001", Instant.EPOCH);
    }
    private AuthorizationRequest request(String pan, String amount) {
        return new AuthorizationRequest("0100", "000001", pan, "000000", new BigDecimal(amount), "643",
                Instant.parse("2026-06-30T23:59:59Z"), "TERM0001", "POS", "MERCH0000000001", "5411", "ACQ001", null);
    }
}
