package br.com.renan.transactionauthorization.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Account data carried by an account-created message.
 *
 * @param id        account identifier (UUID, as string)
 * @param owner     owner identifier (UUID, as string)
 * @param createdAt account creation time as epoch seconds (string)
 * @param status    account status (e.g. {@code ENABLED})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountPayload(
        String id,
        String owner,
        @JsonProperty("created_at") String createdAt,
        String status) {
}
