# Aether Terra

Custom auction platform for **1-of-1 made-to-order t-shirts**. Every shirt is unique — crafted only after the auction winner confirms payment. Built with a custom auction engine, React frontend, and Spring Boot backend.

---

## Monorepo Structure

```
AetherTerra/
├── frontend/      # React 18 + TypeScript + Vite + TanStack Query + Tailwind CSS
├── backend/       # Spring Boot 3.4 + Java 21 + Maven + PostgreSQL + Flyway
├── infra/         # docker-compose (Postgres + Mailpit), .env.example
├── docs/          # Architecture, auction rules, PRD, API outline
└── scripts/       # Helper scripts
```

---

## Prerequisites

| Tool        | Version  | Install                                        |
|-------------|----------|------------------------------------------------|
| Java        | 21+      | [sdkman.io](https://sdkman.io) or OS package   |
| Maven       | 3.9+     | Bundled via `./mvnw` wrapper (see below)       |
| Node.js     | 20+      | [nodejs.org](https://nodejs.org)               |
| Docker      | 24+      | [docs.docker.com/get-docker](https://docs.docker.com/get-docker/) |
| Docker Compose | v2+   | Included with Docker Desktop                   |

---

## 1 — Start PostgreSQL Locally

```bash
cd infra
docker compose up -d
```

This starts:
- **PostgreSQL 16** on `localhost:5433` (db: `aetherterra`, user: `aetherterra`, password: `aetherterra`)
- **Mailpit** SMTP stub on port `1025`, web UI at [http://localhost:8025](http://localhost:8025)

Stop everything:
```bash
docker compose down
```

---

## 2 — Run the Backend

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

> **Windows (cmd/PowerShell):** use `mvnw.cmd` instead of `./mvnw`

On first run, Flyway applies all migrations and creates the full schema. Verify:
```
GET http://localhost:8080/actuator/health
GET http://localhost:8080/api/v1/auctions
```

---

## 3 — Run the Frontend

```bash
cd frontend
npm install
npm run dev
```

App runs at [http://localhost:5173](http://localhost:5173). API calls to `/api/*` are proxied to `localhost:8080` automatically.

---

## Environment Variables

Copy the example and fill in values:

```bash
cp infra/.env.example .env
```

Never commit `.env`. See `infra/.env.example` for all expected variables and their defaults.

---

## Payment Flow (v1)

```
1. Bidder qualifies  → Stripe SetupIntent saves card on file (bidder qualification, no charge)
2. Auction ends      → Backend picks winner, creates Shopify Draft Order, emails invoice link
3. Winner pays       → Completes checkout via Shopify invoice (credit card, buy-now-pay-later, etc.)
4. Shopify fires     → orders/paid webhook → POST /api/v1/webhooks/shopify
                        → AuctionOrder: PENDING_PAYMENT → PAID
5. Expiry guard      → Scheduler marks PENDING_PAYMENT orders EXPIRED after payment deadline (default 24 h)
```

**Why Stripe auto-charge is deferred:** Stripe is used only for bidder qualification in v1 (SetupIntent — saves payment method without charging). Auto-charging the winner via the saved method is a v2 feature. Shopify is the source of truth for payment, tax, shipping, and fulfillment.

---

## Stripe Webhook Setup (local testing)

**Mock mode (default):** Leave `STRIPE_SECRET_KEY` blank. The Account page shows a mock button that simulates the full SetupIntent → webhook flow locally. No Stripe account needed.

**Real test-mode:**

1. Copy your test-mode keys from [dashboard.stripe.com/apikeys](https://dashboard.stripe.com/apikeys).
2. Set in your `.env`:
   ```
   STRIPE_SECRET_KEY=sk_test_...
   STRIPE_PUBLISHABLE_KEY=pk_test_...
   VITE_STRIPE_PUBLISHABLE_KEY=pk_test_...   (same value)
   ```
3. Install the [Stripe CLI](https://docs.stripe.com/stripe-cli) and forward webhooks:
   ```bash
   stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe
   ```
   The CLI prints a `whsec_...` signing secret — set it as `STRIPE_WEBHOOK_SECRET`.
4. Restart the backend. Startup log should read `PaymentQualificationProvider: Stripe (real mode)`.
5. Open the Account page at [http://localhost:5173/account](http://localhost:5173/account), click **Add a card**.
6. Use test card: **4242 4242 4242 4242**, any future expiry, any CVC, any postal code.
7. After confirming, the Stripe CLI receives `setup_intent.succeeded` → backend marks your account payment-method-ready.

---

## Shopify Webhook Setup

1. In Shopify admin: **Settings → Notifications → Webhooks → Create webhook**
   - Event: **Order payment** (`orders/paid`)
   - URL: `https://your-domain.com/api/v1/webhooks/shopify`
   - Format: JSON
2. Copy the **Signing Secret** displayed after creation.
3. Set `SHOPIFY_WEBHOOK_SECRET=<signing-secret>` in your environment.

**Local testing:** Shopify cannot reach `localhost`. Use a tunnel:
```bash
ngrok http 8080
# Then register: https://<ngrok-id>.ngrok.io/api/v1/webhooks/shopify
```

Or send a manual test request with the correct `X-Shopify-Hmac-Sha256` header (HMAC-SHA256 of the body using your secret, Base64-encoded).

**Mock mode:** When `SHOPIFY_WEBHOOK_SECRET` is blank (the default locally), signature verification is skipped and a warning is logged. All other webhook logic runs normally, which is convenient for local testing. Do not leave the secret blank in production.

---

## Current Status

| Area                    | Status                                                    |
|-------------------------|-----------------------------------------------------------|
| Monorepo layout         | Done                                                      |
| Auth (register, login)  | Done — JWT, email verification                            |
| Auction engine          | Done — SCHEDULED → LIVE → ENDED lifecycle, scheduler      |
| Bidding                 | Done — pre-bid requirements enforced                      |
| Admin CRUD              | Done — auctions, users, order lookup                      |
| Stripe (bidder qualification) | Done — SetupIntent, webhook, mock mode             |
| Shopify (winner checkout) | Done — Draft Order creation, invoice email, mock mode   |
| Shopify webhook (payment confirmation) | Done — orders/paid, HMAC, idempotency   |
| Payment deadline expiry | Done — scheduler marks PENDING_PAYMENT → EXPIRED          |
| Stripe winner auto-charge | Deferred to v2                                          |

---

## Documentation

| Document                                | Description                            |
|-----------------------------------------|----------------------------------------|
| [docs/architecture.md](docs/architecture.md)           | System design, payment flow, Shopify webhook setup |
| [docs/auction-rules.md](docs/auction-rules.md)         | Business rules, order status lifecycle |
| [docs/product-requirements.md](docs/product-requirements.md) | Feature scope and user flows      |
| [docs/api-outline.md](docs/api-outline.md)             | API endpoint reference                 |

---

## Why Maven over Gradle?

Maven was chosen because Spring Boot's parent BOM makes dependency management nearly zero-config, the XML is verbose but unambiguous, and the ecosystem of plugins (Flyway, Spring Boot plugin) is well-tested against it. Gradle is faster for large multi-module builds, but this project doesn't need that tradeoff yet.

---

## Manual Steps After Cloning

Generate the Maven wrapper once:

```bash
cd backend
mvn wrapper:wrapper
```

Then commit the generated `mvnw`, `mvnw.cmd`, and `.mvn/` directory.
