-- Tracks the post-auction winner checkout created via the CommerceOrderProvider.
-- One order per auction (unique constraint prevents duplicate checkout creation).

CREATE TABLE auction_orders (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auction_id         UUID        NOT NULL REFERENCES auctions(id),
    user_id            UUID        NOT NULL REFERENCES users(id),
    winning_bid_id     UUID        REFERENCES bids(id),
    amount             NUMERIC(10, 2) NOT NULL CHECK (amount > 0),
    currency           VARCHAR(10) NOT NULL DEFAULT 'USD',
    shirt_size         VARCHAR(10),
    provider           VARCHAR(50) NOT NULL,
    provider_order_id  VARCHAR(255),
    checkout_url       TEXT,
    status             VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_auction_orders_auction UNIQUE (auction_id)
);

CREATE INDEX idx_auction_orders_user ON auction_orders (user_id);
