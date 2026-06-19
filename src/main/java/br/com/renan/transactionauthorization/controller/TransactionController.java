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
import br.com.renan.transactionauthorization.mapper.TransactionResponseMapper;
import br.com.renan.transactionauthorization.service.AuthorizationResult;
import br.com.renan.transactionauthorization.service.TransactionAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * REST endpoint for transaction authorization.
 */
@RestController
@RequestMapping("/transactions")
@Tag(name = "Transactions", description = "Authorize CREDIT/DEBIT transactions")
public class TransactionController {

    private final TransactionAuthorizationService authorizationService;
    private final TransactionResponseMapper responseMapper;

    public TransactionController(TransactionAuthorizationService authorizationService,
                                 TransactionResponseMapper responseMapper) {
        this.authorizationService = authorizationService;
        this.responseMapper = responseMapper;
    }

    @PostMapping("/{transactionId}")
    @Operation(summary = "Authorize a transaction",
            description = "Processes a CREDIT or DEBIT authorization for the given account "
                    + "and returns the transaction outcome and the resulting account balance.")
    public ResponseEntity<TransactionResponse> authorize(
            @PathVariable UUID transactionId,
            @Valid @RequestBody TransactionRequest request) {

        AuthorizationResult result = authorizationService.authorize(transactionId, request);
        return ResponseEntity.ok(responseMapper.toResponse(result));
    }
}
