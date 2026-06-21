ALTER TABLE transactions
    ADD COLUMN resulting_balance_amount NUMERIC(19, 2);

UPDATE transactions t
SET resulting_balance_amount = a.balance_amount
FROM accounts a
WHERE t.account_id = a.id
  AND t.resulting_balance_amount IS NULL;

ALTER TABLE transactions
    ALTER COLUMN resulting_balance_amount SET NOT NULL;
