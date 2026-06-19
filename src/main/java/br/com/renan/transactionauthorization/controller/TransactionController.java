package br.com.renan.transactionauthorization.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.renan.transactionauthorization.dto.TransactionRequest;
import br.com.renan.transactionauthorization.dto.TransactionResponse;
import br.com.renan.transactionauthorization.service.TransactionAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * REST endpoint for transaction authorization.
 *
 * <p>Business outcomes (SUCCEEDED, or FAILED due to insufficient funds / disabled
 * account) return {@code 200 OK} with the outcome in the body. Errors are mapped
 * by {@code ApiExceptionHandler}: 400 (validation), 404 (account not found),
 * 409 (idempotency conflict), 500 (unexpected).
 */
@RestController
@RequestMapping("/transactions")
@Tag(name = "Transactions", description = "Authorize CREDIT/DEBIT transactions")
public class TransactionController {

    private final TransactionAuthorizationService authorizationService;

    public TransactionController(TransactionAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @PostMapping("/{transactionId}")
    @Operation(summary = "Authorize a transaction",
            description = "Processes a CREDIT or DEBIT authorization for the given account "
                    + "and returns the transaction outcome and the resulting account balance. "
                    + "Idempotent by transactionId.")
    public ResponseEntity<TransactionResponse> authorize(
            @PathVariable UUID transactionId,
            @Valid @RequestBody TransactionRequest request) {

        return ResponseEntity.ok(authorizationService.authorize(transactionId, request));
    }
}
