package com.processing.cardmanagement.services;

import com.processing.cardmanagement.events.*;
import com.processing.cardmanagement.exceptions.*;
import com.processing.cardmanagement.models.*;
import com.processing.cardmanagement.options.*;
import com.processing.cardmanagement.repositories.*;
import com.processing.cardmanagement.services.retries.RetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CardServiceImplTest {
    private CardRepository cards;
    private ReservationRepository reservations;
    private ReservationRollbackRepository rollbacks;
    private CardEventNotifier events;
    private CardServiceImpl service;

    @BeforeEach
    void setUp() {
        cards = mock(CardRepository.class);
        reservations = mock(ReservationRepository.class);
        rollbacks = mock(ReservationRollbackRepository.class);
        events = mock(CardEventNotifier.class);
        service = new CardServiceImpl(cards, reservations, rollbacks, mock(CardServiceSettings.class),
                mock(CardServiceDefaults.class), mock(PanGenerator.class), events, mock(BinIssuerService.class),
                mock(RetryService.class), new DirectTransactionRunner());
    }

    @Test
    void shouldPersistReservationAndDebitCardWhenFundsAreAvailable() {
        // Arrange
        var card = activeCard();
        when(cards.findByPanForUpdate(card.pan())).thenReturn(Optional.of(card));
        when(reservations.isUnique("123456789012", card.pan())).thenReturn(true);
        when(reservations.save(any())).thenAnswer(call -> call.getArgument(0));
        when(cards.update(any())).thenAnswer(call -> call.getArgument(0));
        // Act
        var result = service.reserve(card.pan(), new BigDecimal("25"), "123456789012");
        // Assert
        assertThat(result.availableBalance()).isEqualByComparingTo("75");
        var saved = ArgumentCaptor.forClass(Reservation.class);
        verify(reservations).save(saved.capture());
        assertThat(saved.getValue().reservationAmount()).isEqualByComparingTo("25");
        assertThat(saved.getValue().rrn()).isEqualTo("123456789012");
        verify(cards).update(result);
        verify(events).notifyListeners(any(CardServiceReserveEvent.class));
    }

    @Test
    void shouldRejectWhenReservationAlreadyExists() {
        // Arrange
        var card = activeCard();
        when(cards.findByPanForUpdate(card.pan())).thenReturn(Optional.of(card));
        when(reservations.isUnique("123456789012", card.pan())).thenReturn(false);
        // Act
        var error = catchThrowable(() -> service.reserve(card.pan(), BigDecimal.TEN, "123456789012"));
        // Assert
        assertThat(error).isInstanceOf(ReservationAlreadyExistsException.class);
        verify(reservations, never()).save(any());
        verify(cards, never()).update(any());
    }

    @Test
    void shouldRejectWhenCardIsMissing() {
        // Arrange
        when(cards.findByPanForUpdate(any())).thenReturn(Optional.empty());
        // Act
        var error = catchThrowable(() -> service.reserve("4000000000000002", BigDecimal.TEN, "123456789012"));
        // Assert
        assertThat(error).isInstanceOf(CardNotFoundException.class);
        verify(reservations, never()).save(any());
        verify(cards, never()).update(any());
    }

    @Test
    void shouldRestoreBalanceWhenReservationIsRolledBack() {
        // Arrange
        var card = activeCard();
        var reservation = new Reservation(new UUID(0, 2), card.pan(), BigDecimal.TEN, "123456789012",
                ReservationStatus.RESERVED, Instant.EPOCH, Instant.EPOCH);
        when(cards.findByPanForUpdate(card.pan())).thenReturn(Optional.of(card));
        when(reservations.findByRrnAndPanForUpdate("123456789012", card.pan())).thenReturn(Optional.of(reservation));
        when(rollbacks.save(any())).thenAnswer(call -> call.getArgument(0));
        when(cards.update(any())).thenAnswer(call -> call.getArgument(0));
        // Act
        var result = service.rollback(card.pan(), BigDecimal.TEN, "123456789012");
        // Assert
        assertThat(result.availableBalance()).isEqualByComparingTo("110");
        verify(rollbacks).save(any());
        var saved = ArgumentCaptor.forClass(Reservation.class);
        verify(reservations).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ReservationStatus.ROLLED_BACK);
        verify(cards).update(result);
        verify(events).notifyListeners(any(CardServiceRollbackEvent.class));
    }

    @Test
    void shouldRejectRollbackWhenReservationIsMissing() {
        // Arrange
        var card = activeCard();
        when(cards.findByPanForUpdate(card.pan())).thenReturn(Optional.of(card));
        when(reservations.findByRrnAndPanForUpdate(any(), any())).thenReturn(Optional.empty());
        // Act
        var error = catchThrowable(() -> service.rollback(card.pan(), BigDecimal.TEN, "123456789012"));
        // Assert
        assertThat(error).isInstanceOf(ReservationNotFoundException.class);
        verify(rollbacks, never()).save(any());
        verify(cards, never()).update(any());
    }

    private Card activeCard() {
        return new Card(new UUID(0, 1), "4000000000000002", "400000", "IVAN IVANOV", YearMonth.of(2027, 1),
                CardStatus.ACTIVE, "643", new BigDecimal("1000"), new BigDecimal("10000"),
                new BigDecimal("100"), "ISS001", Instant.EPOCH);
    }

    private static final class DirectTransactionRunner implements TransactionRunner {
        @Override
        public void run(Runnable operation) {
            operation.run();
        }
        @Override
        public <T> T runSupplier(Supplier<T> operation) {
            return operation.get();
        }
        @Override
        public <T> T runSupplierWithNestedQuery(Function<TransactionRunner, T> operation) {
            return operation.apply(this);
        }
    }
}
