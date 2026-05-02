-- Enforce data integrity at the database level as a second line of defence
ALTER TABLE auctions ADD CONSTRAINT chk_starting_bid_positive CHECK (starting_bid > 0);
ALTER TABLE auctions ADD CONSTRAINT chk_current_bid_positive  CHECK (current_bid > 0 OR current_bid IS NULL);
ALTER TABLE auctions ADD CONSTRAINT chk_auction_dates_valid   CHECK (starts_at < ends_at);
ALTER TABLE bids     ADD CONSTRAINT chk_bid_amount_positive   CHECK (amount > 0);
