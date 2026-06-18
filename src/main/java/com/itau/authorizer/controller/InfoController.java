package com.itau.authorizer.controller;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight liveness/info endpoint kept separate from Actuator's
 * {@code /actuator/health}. Useful as a quick smoke test of the running app.
 */
@RestController
public class InfoController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
                "service", "transaction-authorizer",
                "status", "UP",
                "timestamp", OffsetDateTime.now().toString());
    }
}
