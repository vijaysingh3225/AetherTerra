# Architecture — Aether Terra

## Overview

Aether Terra is a monorepo containing a React/TypeScript frontend and a Spring Boot backend, backed by PostgreSQL. The auction engine is custom-built and owned by this backend. Shopify and Stripe are external integrations added in a later phase.

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
| Security       | Spring Security (JWT in v2) |
| Observability  | Spring Actuator          |

### Package Structure

```
com.aetherterra
├── auth/       # Registration, login, JWT, email verification
├── users/      # User entity, profile, shirt size
├── auctions/   # Auction lifecycle, scheduling, listing
├── bids/       # Bid placement, validation, history
├── admin/      # Admin-only management endpoints
└── common/     # Shared config, security, response wrappers
```

## Database

PostgreSQL 16 managed by Flyway migrations. Schema lives in `backend/src/main/resources/db/migration/`.

## External Integrations (Planned)

| Integration | Purpose                            | Phase |
|-------------|------------------------------------|-------|
| Stripe      | Save payment methods, charge winner | v2   |
| Shopify     | Post-auction order + fulfillment    | v2   |
| Mailpit     | Local SMTP stub for email testing   | Now  |

## Local Development

See the root [README](../README.md) for setup instructions.
