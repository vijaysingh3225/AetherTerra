-- V1: Initial schema for Aether Terra auction platform
-- Keep minimal but future-friendly. No foreign key constraints broken here;
-- expand columns as features are built rather than anticipating everything now.

CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    shirt_size  VARCHAR(10),                    -- required before bidding
    email_verified_at TIMESTAMPTZ,              -- NULL = not yet verified
    role        VARCHAR(20) NOT NULL DEFAULT 'BUYER',  -- BUYER | ADMIN
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE auctions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        VARCHAR(100) NOT NULL UNIQUE,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',  -- SCHEDULED | LIVE | ENDED | CANCELLED
    starting_bid NUMERIC(10, 2) NOT NULL,
    current_bid  NUMERIC(10, 2),
    winner_id    UUID REFERENCES users(id),
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ NOT NULL,
    created_by  UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE bids (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auction_id  UUID NOT NULL REFERENCES auctions(id),
    bidder_id   UUID NOT NULL REFERENCES users(id),
    amount      NUMERIC(10, 2) NOT NULL,
    placed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
    -- No retraction flag in v1; bids are final once placed
);

-- Indexes for expected hot paths
CREATE INDEX idx_auctions_status   ON auctions(status);
CREATE INDEX idx_auctions_ends_at  ON auctions(ends_at);
CREATE INDEX idx_bids_auction_id   ON bids(auction_id);
CREATE INDEX idx_bids_bidder_id    ON bids(bidder_id);
