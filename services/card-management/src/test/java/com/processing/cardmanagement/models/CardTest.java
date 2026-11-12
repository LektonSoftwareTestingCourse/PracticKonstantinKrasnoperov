package com.processing.cardmanagement.models;

import com.processing.cardmanagement.exceptions.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class CardTest {
    @ParameterizedTest
    @CsvSource({"0.01", "99.99", "100"})
    void shouldStartReservationWhenBalanceCoversAmount(BigDecimal amount) {
        // Arrange
        var card = activeCard();
        // Act
        var reservation = card.startReservation(amount, "123456789012");
        // Assert
        assertThat(reservation.pan()).isEqualTo(card.pan());
        assertThat(reservation.reservationAmount()).isEqualByComparingTo(amount);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.RESERVED);
    }
    @Test
    void shouldRejectReservationWhenFundsAreInsufficient() {
        // Arrange
        var card = activeCard();
        // Act
        var error = catchThrowable(() -> card.startReservation(new BigDecimal("100.01"), "123456789012"));
        // Assert
        assertThat(error).isInstanceOf(InsufficientFundsException.class);
    }
    @ParameterizedTest
    @EnumSource(value = CardStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    void shouldRejectReservationWhenCardIsNotActive(CardStatus status) {
        // Arrange
        var card = card(status);
        // Act
        var error = catchThrowable(() -> card.startReservation(BigDecimal.ONE, "123456789012"));
        // Assert
        assertThat(error).isInstanceOf(IllegalStateException.class);
    }
    @Test
    void shouldReduceBalanceWhenReservationIsApplied() {
        // Arrange
        var card = activeCard();
        var reservation = new Reservation(card.pan(), new BigDecimal("25"), "123456789012");
        // Act
        var updated = card.withReservation(reservation);
        // Assert
        assertThat(updated.availableBalance()).isEqualByComparingTo("75");
        assertThat(card.availableBalance()).isEqualByComparingTo("100");
    }
    @Test
    void shouldRestoreBalanceWhenRollbackIsApplied() {
        // Arrange
        var card = activeCard();
        var rollback = new ReservationRollback(new UUID(0, 2), new UUID(0, 3), card.pan(),
                new BigDecimal("25"), "123456789012", Instant.EPOCH);
        // Act
        var updated = card.withRollback(rollback);
        // Assert
        assertThat(updated.availableBalance()).isEqualByComparingTo("125");
    }
    @ParameterizedTest
    @CsvSource({"-1,100", "1,-1", "101,100"})
    void shouldRejectUpdateWhenLimitsAreInconsistent(BigDecimal daily, BigDecimal monthly) {
        // Arrange
        var card = activeCard();
        // Act
        var error = catchThrowable(() -> card.withData(CardStatus.ACTIVE, daily, monthly, BigDecimal.TEN));
        // Assert
        assertThat(error).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void shouldMarkDeletedWhenCardIsDeleted() {
        // Arrange
        var card = activeCard();
        // Act
        var deleted = card.deleted();
        // Assert
        assertThat(deleted.status()).isEqualTo(CardStatus.DELETED);
        assertThat(deleted.pan()).isEqualTo(card.pan());
    }
    private Card activeCard() {
        return card(CardStatus.ACTIVE);
    }
    private Card card(CardStatus status) {
        return new Card(new UUID(0, 1), "4000000000000002", "400000", "IVAN IVANOV", YearMonth.of(2027, 1),
                status, "643", new BigDecimal("1000"), new BigDecimal("10000"), new BigDecimal("100"),
                "ISS001", Instant.EPOCH);
    }
}
