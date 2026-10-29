ALTER TABLE donations
    ALTER COLUMN proof_file_path DROP NOT NULL,
    ADD COLUMN paymongo_session_id VARCHAR(100),
    ADD COLUMN paymongo_checkout_url VARCHAR(1000),
    ADD COLUMN payment_idempotency_key VARCHAR(36);

CREATE UNIQUE INDEX uq_donations_paymongo_session
    ON donations (paymongo_session_id)
    WHERE paymongo_session_id IS NOT NULL;

CREATE UNIQUE INDEX uq_donations_payment_idempotency
    ON donations (payment_idempotency_key)
    WHERE payment_idempotency_key IS NOT NULL;

CREATE TABLE processed_payment_webhooks (
    event_id VARCHAR(100) PRIMARY KEY,
    received_at TIMESTAMP NOT NULL
);
