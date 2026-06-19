package br.com.renan.corebanking.authorization.mapper;

import org.springframework.stereotype.Component;

import br.com.renan.corebanking.authorization.dto.AccountDataResponse;
import br.com.renan.corebanking.authorization.dto.AmountResponse;
import br.com.renan.corebanking.authorization.dto.BalanceResponse;
import br.com.renan.corebanking.authorization.dto.TransactionDataResponse;
import br.com.renan.corebanking.authorization.dto.TransactionResponse;
import br.com.renan.corebanking.authorization.entity.AccountEntity;
import br.com.renan.corebanking.authorization.entity.TransactionEntity;

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
