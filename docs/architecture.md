# Architecture — Aether Terra

## Overview

Aether Terra is a monorepo containing a React/TypeScript frontend and a Spring Boot backend, backed by PostgreSQL. The auction engine is custom-built and owned by this backend. Shopify and Stripe are external integrations used for complementary concerns.

```
browser
  └── React (Vite, TanStack Query)
        └── /api/* proxy → Spring Boot (port 8080)
                            └── PostgreSQL (port 5432)
```

## Monorepo Layout

```
AetherTerra/
├── frontend/      # React + Vite + TypeScript
├── backend/       # Spring Boot + Java 21 + Maven
├── infra/         # docker-compose, .env.example
├── docs/          # Architecture, rules, API outline, PRD
└── scripts/       # Helper shell scripts
```

## Frontend

| Concern        | Library                  |
|----------------|--------------------------|
| Framework      | React 18 + TypeScript    |
| Bundler        | Vite                     |
| Routing        | React Router v7          |
| Data fetching  | TanStack Query v5        |
| Styling        | Tailwind CSS v4          |

The frontend proxies all `/api` requests to `localhost:8080` in dev, so no CORS config is needed locally.

## Backend

| Concern        | Library / Tool           |
|----------------|--------------------------|
| Framework      | Spring Boot 3.4          |
| Language       | Java 21 (records, text blocks, virtual threads) |
| Build          | Maven (Spring Boot parent BOM simplifies dep management) |
| Persistence    | Spring Data JPA + Hibernate |
| Database       | PostgreSQL 16            |
| Migrations     | Flyway                   |
| Security       | Spring Security + JWT    |
| Observability  | Spring Actuator          |

### Package Structure

```
com.aetherterra
├── auth/       # Registration, login, JWT, email verification
├── users/      # User entity, profile, shirt size
├── auctions/   # Auction lifecycle, scheduling, listing
├── bids/       # Bid placement, validation, history
├── orders/     # AuctionOrder entity + status lifecycle
├── commerce/   # Shopify Draft Order creation (post-auction checkout)
├── payment/    # Stripe SetupIntent (bidder card qualification)
├── webhooks/   # Stripe + Shopify webhook receivers
├── admin/      # Admin-only management endpoints
├── notification/ # Email notifications (Mailpit local, real SMTP prod)
└── common/     # Shared config, security, response wrappers
```

## Database

PostgreSQL 16 managed by Flyway migrations. Schema lives in `backend/src/main/resources/db/migration/`.

| Migration | Description |
|-----------|-------------|
| V1 | Initial schema: users, auctions, bids |
| V2 | Email verification tokens |
| V3 | Cascade-delete email tokens on user delete |
| V4 | Shirt size + payment method brand/last4 on users |
| V5 | CHECK constraints on bids and auctions |
| V6 | Stripe customer/payment method fields on users |
| V7 | auction_orders table |
| V8 | Optimistic locking version column on auctions |
| V9 | Payment timeline columns on auction_orders; processed_shopify_webhooks idempotency table |

## External Integrations

| Integration | Purpose | Status |
|-------------|---------|--------|
| Stripe | Save payment methods (bidder qualification via SetupIntent) | Live |
| Shopify | Post-auction Draft Order + invoice link for winner payment | Live |
| Mailpit | Local SMTP stub for email testing | Local only |

### Payment Flow (v1)

```
1. Bidder qualifies — Stripe SetupIntent saves card on file (no charge).
2. Auction ends    — AuctionScheduler picks winner:
                       a. Persists AuctionOrder (PENDING_PAYMENT) to get a stable UUID.
                       b. Creates Shopify Draft Order with metadata (auction_order_id,
                          auction_id, user_id, winning_bid_id, shirt_size, source).
                       c. Sets payment_due_at = now + PAYMENT_DUE_HOURS (default 24h).
                       d. Emails winner the invoice URL (checkout link).
3. Winner pays     — Completes checkout via Shopify invoice (no Stripe charge in v1).
4. Shopify fires   — orders/paid webhook → POST /api/v1/webhooks/shopify
                       → Matches order by auction_order_id (primary) or auction_id (fallback)
                       → AuctionOrder status: PENDING_PAYMENT or EXPIRED → PAID
5. Expiry guard    — Scheduler (every 5 min) marks PENDING_PAYMENT orders EXPIRED
                     when payment_due_at has passed.
```

Stripe auto-charge of the winner is **intentionally deferred to v2**. The saved SetupIntent
(payment method on file) provides bidder friction and will be used for auto-charge later.
Shopify is the source of truth for actual order payment, tax, shipping, and fulfillment.

### Draft Order Metadata (custom_attributes → note_attributes)

Every Draft Order sent to Shopify carries these custom attributes. Shopify copies them to
the resulting Order's `note_attributes` when the invoice is paid, making them available in
the `orders/paid` webhook payload.

| Key                | Value                    | Purpose                                |
|--------------------|--------------------------|----------------------------------------|
| `auction_order_id` | AuctionOrder UUID        | Primary webhook matching key           |
| `auction_id`       | Auction UUID             | Fallback webhook matching key          |
| `user_id`          | User UUID                | Winner identity                        |
| `winning_bid_id`   | Bid UUID (if present)    | Bid traceability                       |
| `shirt_size`       | e.g. "L" (if set)        | Shirt size at time of order            |
| `source`           | `aetherterra_auction`    | Origin marker for Shopify admin        |

### Shopify Webhook Matching

The `orders/paid` handler resolves the AuctionOrder using a two-step strategy:

1. **Primary**: extract `auction_order_id` from `note_attributes` → `findById()`
2. **Fallback**: extract `auction_id` from `note_attributes` → `findByAuctionId()`

The fallback handles any Draft Orders created before the metadata was added.
Both result in the same unambiguous match because there is at most one order per auction.

### Shopify Webhook Setup

Register a webhook in your Shopify admin (Settings → Notifications → Webhooks):

| Field | Value |
|-------|-------|
| Event | Order payment (`orders/paid`) |
| URL   | `https://your-domain.com/api/v1/webhooks/shopify` |
| Format | JSON |

After creating the webhook, copy the **Signing Secret** from the Shopify UI and set it as
`SHOPIFY_WEBHOOK_SECRET` in your environment. See `.env.example` for details.

**Local testing:** Shopify cannot reach `localhost`. Use a tunnel (e.g. `ngrok http 8080`)
or send test requests manually with the correct HMAC header.

**Mock mode:** When `SHOPIFY_WEBHOOK_SECRET` is blank (the default locally), signature
verification is skipped and a warning is logged. Never deploy with a blank secret.

### Shopify Idempotency

Shopify retries unacknowledged webhooks up to 19 times. Every delivery carries a unique
`X-Shopify-Webhook-Id`. Processed IDs are stored in `processed_shopify_webhooks`; duplicate
deliveries are silently acknowledged with `{"status": "already_processed"}`.

## Local Development

See the root [README](../README.md) for setup instructions.
