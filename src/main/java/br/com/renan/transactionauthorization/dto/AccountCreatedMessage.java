package br.com.renan.transactionauthorization.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Envelope of the SQS account-created message.
 *
 * <pre>{@code
 * { "account": { "id": "...", "owner": "...", "created_at": "...", "status": "..." } }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountCreatedMessage(AccountPayload account) {
}
