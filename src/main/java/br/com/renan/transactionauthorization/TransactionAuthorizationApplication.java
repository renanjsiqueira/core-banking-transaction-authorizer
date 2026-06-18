package br.com.renan.transactionauthorization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the core-banking transaction authorization service.
 *
 * <p>The service will expose a REST API to authorize CREDIT/DEBIT transactions
 * and consume account-created events from an AWS SQS queue. This phase only
 * wires the local infrastructure (PostgreSQL + LocalStack/SQS) and the
 * {@code SqsClient}; no business rules are implemented yet.
 */
@SpringBootApplication
public class TransactionAuthorizationApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionAuthorizationApplication.class, args);
    }
}
