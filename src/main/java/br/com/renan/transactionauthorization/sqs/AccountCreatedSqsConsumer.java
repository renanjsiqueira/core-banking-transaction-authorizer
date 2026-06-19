package br.com.renan.transactionauthorization.sqs;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.renan.transactionauthorization.config.SqsProperties;
import br.com.renan.transactionauthorization.dto.AccountCreatedMessage;
import br.com.renan.transactionauthorization.exception.InvalidAccountMessageException;
import br.com.renan.transactionauthorization.service.AccountCreatedConsumerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * Polls the {@code conta-bancaria-criada} SQS queue and imports accounts.
 *
 * <p>Active only when {@code app.aws.sqs.polling-enabled=true}. Uses long polling
 * and processes up to {@code max-number-of-messages} per cycle. A message is
 * deleted <strong>only after</strong> a successful import; on any error it is
 * left in the queue so SQS redelivers it (at-least-once), which is safe because
 * the import is idempotent.
 */
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

    @Scheduled(fixedDelayString = "${app.aws.sqs.poll-delay-millis:1000}")
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
        AccountCreatedMessage payload;
        try {
            payload = objectMapper.readValue(message.body(), AccountCreatedMessage.class);
        } catch (Exception e) {
            // Unparseable body: permanent failure, kept for DLQ/redrive (future).
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
            // Transient failure (e.g. DB unavailable): do NOT delete, let SQS retry.
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
