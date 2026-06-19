package br.com.renan.transactionauthorization.sqs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.renan.transactionauthorization.config.SqsProperties;
import br.com.renan.transactionauthorization.dto.AccountCreatedMessage;
import br.com.renan.transactionauthorization.service.AccountCreatedConsumerService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

class AccountCreatedSqsConsumerTest {

    private static final String QUEUE_URL = "http://localhost:4566/000000000000/conta-bancaria-criada";
    private static final String VALID_BODY = """
            {"account":{"id":"5b19c8b6-0cc4-4c72-a989-0c2ee15fa975",
            "owner":"315e3cfe-f4af-4cd2-b298-a449e614349a",
            "created_at":"1634874339","status":"ENABLED"}}
            """;

    private SqsClient sqsClient;
    private AccountCreatedConsumerService consumerService;
    private AccountCreatedSqsConsumer consumer;

    @BeforeEach
    void setUp() {
        sqsClient = mock(SqsClient.class);
        consumerService = mock(AccountCreatedConsumerService.class);
        SqsProperties properties = new SqsProperties(
                "sa-east-1", "http://localhost:4566", QUEUE_URL, "test", "test",
                10, 10, true, 1000L);
        consumer = new AccountCreatedSqsConsumer(
                sqsClient, properties, consumerService, new ObjectMapper(), new SimpleMeterRegistry());

        Message message = Message.builder()
                .messageId("msg-1")
                .receiptHandle("receipt-1")
                .body(VALID_BODY)
                .build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
    }

    @Test
    void deletesMessage_whenProcessingSucceeds() {
        doNothing().when(consumerService).importAccount(any(AccountCreatedMessage.class));

        consumer.poll();

        verify(consumerService).importAccount(any(AccountCreatedMessage.class));
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void doesNotDeleteMessage_whenServiceThrows() {
        doThrow(new RuntimeException("database unavailable"))
                .when(consumerService).importAccount(any(AccountCreatedMessage.class));

        consumer.poll();

        verify(consumerService).importAccount(any(AccountCreatedMessage.class));
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
}
