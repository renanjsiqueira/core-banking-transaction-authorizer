package br.com.renan.transactionauthorization.config;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Builds the AWS SDK v2 {@link SqsClient}.
 *
 * <p>Behaviour is driven entirely by {@link SqsProperties}:
 * <ul>
 *   <li>If an {@code endpoint} is configured (LocalStack), it is applied via
 *       {@code endpointOverride}.</li>
 *   <li>If static credentials are provided (local), they are used; otherwise the
 *       SDK default provider chain (env vars, profile, IAM role) is used so the
 *       same code runs unchanged in AWS.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(SqsProperties.class)
public class AwsSqsConfig {

    @Bean
    public SqsClient sqsClient(SqsProperties properties) {
        SqsClientBuilderHelper.validate(properties);

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

    /** Fail fast on misconfiguration so the context does not start half-wired. */
    private static final class SqsClientBuilderHelper {
        private static void validate(SqsProperties p) {
            if (p.region() == null || p.region().isBlank()) {
                throw new IllegalStateException("app.aws.sqs.region must be configured");
            }
            if (p.queueUrl() == null || p.queueUrl().isBlank()) {
                throw new IllegalStateException("app.aws.sqs.queue-url must be configured");
            }
        }
    }
}
