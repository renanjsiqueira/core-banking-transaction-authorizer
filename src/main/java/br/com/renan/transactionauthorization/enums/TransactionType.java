package br.com.renan.transactionauthorization.enums;

/**
 * Type of a financial transaction.
 */
public enum TransactionType {

    /** Adds the transaction amount to the account balance. */
    CREDIT,

    /** Subtracts the transaction amount from the account balance. */
    DEBIT
}
