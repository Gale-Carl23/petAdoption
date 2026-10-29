ALTER TABLE donations
    ADD COLUMN payment_method VARCHAR(30) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN payment_reference VARCHAR(100);

CREATE INDEX ix_donations_payment_method_submitted
    ON donations (payment_method, submitted_at DESC);
