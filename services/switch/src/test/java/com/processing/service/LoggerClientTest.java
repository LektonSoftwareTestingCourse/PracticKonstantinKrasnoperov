package com.processing.service;

import com.processing.config.RabbitMQConfig;
import com.processing.common.dto.transactionlogger.TransactionRequest;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LoggerClientTest {
    @Test
    void shouldPublishWhenBrokerIsAvailable() {
        // Arrange
        var rabbit = mock(RabbitTemplate.class);
        var logger = new LoggerClient(rabbit);
        var transaction = mock(TransactionRequest.class);
        // Act
        var logged = logger.log(transaction);
        // Assert
        assertThat(logged).isTrue();
        verify(rabbit).convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, transaction);
    }

    @Test
    void shouldReturnFalseWhenBrokerFails() {
        // Arrange
        var rabbit = mock(RabbitTemplate.class);
        var logger = new LoggerClient(rabbit);
        var transaction = mock(TransactionRequest.class);
        doThrow(new AmqpException("offline")).when(rabbit)
                .convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, transaction);
        // Act
        var logged = logger.log(transaction);
        // Assert
        assertThat(logged).isFalse();
    }
}
