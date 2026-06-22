package br.com.renan.corebanking.onboarding.sqs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Component
@ConditionalOnProperty(prefix = "app.aws.sqs", name = "polling-enabled", havingValue = "true")
public class AccountCreatedSqsConsumer {
    private static final Logger log = LoggerFactory.getLogger(AccountCreatedSqsConsumer.class);
    private static final int DEFAULT_CONCURRENCY = 5;
    private static final int DELETE_BATCH_LIMIT = 10;

    private final SqsClient sqsClient;
    private final SqsProperties properties;
    private final AccountCreatedConsumerService consumerService;
    private final ObjectMapper objectMapper;
    private final ExecutorService workerPool;
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
        this.workerPool = Executors.newFixedThreadPool(resolveConcurrency(properties), threadFactory());
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

        List<CompletableFuture<Message>> inFlight = new ArrayList<>(messages.size());
        for (Message message : messages) {
            inFlight.add(CompletableFuture.supplyAsync(
                    () -> handleMessage(message) ? message : null, workerPool));
        }

        List<Message> processed = new ArrayList<>(messages.size());
        for (CompletableFuture<Message> future : inFlight) {
            Message message = future.join();
            if (message != null) {
                processed.add(message);
            }
        }
        deleteProcessedMessages(processed);
    }

    private List<Message> receiveMessages() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(properties.queueUrl())
                .maxNumberOfMessages(properties.maxNumberOfMessages())
                .waitTimeSeconds(properties.waitTimeSeconds())
                .build();
        return sqsClient.receiveMessage(request).messages();
    }

    private boolean handleMessage(Message message) {
        AccountCreatedMessageDTO payload;
        try {
            payload = objectMapper.readValue(message.body(), AccountCreatedMessageDTO.class);
        } catch (Exception e) {
            failedCounter.increment();
            log.warn("Invalid account created message: messageId={}, error={}",
                    message.messageId(), e.getMessage());
            return false;
        }

        String accountId = payload.account() != null ? payload.account().id() : null;
        try {
            consumerService.importAccount(payload);
            processedCounter.increment();
            log.info("Account created message processed: messageId={}, accountId={}, status=SUCCESS",
                    message.messageId(), accountId);
            return true;
        } catch (InvalidAccountMessageException e) {
            failedCounter.increment();
            log.warn("Invalid account created message: messageId={}, accountId={}, error={}",
                    message.messageId(), accountId, e.getMessage());
            return false;
        } catch (Exception e) {
            failedCounter.increment();
            log.error("Failed to process account created message: messageId={}, accountId={}",
                    message.messageId(), accountId, e);
            return false;
        }
    }

    private void deleteProcessedMessages(List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        for (int offset = 0; offset < messages.size(); offset += DELETE_BATCH_LIMIT) {
            List<Message> chunk = messages.subList(offset, Math.min(offset + DELETE_BATCH_LIMIT, messages.size()));
            List<DeleteMessageBatchRequestEntry> entries = new ArrayList<>(chunk.size());
            for (int i = 0; i < chunk.size(); i++) {
                entries.add(DeleteMessageBatchRequestEntry.builder()
                        .id(String.valueOf(i))
                        .receiptHandle(chunk.get(i).receiptHandle())
                        .build());
            }
            DeleteMessageBatchResponse response = sqsClient.deleteMessageBatch(DeleteMessageBatchRequest.builder()
                    .queueUrl(properties.queueUrl())
                    .entries(entries)
                    .build());
            if (response.hasFailed() && !response.failed().isEmpty()) {
                log.warn("Failed to delete {} processed message(s); they will be redelivered (import is idempotent)",
                        response.failed().size());
            }
        }
    }

    @PreDestroy
    void shutdown() {
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workerPool.shutdownNow();
        }
    }

    private static int resolveConcurrency(SqsProperties properties) {
        Integer configured = properties.concurrency();
        return configured != null && configured > 0 ? configured : DEFAULT_CONCURRENCY;
    }

    private static ThreadFactory threadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "account-created-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
