# Aether Terra

Custom auction platform for **1-of-1 made-to-order t-shirts**. Every shirt is unique — crafted only after the auction winner is confirmed. Built with a custom auction engine, React frontend, and Spring Boot backend.

---

## Monorepo Structure

```
AetherTerra/
├── frontend/      # React 18 + TypeScript + Vite + TanStack Query + Tailwind CSS
├── backend/       # Spring Boot 3.4 + Java 21 + Maven + PostgreSQL + Flyway
├── infra/         # docker-compose (Postgres + Mailpit), .env.example
├── docs/          # Architecture, auction rules, PRD, API outline
└── scripts/       # Helper scripts (empty for now)
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
- **PostgreSQL 16** on `localhost:5432` (db: `aetherterra`, user: `aetherterra`, password: `aetherterra`)
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

On first run, Flyway will apply `V1__init_schema.sql` and create the `users`, `auctions`, and `bids` tables.

Verify it's running:
```
GET http://localhost:8080/actuator/health
GET http://localhost:8080/api/v1/auctions
```

> **Note:** Maven wrapper (`mvnw`) is not included in this initial scaffold. See [Manual Steps](#manual-steps) below.

---

## 3 — Run the Frontend

```bash
cd frontend
npm install    # already done if you followed setup
npm run dev
```

App runs at [http://localhost:5173](http://localhost:5173). API calls to `/api/*` are proxied to `localhost:8080` automatically.

---

## Environment Variables

Copy the example and fill in values:

```bash
cp infra/.env.example .env
```

Never commit `.env`. See `infra/.env.example` for all expected variables.

---

## Current Status

| Area            | Status                                         |
|-----------------|------------------------------------------------|
| Monorepo layout | Done                                           |
| Frontend routes | Scaffolded — placeholder pages                 |
| Backend API     | `/api/v1/auctions` returns mock data           |
| Database schema | V1 migration (users, auctions, bids)           |
| Auth            | Security config in place — implementation TBD |
| Stripe          | Placeholder only                               |
| Shopify         | Placeholder only                               |

---

## Why Maven over Gradle?

Maven was chosen because Spring Boot's parent BOM makes dependency management nearly zero-config, the XML is verbose but unambiguous, and the ecosystem of plugins (Flyway, Spring Boot plugin) is well-tested against it. Gradle is faster for large multi-module builds, but this project doesn't need that tradeoff yet.

---

## Manual Steps After Cloning

The Maven wrapper is not yet committed. Generate it once:

```bash
cd backend
mvn wrapper:wrapper
```

Then commit the generated `mvnw`, `mvnw.cmd`, and `.mvn/` directory.

If Maven isn't installed locally, [download it](https://maven.apache.org/download.cgi) or install via SDKMAN: `sdk install maven`.

---

## Next Recommended Steps

1. **Generate and commit the Maven wrapper** (`mvn wrapper:wrapper` inside `backend/`)
2. **Implement user registration + email verification** (`backend/src/main/java/com/aetherterra/auth/`)
3. **Add JWT-based authentication** (filter, token service, login endpoint)
4. **Wire up Auction JPA entity** and replace the mock data in `AuctionController`
5. **Build bid placement endpoint** with pre-bid requirement checks
6. **Connect frontend login/register forms** to real API endpoints
7. **Add Stripe payment method collection** to the account page (Stripe.js)
8. **Set up GitHub Actions CI** (build + test on push)
9. **Plan Shopify integration** for post-auction fulfillment

---

## Documentation

| Document                                | Description                            |
|-----------------------------------------|----------------------------------------|
| [docs/architecture.md](docs/architecture.md)           | System design, tech choices            |
| [docs/auction-rules.md](docs/auction-rules.md)         | Business rules for bidding             |
| [docs/product-requirements.md](docs/product-requirements.md) | Feature scope and user flows      |
| [docs/api-outline.md](docs/api-outline.md)             | API endpoint reference                 |
