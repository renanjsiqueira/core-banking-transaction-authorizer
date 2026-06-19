package br.com.renan.corebanking.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountCreatedMessage(AccountPayload account) {
}
