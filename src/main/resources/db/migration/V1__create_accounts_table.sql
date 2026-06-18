-- ============================================================================
-- V1: accounts table
--
-- Stores the current balance and lifecycle status of each account imported
-- from the account-opening stream. Money is NUMERIC(19,2) (exact decimal,
-- never float). `version` backs JPA optimistic locking.
-- ============================================================================

CREATE TABLE accounts (
    id                UUID           PRIMARY KEY,
    owner_id          UUID           NOT NULL,
    status            VARCHAR(30)    NOT NULL,
    balance_amount    NUMERIC(19, 2) NOT NULL,
    currency          CHAR(3)        NOT NULL,
    source_created_at TIMESTAMPTZ    NOT NULL,
    imported_at       TIMESTAMPTZ    NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_accounts_owner_id ON accounts (owner_id);
CREATE INDEX idx_accounts_status   ON accounts (status);
