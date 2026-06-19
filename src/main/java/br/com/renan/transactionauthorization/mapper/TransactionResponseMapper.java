package br.com.renan.transactionauthorization.mapper;

import org.springframework.stereotype.Component;

import br.com.renan.transactionauthorization.dto.AccountDataResponse;
import br.com.renan.transactionauthorization.dto.AmountResponse;
import br.com.renan.transactionauthorization.dto.BalanceResponse;
import br.com.renan.transactionauthorization.dto.TransactionDataResponse;
import br.com.renan.transactionauthorization.dto.TransactionResponse;
import br.com.renan.transactionauthorization.service.AuthorizationResult;

/** Builds the challenge-shaped {@link TransactionResponse} from an {@link AuthorizationResult}. */
@Component
public class TransactionResponseMapper {

    public TransactionResponse toResponse(AuthorizationResult result) {
        TransactionDataResponse transaction = new TransactionDataResponse(
                result.transactionId(),
                result.type(),
                new AmountResponse(result.amount(), result.currency()),
                result.status(),
                result.timestamp());

        AccountDataResponse account = new AccountDataResponse(
                result.accountId(),
                new BalanceResponse(result.balanceAmount(), result.balanceCurrency()));

        return new TransactionResponse(transaction, account);
    }
}
