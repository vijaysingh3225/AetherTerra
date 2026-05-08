# API Outline — Aether Terra

Base path: `/api/v1`

All responses follow the envelope:
```json
{ "data": <payload>, "message": null }
```
Errors return `{ "data": null, "message": "description" }` with an appropriate HTTP status.

---

## Auth

| Method | Path                       | Auth     | Description                        |
|--------|----------------------------|----------|------------------------------------|
| POST   | `/auth/register`           | Public   | Register a new user                |
| POST   | `/auth/login`              | Public   | Authenticate, receive JWT          |
| POST   | `/auth/verify-email`       | Public   | Confirm email via token            |
| POST   | `/auth/logout`             | Bearer   | Invalidate session                 |

## Users

| Method | Path                                   | Auth   | Description                             |
|--------|----------------------------------------|--------|-----------------------------------------|
| GET    | `/users/me`                            | Bearer | Get current user profile                |
| PATCH  | `/users/me`                            | Bearer | Update profile (size, etc.)             |
| POST   | `/users/me/payment-method/setup-intent`| Bearer | Create Stripe SetupIntent (card qualification) |
| GET    | `/users/me/payment-method/status`      | Bearer | Check payment method qualification      |

## Auctions

| Method | Path                       | Auth     | Description                        |
|--------|----------------------------|----------|------------------------------------|
| GET    | `/auctions`                | Public   | List live/upcoming auctions        |
| GET    | `/auctions/:slug`          | Public   | Auction detail                     |

## Bids

| Method | Path                       | Auth     | Description                                  |
|--------|----------------------------|----------|----------------------------------------------|
| POST   | `/auctions/:slug/bids`     | Bearer   | Place a bid (enforces all pre-bid rules)     |
| GET    | `/auctions/:slug/bids`     | Public   | Bid history for an auction                   |

## Webhooks

| Method | Path                   | Auth     | Description                                                |
|--------|------------------------|----------|------------------------------------------------------------|
| POST   | `/webhooks/stripe`     | Public*  | Stripe webhook receiver (setup_intent.succeeded)           |
| POST   | `/webhooks/shopify`    | Public*  | Shopify webhook receiver (orders/paid)                     |

*These endpoints are public but authenticated via provider-specific HMAC signature headers:
- Stripe: `Stripe-Signature` verified against `STRIPE_WEBHOOK_SECRET`
- Shopify: `X-Shopify-Hmac-Sha256` verified against `SHOPIFY_WEBHOOK_SECRET`

Shopify webhooks must be registered in the Shopify admin for the `orders/paid` topic,
pointing at `https://your-domain.com/api/v1/webhooks/shopify`.

## Admin

| Method | Path                       | Auth  | Description                            |
|--------|----------------------------|-------|----------------------------------------|
| GET    | `/admin/dashboard`         | Admin | Dashboard stats (users, auctions, etc.)|
| GET    | `/admin/auctions`          | Admin | List all auctions (all statuses)       |
| POST   | `/admin/auctions`          | Admin | Create a new auction                   |
| PATCH  | `/admin/auctions/:id`      | Admin | Update auction (title, times, status)  |
| DELETE | `/admin/auctions/:id`      | Admin | Delete scheduled auction (no bids)     |
| GET    | `/admin/auctions/:id/order`| Admin | Get winner order for a specific auction|
| GET    | `/admin/users`             | Admin | List all users                         |
| DELETE | `/admin/users/:id`         | Admin | Delete a user (non-admins only)        |

### AuctionOrderDto (GET /admin/auctions/:id/order)

```json
{
  "id": "uuid",
  "auctionId": "uuid",
  "userId": "uuid",
  "amount": 150.00,
  "currency": "USD",
  "shirtSize": "L",
  "provider": "SHOPIFY",
  "mockProvider": false,
  "providerOrderId": "gid://shopify/DraftOrder/12345",
  "checkoutUrl": "https://...",
  "status": "PENDING_PAYMENT",
  "paymentDueAt": "2026-05-08T12:00:00Z",
  "paidAt": null,
  "expiredAt": null,
  "createdAt": "2026-05-07T12:00:00Z",
  "updatedAt": "2026-05-07T12:00:00Z"
}
```

---

## Notes

- `Bearer` = valid JWT required
- `Admin` = Bearer + ADMIN role
- Shopify webhook idempotency is tracked via `X-Shopify-Webhook-Id`; Shopify retries are silently deduplicated
