package com.processing.service;

import com.processing.common.dto.authorization.*;
import com.processing.common.dto.transactionlogger.*;
import com.processing.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RouteServiceTest {
    private RoutingService routing;
    private AuthorizationClient authorization;
    private AcquiringFeeClient fees;
    private LoggerClient logger;
    private RouteService service;
    @BeforeEach
    void setUp() {
        routing = mock(RoutingService.class);
        authorization = mock(AuthorizationClient.class);
        fees = mock(AcquiringFeeClient.class);
        logger = mock(LoggerClient.class);
        service = new RouteService(routing, authorization, fees, logger);
    }
    @Test
    void shouldPublishTransactionWhenAuthorizationSucceeds() {
        // Arrange
        var request = SwitchFixtures.request();
        var approved = approved();
        when(routing.getIssuerIdByPan(request.pan())).thenReturn("ISS001");
        when(authorization.authorize(any())).thenReturn(approved);
        when(fees.fetchAcquiringFee(any(), any(), any(), any(), any())).thenReturn(new BigDecimal("2"));
        when(logger.log(any())).thenReturn(true);
        // Act
        var response = service.route(request);
        // Assert
        assertThat(response).isEqualTo(approved);
        var transaction = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(logger).log(transaction.capture());
        assertThat(transaction.getValue().issuerId()).isEqualTo("ISS001");
        assertThat(transaction.getValue().amount()).isEqualByComparingTo("100");
        assertThat(transaction.getValue().status()).isEqualTo(TransactionStatus.APPROVED);
        verify(authorization, never()).rollback(any(), any());
    }
    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "DECLINED"})
    void shouldReturnSystemErrorWhenPublishingApprovalFails(String rollbackStatus) {
        // Arrange
        var request = SwitchFixtures.request();
        when(routing.getIssuerIdByPan(request.pan())).thenReturn("ISS001");
        when(authorization.authorize(any())).thenReturn(approved());
        when(logger.log(any())).thenReturn(false);
        when(authorization.rollback(any(), any())).thenReturn(
                new RollbackResponse("123456789012", "00", rollbackStatus, null, 0));
        // Act
        var response = service.route(request);
        // Assert
        assertThat(response.responseCode()).isEqualTo("96");
        verify(authorization).rollback(request.withIssuerId("ISS001"), "123456789012");
    }
    @Test
    void shouldReturnSystemErrorWhenRollbackHasNoResponse() {
        // Arrange
        when(routing.getIssuerIdByPan(any())).thenReturn("ISS001");
        when(authorization.authorize(any())).thenReturn(approved());
        when(logger.log(any())).thenReturn(false);
        // Act
        var response = service.route(SwitchFixtures.request());
        // Assert
        assertThat(response.responseCode()).isEqualTo("96");
        verify(authorization).rollback(any(), eq("123456789012"));
    }
    @Test
    void shouldDeclineWhenBinIsUnknown() {
        // Arrange
        when(routing.getIssuerIdByPan(any())).thenThrow(new UnknownBinException("400000"));
        // Act
        var response = service.route(SwitchFixtures.request());
        // Assert
        assertThat(response.status()).isEqualTo("DECLINED");
        assertThat(response.responseCode()).isEqualTo("14");
        verify(authorization, never()).rollback(any(), any());
        verify(logger).log(any());
    }
    @Test
    void shouldDeclineWhenAuthorizationIsUnavailable() {
        // Arrange
        when(authorization.authorize(any())).thenThrow(new AuthorizationException("000001", 1, "offline"));
        // Act
        var response = service.route(SwitchFixtures.request());
        // Assert
        assertThat(response.responseCode()).isEqualTo("05");
        verify(authorization, never()).rollback(any(), any());
    }
    private AuthorizationResponse approved() {
        return new AuthorizationResponse("0110", "000001", "123456789012", "ABC123", "00", "APPROVED", null, null);
    }
}
