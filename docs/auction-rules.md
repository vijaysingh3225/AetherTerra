# Auction Rules — Aether Terra

> Version 1 business rules. Updated when rules change; keep this file authoritative.

## Access

- **Guests** (unauthenticated users) may browse live and past auctions but cannot place bids.
- **Registered users** may bid once all pre-bid requirements are satisfied.

## Pre-Bid Requirements

A registered user must complete all three before their first bid is accepted:

1. **Email verification** — must click the verification link sent at registration.
2. **Shirt size selected** — size must be saved on the account before placing a bid. Prompted at bid time if not yet set; can also be set in advance via the Account page. Required because shirts are made-to-order after the auction ends.
3. **Saved payment method** — a valid payment method must be attached to the account via Stripe (integration pending v2).

## Bidding Rules

- Bids are **final**. No bid retraction in v1.
- Each bid must be strictly greater than the current highest bid.
- The minimum increment will be defined per auction by the admin.
- A user may place multiple bids; only the highest stands.

## Auction Lifecycle

| Status      | Description                                              |
|-------------|----------------------------------------------------------|
| `SCHEDULED` | Created by admin, not yet open to bids.                  |
| `LIVE`      | Accepting bids within the defined start/end window.      |
| `ENDED`     | Bidding window closed; winner determined.                |
| `CANCELLED` | Admin cancelled before or during the live window.        |

- Admin may **create**, **schedule**, and **cancel** auctions.
- A cancelled auction notifies all previous bidders (email flow pending v2).
- Auction end time may be **extended** by admin if a bid is placed in the last N minutes (exact rule TBD).

## Winner & Fulfillment

- When an auction ends, the highest bidder is the winner.
- The winner is charged via their saved Stripe payment method (v2).
- A Shopify order is created post-payment for fulfillment tracking (v2).
- The shirt is made to order *after* the winner pays. Lead time TBD.

## Future: Standard Product Drops

- The same platform will eventually support standard (non-auction) product drops.
- These will use the Shopify storefront for purchase flow.
- Auction and drop inventory are separate domains; no shared cart in v1.
