package br.com.renan.transactionauthorization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the core-banking transaction authorization service.
 *
 * <p>The service consumes account-created events from an AWS SQS queue and will
 * (in later phases) expose a REST API to authorize CREDIT/DEBIT transactions.
 * Scheduling is enabled to drive the SQS poller (see
 * {@code AccountCreatedSqsConsumer}).
 */
@SpringBootApplication
@EnableScheduling
public class TransactionAuthorizationApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionAuthorizationApplication.class, args);
    }
}
