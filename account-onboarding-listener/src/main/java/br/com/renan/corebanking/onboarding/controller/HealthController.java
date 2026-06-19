package br.com.renan.corebanking.onboarding.controller;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @Value("${spring.application.name:account-onboarding-listener}")
    private String applicationName;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "service", applicationName,
                "status", "UP",
                "timestamp", OffsetDateTime.now().toString());
    }
}
