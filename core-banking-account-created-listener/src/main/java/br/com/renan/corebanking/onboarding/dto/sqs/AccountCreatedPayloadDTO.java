package br.com.renan.corebanking.onboarding.dto.sqs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountCreatedPayloadDTO(
        String id,
        String owner,
        @JsonProperty("created_at") String createdAt,
        String status) {
}
