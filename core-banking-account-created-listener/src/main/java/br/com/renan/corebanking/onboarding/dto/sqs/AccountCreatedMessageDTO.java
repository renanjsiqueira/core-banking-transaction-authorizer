package br.com.renan.corebanking.onboarding.dto.sqs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountCreatedMessageDTO(AccountCreatedPayloadDTO account) {
}
