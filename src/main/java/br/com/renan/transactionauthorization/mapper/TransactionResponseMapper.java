package br.com.renan.transactionauthorization.mapper;

import org.springframework.stereotype.Component;

import br.com.renan.transactionauthorization.dto.AccountDataResponse;
import br.com.renan.transactionauthorization.dto.AmountResponse;
import br.com.renan.transactionauthorization.dto.BalanceResponse;
import br.com.renan.transactionauthorization.dto.TransactionDataResponse;
import br.com.renan.transactionauthorization.dto.TransactionResponse;
import br.com.renan.transactionauthorization.entity.AccountEntity;
import br.com.renan.transactionauthorization.entity.TransactionEntity;

/** Builds the challenge-shaped {@link TransactionResponse} from persisted state. */
@Component
public class TransactionResponseMapper {

    public TransactionResponse toResponse(TransactionEntity transaction, AccountEntity account) {
        TransactionDataResponse transactionData = new TransactionDataResponse(
                transaction.getId(),
                transaction.getType(),
                new AmountResponse(transaction.getAmount(), transaction.getCurrency()),
                transaction.getStatus(),
                transaction.getCreatedAt());

        AccountDataResponse accountData = new AccountDataResponse(
                account.getId(),
                new BalanceResponse(account.getBalanceAmount(), account.getCurrency()));

        return new TransactionResponse(transactionData, accountData);
    }
}
