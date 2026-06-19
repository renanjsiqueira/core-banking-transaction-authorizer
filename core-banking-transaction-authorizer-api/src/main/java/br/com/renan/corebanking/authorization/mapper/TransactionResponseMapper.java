package br.com.renan.corebanking.authorization.mapper;

import org.springframework.stereotype.Component;

import br.com.renan.corebanking.authorization.dto.response.AccountPayloadDTO;
import br.com.renan.corebanking.authorization.dto.response.AmountPayloadDTO;
import br.com.renan.corebanking.authorization.dto.response.BalancePayloadDTO;
import br.com.renan.corebanking.authorization.dto.response.TransactionPayloadDTO;
import br.com.renan.corebanking.authorization.dto.response.TransactionResponseDTO;
import br.com.renan.corebanking.authorization.model.Account;
import br.com.renan.corebanking.authorization.model.Transaction;

@Component
public class TransactionResponseMapper {
    public TransactionResponseDTO toResponse(Transaction transaction, Account account) {
        TransactionPayloadDTO transactionData = new TransactionPayloadDTO(
                transaction.getId(),
                transaction.getType(),
                new AmountPayloadDTO(transaction.getAmount(), transaction.getCurrency()),
                transaction.getStatus(),
                transaction.getCreatedAt());

        AccountPayloadDTO accountData = new AccountPayloadDTO(
                account.getId(),
                new BalancePayloadDTO(account.getBalanceAmount(), account.getCurrency()));

        return new TransactionResponseDTO(transactionData, accountData);
    }
}
