package br.com.renan.transactionauthorization.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the AWS SQS integration, bound from {@code app.aws.sqs.*}.
 *
 * <p>For local development these point at LocalStack; in real environments the
 * {@code endpoint} is left blank so the AWS SDK resolves the regional endpoint,
 * and credentials are resolved by the default provider chain (IAM role).
 *
 * @param region               AWS region (e.g. {@code sa-east-1})
 * @param endpoint             optional endpoint override (LocalStack); blank = real AWS
 * @param queueUrl             full URL of the account-created queue
 * @param accessKey            static access key (local only; blank = default chain)
 * @param secretKey            static secret key (local only; blank = default chain)
 * @param maxNumberOfMessages  max messages per receive call (1..10)
 * @param waitTimeSeconds      long-polling wait time in seconds (0..20)
 * @param pollingEnabled       whether the scheduled SQS poller is active
 * @param pollDelayMillis      fixed delay between poll cycles, in milliseconds
 */
@ConfigurationProperties(prefix = "app.aws.sqs")
public record SqsProperties(
        String region,
        String endpoint,
        String queueUrl,
        String accessKey,
        String secretKey,
        Integer maxNumberOfMessages,
        Integer waitTimeSeconds,
        Boolean pollingEnabled,
        Long pollDelayMillis) {

    public boolean hasEndpointOverride() {
        return endpoint != null && !endpoint.isBlank();
    }

    public boolean hasStaticCredentials() {
        return accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
