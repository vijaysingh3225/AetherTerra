# Product Requirements — Aether Terra

## Summary

Aether Terra is a clothing brand that sells 1-of-1 t-shirts. Every shirt is unique and made to order only after the auction winner is confirmed. The platform hosts live auctions, enforces bidder eligibility, and will eventually support standard product drops alongside auctions.

## Core User Flows

### Guest
- Browse live and past auctions
- View auction detail (shirt design, current bid, time remaining)
- Register for an account

### Registered User (Buyer)
- All guest capabilities
- Complete account setup: email verification, shirt size, saved payment method
- Place bids on live auctions
- View bid history and active bids
- Receive winner notification
- Pay for won auction (v2, via Stripe)

### Admin
- Create and schedule auctions
- Set starting bid, minimum increment, start/end times
- Cancel an auction (notifies bidders)
- View all users, bids, auctions
- Manage post-auction fulfillment (v2, via Shopify)

## MVP Scope (v1)

- [x] Monorepo scaffold
- [x] User registration + email verification
- [x] Login / logout (session or JWT)
- [x] Auction listing page
- [x] Auction detail page with bid form
- [x] Bid placement with pre-bid requirement enforcement
- [x] Admin CRUD for auctions
- [ ] Basic account page (profile, size, placeholder for payment)

## Out of Scope for v1

- Stripe payment capture
- Shopify order creation
- Standard (non-auction) product drops
- Real-time bid updates (WebSocket / SSE)
- Auction end-time extension logic
- Mobile-optimised design polish

## Non-Functional Requirements

- All API endpoints under `/api/v1/`
- Backend validated at the boundary (Jakarta Validation)
- No sensitive data logged
- Secrets via environment variables only
- Flyway for all schema changes — no manual DDL in production
