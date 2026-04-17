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
| POST   | `/auth/login`              | Public   | Authenticate, receive JWT (v2)     |
| POST   | `/auth/verify-email`       | Public   | Confirm email via token            |
| POST   | `/auth/logout`             | Bearer   | Invalidate session                 |

## Users

| Method | Path                       | Auth     | Description                        |
|--------|----------------------------|----------|------------------------------------|
| GET    | `/users/me`                | Bearer   | Get current user profile           |
| PATCH  | `/users/me`                | Bearer   | Update profile (size, etc.)        |
| POST   | `/users/me/payment-method` | Bearer   | Save Stripe payment method (v2)    |

## Auctions

| Method | Path                       | Auth     | Description                        |
|--------|----------------------------|----------|------------------------------------|
| GET    | `/auctions`                | Public   | List live/upcoming auctions        |
| GET    | `/auctions/:slug`          | Public   | Auction detail                     |

## Bids

| Method | Path                       | Auth     | Description                        |
|--------|----------------------------|----------|------------------------------------|
| POST   | `/auctions/:slug/bids`     | Bearer   | Place a bid (enforces all pre-bid rules) |
| GET    | `/auctions/:slug/bids`     | Public   | Bid history for an auction         |

## Admin

| Method | Path                       | Auth     | Description                        |
|--------|----------------------------|----------|------------------------------------|
| GET    | `/admin/auctions`          | Admin    | List all auctions (all statuses)   |
| POST   | `/admin/auctions`          | Admin    | Create a new auction               |
| PATCH  | `/admin/auctions/:id`      | Admin    | Update auction (title, times, etc.)|
| POST   | `/admin/auctions/:id/cancel` | Admin  | Cancel auction                     |
| GET    | `/admin/users`             | Admin    | List all users                     |

---

## Notes

- `Bearer` = valid JWT required (v2; session-based in v1 if simpler)
- `Admin` = Bearer + ADMIN role
- Stripe and Shopify webhook endpoints to be added in v2 under `/webhooks/`
