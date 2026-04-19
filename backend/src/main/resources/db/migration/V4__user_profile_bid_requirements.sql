ALTER TABLE users
    ADD COLUMN payment_method_brand VARCHAR(50),
    ADD COLUMN payment_method_last4 VARCHAR(4),
    ADD COLUMN payment_method_added_at TIMESTAMPTZ;
