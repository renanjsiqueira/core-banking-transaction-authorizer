package br.com.renan.corebanking.onboarding.config;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@EnableConfigurationProperties(SqsProperties.class)
public class AwsSqsConfig {
    @Bean
    public SqsClient sqsClient(SqsProperties properties) {
        validate(properties);

        var builder = SqsClient.builder()
                .region(Region.of(properties.region()));

        if (properties.hasEndpointOverride()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }

        if (properties.hasStaticCredentials()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    private static void validate(SqsProperties properties) {
        if (properties.region() == null || properties.region().isBlank()) {
            throw new IllegalStateException("app.aws.sqs.region must be configured");
        }
        if (properties.queueUrl() == null || properties.queueUrl().isBlank()) {
            throw new IllegalStateException("app.aws.sqs.queue-url must be configured");
        }
    }
}
