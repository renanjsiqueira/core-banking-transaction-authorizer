package com.itau.authorizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the core-banking transaction authorizer service.
 *
 * <p>The service exposes a REST API to authorize CREDIT/DEBIT transactions and
 * consumes account-created events from an AWS SQS queue.
 */
@SpringBootApplication
@EnableScheduling
public class TransactionAuthorizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionAuthorizerApplication.class, args);
    }
}
