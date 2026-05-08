# Auction Rules — Aether Terra

> Version 1 business rules. Updated when rules change; keep this file authoritative.

## Access

- **Guests** (unauthenticated users) may browse live and past auctions but cannot place bids.
- **Registered users** may bid once all pre-bid requirements are satisfied.

## Pre-Bid Requirements

A registered user must complete all three before their first bid is accepted:

1. **Email verification** — must click the verification link sent at registration.
2. **Shirt size selected** — must be saved on the account before placing a bid. Required because shirts are made-to-order after the auction ends.
3. **Saved payment method** — a valid Stripe payment method must be attached to the account (SetupIntent flow on the Account page). This is for bidder qualification only; no charge is made until the winner pays via Shopify invoice.

## Bidding Rules

- Bids are **final**. No bid retraction in v1.
- Each bid must be strictly greater than the current highest bid.
- A user may place multiple bids; only the highest stands.

## Auction Lifecycle

| Status      | Description                                              |
|-------------|----------------------------------------------------------|
| `SCHEDULED` | Created by admin, not yet open to bids.                  |
| `LIVE`      | Accepting bids within the defined start/end window.      |
| `ENDED`     | Bidding window closed; winner determined.                |
| `CANCELLED` | Admin cancelled before or during the live window.        |

- Admin may create, schedule, and cancel auctions.
- A cancelled auction notifies all previous bidders (email flow pending v2).

## Winner & Payment Flow

When an auction ends:

1. The highest bidder is declared the winner.
2. A **Shopify Draft Order** is created automatically (via `AuctionScheduler`). The Draft Order embeds our internal `auction_order_id` in its custom attributes so the `orders/paid` webhook can find the order directly.
3. The winner receives an email with the Shopify invoice link (checkout URL).
4. The winner has **24 hours** (configurable via `PAYMENT_DUE_HOURS`) to complete checkout via the Shopify invoice.
5. Once the Shopify order is paid, Shopify fires an `orders/paid` webhook.
6. The backend marks the `auction_order` status as `PAID`.

**Stripe is not used to charge auction winners in v1.** The saved SetupIntent (card on file) is for bidder qualification only; auto-charge via Stripe is deferred to v2.

## Order Status Lifecycle

| Status           | Description                                                     |
|------------------|-----------------------------------------------------------------|
| `PENDING_PAYMENT`| Checkout created; winner has not yet paid.                      |
| `PAID`           | Shopify confirmed payment via `orders/paid` webhook.            |
| `EXPIRED`        | Payment deadline passed; winner did not complete checkout.      |
| `FAILED`         | Checkout creation failed (Shopify API error at auction close).  |
| `CANCELLED`      | Reserved for future admin cancellation (unused in v1).          |

### Allowed transitions

| From              | To       | Trigger              | Notes                               |
|-------------------|----------|----------------------|-------------------------------------|
| `PENDING_PAYMENT` | `PAID`   | Shopify webhook      | Normal path                         |
| `PENDING_PAYMENT` | `EXPIRED`| Expiry scheduler     | Payment deadline exceeded           |
| `EXPIRED`         | `PAID`   | Shopify webhook      | **Late payment — see below**        |
| `PAID`            | —        | (no transition)      | Terminal; idempotent on re-delivery |
| `FAILED`          | —        | (no transition)      | Terminal; no automatic recovery     |

### EXPIRED → PAID (late payment)

**Decision:** If a winner pays after the expiry deadline, we still accept the payment and mark the order `PAID`.

**Rationale:** The shirt is made-to-order; if the winner paid (even late), we have a real Shopify order and the money. Refusing to acknowledge a real payment creates more operational problems than accepting it. The `expired_at` timestamp is preserved on the row so admins can see the payment arrived late and decide what to do (notify winner, proceed with fulfillment, flag for review).

Automatic second-chance offers to the next-highest bidder are out of scope for v1.

## Payment Deadline

- Default: **24 hours** after auction end.
- Configured via `PAYMENT_DUE_HOURS` environment variable.
- A scheduler runs every 5 minutes and marks `PENDING_PAYMENT` orders as `EXPIRED` when `payment_due_at` has passed.
- Expired orders are visible to admins (status + expiry timestamp in the order modal).

---

## Local Mock-Mode End-to-End Test Flow

The following sequence exercises the full auction lifecycle locally without Stripe or Shopify credentials.

### Prerequisites

