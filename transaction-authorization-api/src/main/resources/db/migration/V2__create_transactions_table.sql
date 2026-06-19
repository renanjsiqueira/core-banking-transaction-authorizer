CREATE TABLE transactions (
    id             UUID           PRIMARY KEY,
    account_id     UUID           NOT NULL,
    type           VARCHAR(20)    NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    currency       CHAR(3)        NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    failure_reason VARCHAR(50),
    requested_at   TIMESTAMPTZ    NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_transactions_account
        FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE INDEX idx_transactions_account_id      ON transactions (account_id);
CREATE INDEX idx_transactions_account_created ON transactions (account_id, created_at);
CREATE INDEX idx_transactions_status          ON transactions (status);
CREATE INDEX idx_transactions_created_at      ON transactions (created_at);
