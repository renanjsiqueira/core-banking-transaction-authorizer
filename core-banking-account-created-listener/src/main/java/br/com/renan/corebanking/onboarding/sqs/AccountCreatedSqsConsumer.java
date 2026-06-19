package br.com.renan.corebanking.onboarding.sqs;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.renan.corebanking.onboarding.config.SqsProperties;
import br.com.renan.corebanking.onboarding.dto.sqs.AccountCreatedMessageDTO;
import br.com.renan.corebanking.onboarding.exception.InvalidAccountMessageException;
import br.com.renan.corebanking.onboarding.service.AccountCreatedConsumerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Component
@ConditionalOnProperty(prefix = "app.aws.sqs", name = "polling-enabled", havingValue = "true")
public class AccountCreatedSqsConsumer {
    private static final Logger log = LoggerFactory.getLogger(AccountCreatedSqsConsumer.class);

    private final SqsClient sqsClient;
    private final SqsProperties properties;
    private final AccountCreatedConsumerService consumerService;
    private final ObjectMapper objectMapper;
    private final Counter processedCounter;
    private final Counter failedCounter;

    public AccountCreatedSqsConsumer(SqsClient sqsClient,
                                     SqsProperties properties,
                                     AccountCreatedConsumerService consumerService,
                                     ObjectMapper objectMapper,
                                     MeterRegistry meterRegistry) {
        this.sqsClient = sqsClient;
        this.properties = properties;
        this.consumerService = consumerService;
        this.objectMapper = objectMapper;
        this.processedCounter = Counter.builder("sqs.account-created.messages.processed.total")
                .description("Account-created messages processed successfully")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("sqs.account-created.messages.failed.total")
                .description("Account-created messages that failed processing")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.aws.sqs.polling-delay-ms:1000}")
    public void poll() {
        List<Message> messages = receiveMessages();
        if (messages.isEmpty()) {
            return;
        }
        log.debug("Received {} account-created message(s)", messages.size());
        for (Message message : messages) {
            handleMessage(message);
        }
    }

    private List<Message> receiveMessages() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(properties.queueUrl())
                .maxNumberOfMessages(properties.maxNumberOfMessages())
                .waitTimeSeconds(properties.waitTimeSeconds())
                .build();
        return sqsClient.receiveMessage(request).messages();
    }

    private void handleMessage(Message message) {
        AccountCreatedMessageDTO payload;
        try {
            payload = objectMapper.readValue(message.body(), AccountCreatedMessageDTO.class);
        } catch (Exception e) {
            failedCounter.increment();
            log.warn("Invalid account created message: messageId={}, error={}",
                    message.messageId(), e.getMessage());
            return;
        }

        String accountId = payload.account() != null ? payload.account().id() : null;
        try {
            consumerService.importAccount(payload);
            deleteMessage(message);
            processedCounter.increment();
            log.info("Account created message processed: messageId={}, accountId={}, status=SUCCESS",
                    message.messageId(), accountId);
        } catch (InvalidAccountMessageException e) {
            failedCounter.increment();
            log.warn("Invalid account created message: messageId={}, accountId={}, error={}",
                    message.messageId(), accountId, e.getMessage());
        } catch (Exception e) {
            failedCounter.increment();
            log.error("Failed to process account created message: messageId={}, accountId={}",
                    message.messageId(), accountId, e);
        }
    }

    private void deleteMessage(Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(properties.queueUrl())
                .receiptHandle(message.receiptHandle())
                .build());
    }
}