```bash
cd infra && docker compose up -d    # PostgreSQL + Mailpit
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

### 1. Register a buyer

```
POST /api/v1/auth/register
{ "email": "buyer@test.local", "password": "Password123!" }
```

Check Mailpit at [http://localhost:8025](http://localhost:8025) for the verification email.

### 2. Verify email

```
POST /api/v1/auth/verify-email
{ "token": "<token-from-email>" }
```

### 3. Login and get JWT

```
POST /api/v1/auth/login
{ "email": "buyer@test.local", "password": "Password123!" }
```

Save the `token` from the response.

### 4. Set shirt size

```
PATCH /api/v1/users/me
Authorization: Bearer <token>
{ "shirtSize": "L" }
```

### 5. Activate mock payment method

The mock Stripe provider accepts a simplified payload:

```
POST /api/v1/account/payment-method/setup-intent
Authorization: Bearer <token>
```

Then simulate the webhook that the Stripe mock processes:

```
POST /api/v1/webhooks/stripe
Content-Type: application/json
{
  "type": "setup_intent.succeeded",
  "data": { "object": { "customer": "mock_cus_test", "payment_method": "mock_pm_test" } }
}
```

The user's `paymentMethodReady` flag is now `true`.

### 6. Create an auction as admin

Use the admin user created by `DataInitializer` (see logs on startup, or register and promote via DB). 

Via the admin UI at [http://localhost:5173/admin/auctions](http://localhost:5173/admin/auctions), create a new auction with `startsAt` in the past and `endsAt` a few minutes from now.

Or via API:

```
POST /api/v1/admin/auctions
Authorization: Bearer <admin-token>
{
  "title": "Test Shirt Drop",
  "startingBid": 50.00,
  "startsAt": "<now - 1 min>",
  "endsAt": "<now + 2 min>"
}
```

### 7. Place a bid

```
POST /api/v1/auctions/test-shirt-drop/bids
Authorization: Bearer <buyer-token>
{ "amount": 75.00 }
```

### 8. Let the scheduler close the auction

The `AuctionScheduler` runs every 30 seconds. Once `endsAt` passes, it:
- Transitions the auction to `ENDED`
- Creates a `PENDING_PAYMENT` `AuctionOrder` in the DB
- (Mock) generates a fake checkout URL like `http://localhost:8080/mock-checkout/MOCK-...`
- Sends a winner notification email (visible in Mailpit)

Check the order in the admin UI: **Admin → Auctions → [ended auction] → Order**.

Verify:
- Status: `PENDING_PAYMENT`
- Provider: `MOCK (mock)`
- Checkout URL: present
- Payment Due: ~24 hours from auction end

### 9. Simulate Shopify orders/paid webhook (mock mode)

Because `SHOPIFY_WEBHOOK_SECRET` is blank locally, signature verification is skipped.

Get the `auction_order_id` from the admin order modal (or from the DB). Then:

```bash
ORDER_ID="<uuid from admin UI>"
AUCTION_ID="<uuid of the auction>"

curl -X POST http://localhost:8080/api/v1/webhooks/shopify \
  -H "Content-Type: application/json" \
  -H "X-Shopify-Topic: orders/paid" \
  -H "X-Shopify-Webhook-Id: $(uuidgen)" \
  -d "{
    \"id\": 12345,
    \"financial_status\": \"paid\",
    \"note_attributes\": [
      {\"name\": \"auction_order_id\", \"value\": \"$ORDER_ID\"},
      {\"name\": \"auction_id\",       \"value\": \"$AUCTION_ID\"},
      {\"name\": \"source\",           \"value\": \"aetherterra_auction\"}
    ]
  }"
```

Expected response: `{"status": "ok"}`

### 10. Verify PAID status

Refresh the admin order modal. The order should now show:
- Status: `PAID`
- Paid At: timestamp

### 11. Test payment deadline expiry (optional)

Create another auction that ends, then update the order's `payment_due_at` to the past directly in the DB:

```sql
UPDATE auction_orders SET payment_due_at = NOW() - interval '1 hour'
WHERE status = 'PENDING_PAYMENT';
```

Wait up to 5 minutes for the expiry scheduler, or restart the backend (scheduler runs at startup + 60s delay). The order will transition to `EXPIRED`.

To test late payment (EXPIRED → PAID), send the `orders/paid` webhook after expiry — the order should transition to `PAID` and `expired_at` should be preserved.
