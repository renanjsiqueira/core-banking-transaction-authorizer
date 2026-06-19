package br.com.renan.transactionauthorization.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.renan.transactionauthorization.dto.AccountDataResponse;
import br.com.renan.transactionauthorization.dto.AmountResponse;
import br.com.renan.transactionauthorization.dto.BalanceResponse;
import br.com.renan.transactionauthorization.dto.TransactionDataResponse;
import br.com.renan.transactionauthorization.dto.TransactionRequest;
import br.com.renan.transactionauthorization.dto.TransactionResponse;
import br.com.renan.transactionauthorization.enums.TransactionStatus;
import br.com.renan.transactionauthorization.enums.TransactionType;
import br.com.renan.transactionauthorization.exception.AccountNotFoundException;
import br.com.renan.transactionauthorization.exception.TransactionConflictException;
import br.com.renan.transactionauthorization.service.TransactionAuthorizationService;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    private static final String TX_ID = "8e8ae808-b154-48b5-9f3e-553935cc4543";
    private static final String ACCOUNT_ID = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionAuthorizationService authorizationService;

    private static String validBody() {
        return """
                {"accountId":"%s","type":"CREDIT","amount":{"value":97.07,"currency":"BRL"}}
                """.formatted(ACCOUNT_ID);
    }

    private TransactionResponse sampleResponse() {
        return new TransactionResponse(
                new TransactionDataResponse(
                        UUID.fromString(TX_ID), TransactionType.CREDIT,
                        new AmountResponse(new BigDecimal("97.07"), "BRL"),
                        TransactionStatus.SUCCEEDED, OffsetDateTime.now()),
                new AccountDataResponse(
                        UUID.fromString(ACCOUNT_ID),
                        new BalanceResponse(new BigDecimal("183.12"), "BRL")));
    }

    @Test
    void validRequest_returns200WithContract() throws Exception {
        when(authorizationService.authorize(eq(UUID.fromString(TX_ID)), any(TransactionRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/transactions/{transactionId}", TX_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.id").value(TX_ID))
                .andExpect(jsonPath("$.transaction.type").value("CREDIT"))
                .andExpect(jsonPath("$.transaction.amount.value").value(97.07))
                .andExpect(jsonPath("$.transaction.amount.currency").value("BRL"))
                .andExpect(jsonPath("$.transaction.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.transaction.timestamp").exists())
                .andExpect(jsonPath("$.account.id").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.account.balance.amount").value(183.12))
                .andExpect(jsonPath("$.account.balance.currency").value("BRL"));
    }

    @Test
    void invalidTransactionId_returns400() throws Exception {
        mockMvc.perform(post("/transactions/{transactionId}", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingAccountId_returns400() throws Exception {
        String body = """
                {"type":"CREDIT","amount":{"value":97.07,"currency":"BRL"}}
                """;
        mockMvc.perform(post("/transactions/{transactionId}", TX_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingType_returns400() throws Exception {
        String body = """
                {"accountId":"%s","amount":{"value":97.07,"currency":"BRL"}}
                """.formatted(ACCOUNT_ID);
        mockMvc.perform(post("/transactions/{transactionId}", TX_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidType_returns400() throws Exception {
        String body = """
                {"accountId":"%s","type":"TRANSFER","amount":{"value":97.07,"currency":"BRL"}}
                """.formatted(ACCOUNT_ID);
        mockMvc.perform(post("/transactions/{transactionId}", TX_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingAmount_returns400() throws Exception {
        String body = """
                {"accountId":"%s","type":"CREDIT"}
                """.formatted(ACCOUNT_ID);
        mockMvc.perform(post("/transactions/{transactionId}", TX_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zeroAmountValue_returns400() throws Exception {
        String body = """
                {"accountId":"%s","type":"CREDIT","amount":{"value":0,"currency":"BRL"}}
                """.formatted(ACCOUNT_ID);
        mockMvc.perform(post("/transactions/{transactionId}", TX_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeAmountValue_returns400() throws Exception {
        String body = """
                {"accountId":"%s","type":"CREDIT","amount":{"value":-10.00,"currency":"BRL"}}
                """.formatted(ACCOUNT_ID);
        mockMvc.perform(post("/transactions/{transactionId}", TX_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingCurrency_returns400() throws Exception {
        String body = """
                {"accountId":"%s","type":"CREDIT","amount":{"value":97.07}}
                """.formatted(ACCOUNT_ID);
        mockMvc.perform(post("/transactions/{transactionId}", TX_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonBrlCurrency_returns400() throws Exception {
        String body = """
                {"accountId":"%s","type":"CREDIT","amount":{"value":97.07,"currency":"USD"}}
                """.formatted(ACCOUNT_ID);
        mockMvc.perform(post("/transactions/{transactionId}", TX_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accountNotFound_returns404() throws Exception {
        when(authorizationService.authorize(any(UUID.class), any(TransactionRequest.class)))
                .thenThrow(new AccountNotFoundException(UUID.fromString(ACCOUNT_ID)));

        mockMvc.perform(post("/transactions/{transactionId}", TX_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    void transactionConflict_returns409() throws Exception {
        when(authorizationService.authorize(any(UUID.class), any(TransactionRequest.class)))
                .thenThrow(new TransactionConflictException("transactionId replayed with different parameters"));

        mockMvc.perform(post("/transactions/{transactionId}", TX_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isConflict());
    }
}
