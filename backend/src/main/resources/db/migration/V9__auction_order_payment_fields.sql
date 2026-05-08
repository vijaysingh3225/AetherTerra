-- Payment lifecycle timestamps and failure tracking on auction_orders.
-- Idempotency table for Shopify webhook deliveries.

ALTER TABLE auction_orders
    ADD COLUMN payment_due_at  TIMESTAMPTZ,
    ADD COLUMN paid_at         TIMESTAMPTZ,
    ADD COLUMN expired_at      TIMESTAMPTZ,
    ADD COLUMN failure_reason  VARCHAR(500);

-- Shopify retries each webhook up to 19 times. Store the X-Shopify-Webhook-Id
-- of every delivery we have processed so duplicates are silently ignored.
CREATE TABLE processed_shopify_webhooks (
    webhook_id    VARCHAR(255) PRIMARY KEY,
    topic         VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
