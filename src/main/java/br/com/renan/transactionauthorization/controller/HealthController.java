package br.com.renan.transactionauthorization.controller;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal liveness/info endpoint, complementary to Actuator's
 * {@code /actuator/health}. Handy as a quick smoke test that the app booted.
 * No business logic is exposed here.
 */
@RestController
public class HealthController {

    @Value("${spring.application.name:transaction-authorization}")
    private String applicationName;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "service", applicationName,
                "status", "UP",
                "timestamp", OffsetDateTime.now().toString());
    }
}
