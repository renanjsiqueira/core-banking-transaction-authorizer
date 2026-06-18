-- ============================================================================
-- V1: initial schema for the transaction authorizer
--
-- Design notes:
--  * Monetary values use NUMERIC(19,4): exact decimal arithmetic (never float),
--    4 fractional digits cover every ISO-4217 minor unit (incl. 3-decimal
--    currencies). Currency is stored per row as a 3-char ISO-4217 code.
--  * The DEBIT authorization relies on an atomic conditional UPDATE
--    (balance >= amount), so no application-level lock is needed for the
--    hot path. A `version` column is kept for JPA optimistic locking on the
--    rare paths that read-modify-write through the entity.
--  * `transactions.id` is the client-supplied transactionId (path variable),
--    which makes the POST endpoint naturally idempotent.
-- ============================================================================

CREATE TABLE accounts (
    id            UUID            PRIMARY KEY,
    owner_id      UUID            NOT NULL,
    balance       NUMERIC(19, 4)  NOT NULL DEFAULT 0 CHECK (balance >= 0),
    currency      CHAR(3)         NOT NULL DEFAULT 'BRL',
    status        VARCHAR(20)     NOT NULL,
    created_at    TIMESTAMPTZ     NOT NULL,
    registered_at TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version       BIGINT          NOT NULL DEFAULT 0
);

CREATE TABLE transactions (
    id            UUID            PRIMARY KEY,
    account_id    UUID            NOT NULL REFERENCES accounts (id),
    type          VARCHAR(10)     NOT NULL,
    amount        NUMERIC(19, 4)  NOT NULL CHECK (amount > 0),
    currency      CHAR(3)         NOT NULL,
    status        VARCHAR(10)     NOT NULL,
    balance_after NUMERIC(19, 4)  NOT NULL,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Supports account-scoped statement queries ordered by time.
CREATE INDEX idx_transactions_account_created
    ON transactions (account_id, created_at DESC);
