package br.com.renan.corebanking.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountPayload(
        String id,
        String owner,
        @JsonProperty("created_at") String createdAt,
        String status) {
}
