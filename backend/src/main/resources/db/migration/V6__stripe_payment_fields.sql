-- Replace placeholder payment-method columns with real Stripe references.
-- Old columns (payment_method_brand, payment_method_last4) are dropped; the
-- timestamp column is reused under the same name for when the method was saved.

ALTER TABLE users
    DROP COLUMN IF EXISTS payment_method_brand,
    DROP COLUMN IF EXISTS payment_method_last4;

ALTER TABLE users
    ADD COLUMN stripe_customer_id    VARCHAR(255),
    ADD COLUMN stripe_payment_method_id VARCHAR(255),
    ADD COLUMN payment_method_ready  BOOLEAN NOT NULL DEFAULT FALSE;

-- payment_method_added_at already exists from V4; keep it as-is.

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_stripe_customer
    ON users (stripe_customer_id)
    WHERE stripe_customer_id IS NOT NULL;
