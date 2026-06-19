package br.com.renan.corebanking.onboarding.sqs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.renan.corebanking.onboarding.config.SqsProperties;
import br.com.renan.corebanking.onboarding.dto.sqs.AccountCreatedMessageDTO;
import br.com.renan.corebanking.onboarding.service.AccountCreatedConsumerService;
import br.com.renan.corebanking.onboarding.support.AccountCreatedMessageTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

class AccountCreatedSqsConsumerTest {
    private static final String QUEUE_URL = "http://localhost:4566/000000000000/conta-bancaria-criada";

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
    }

    private static Message message(String accountId) {
        return Message.builder()
                .messageId("msg-" + accountId)
                .receiptHandle("receipt-" + accountId)
                .body(AccountCreatedMessageTestFactory.validJson(accountId))
                .build();
    }

    private void givenQueueReturns(Message... messages) {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(messages).build());
    }

    @Test
    void deletesMessageWhenProcessingSucceeds() {
        givenQueueReturns(message(AccountCreatedMessageTestFactory.VALID_ID));
        doNothing().when(consumerService).importAccount(any(AccountCreatedMessageDTO.class));

        consumer.poll();

        verify(consumerService).importAccount(any(AccountCreatedMessageDTO.class));
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void doesNotDeleteMessageWhenServiceThrows() {
        givenQueueReturns(message(AccountCreatedMessageTestFactory.VALID_ID));
        doThrow(new RuntimeException("database unavailable"))
                .when(consumerService).importAccount(any(AccountCreatedMessageDTO.class));

        consumer.poll();

        verify(consumerService).importAccount(any(AccountCreatedMessageDTO.class));
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void emptyQueueDoesNotProcessOrDelete() {
        givenQueueReturns();

        consumer.poll();

        verify(consumerService, never()).importAccount(any());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void multipleMessagesAreProcessedIndividually() {
        givenQueueReturns(
                message(UUID.randomUUID().toString()),
                message(UUID.randomUUID().toString()),
                message(UUID.randomUUID().toString()));
        doNothing().when(consumerService).importAccount(any(AccountCreatedMessageDTO.class));

        consumer.poll();

        verify(consumerService, times(3)).importAccount(any(AccountCreatedMessageDTO.class));
        verify(sqsClient, times(3)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void errorInOneMessageDoesNotStopOthers() {
        String failingId = UUID.randomUUID().toString();
        givenQueueReturns(
                message(UUID.randomUUID().toString()),
                message(failingId),
                message(UUID.randomUUID().toString()));
        doThrow(new RuntimeException("boom"))
                .when(consumerService).importAccount(argThat(m ->
                        m != null && m.account() != null && failingId.equals(m.account().id())));

        consumer.poll();

        verify(consumerService, times(3)).importAccount(any(AccountCreatedMessageDTO.class));
        verify(sqsClient, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }
}
