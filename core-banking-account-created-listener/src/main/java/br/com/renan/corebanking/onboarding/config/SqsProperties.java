package br.com.renan.corebanking.onboarding.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.aws.sqs")
public record SqsProperties(
        String region,
        String endpoint,
        String queueUrl,
        String accessKey,
        String secretKey,
        Integer maxNumberOfMessages,
        Integer waitTimeSeconds,
        Duration apiCallTimeout,
        Duration apiCallAttemptTimeout,
        Boolean pollingEnabled,
        Long pollingDelayMs) {
    public boolean hasEndpointOverride() {
        return endpoint != null && !endpoint.isBlank();
    }

    public boolean hasStaticCredentials() {
        return accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
