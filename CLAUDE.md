# CLAUDE.md — Sonduru Kedella

Project-based financial management web application for Sonduru Kedella, a home design and
construction company in Anuradhapura, Sri Lanka. Monorepo: `backend/` (Java 25, Spring Boot 4.1,
Maven) and `frontend/` (React 18 + TypeScript + Vite, Tailwind v4, shadcn/ui), run locally with
Docker Compose.

## Start of every session

1. Read [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md): business requirements and scope.
2. Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): modular monolith, module boundaries, API conventions.
3. Read [docs/DATABASE.md](docs/DATABASE.md) **before any database-related change**.
4. Read [docs/DEVELOPMENT_WORKFLOW.md](docs/DEVELOPMENT_WORKFLOW.md) **before any build, environment or Docker change**.
5. Read [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md) to see what actually exists.
6. Inspect the relevant source code before changing it.

## Source of truth

- **Source code is authoritative** for what is implemented. **Documentation** records intended
  design and verified decisions.
- If code and docs conflict, point out the discrepancy and resolve it deliberately. Never invent
  missing implementation to make the docs "true", and never describe planned work as done.
- Never claim a feature is implemented or tested unless you verified it (built it, ran the test, ran the app).

## Rules

- **Preserve the existing React UI** and its design decisions (layout, shadcn theme in
  `frontend/src/index.css`, components, sample data). Wire it to the API; don't redesign it.
- **Modular monolith.** One Spring Boot application, organised by business module. Don't split it
  into services or add infrastructure (Redis, brokers, gateways, proxies) unless the owner explicitly approves.
- **Database:** PostgreSQL + Spring Data JPA + Flyway. Every schema change is a new versioned Flyway
  migration in `backend/src/main/resources/db/migration`. Never edit an applied migration. Never let
  Hibernate generate DDL (`ddl-auto=validate`).
- **Money:** `BigDecimal` in Java and `NUMERIC(p,s)` in PostgreSQL (see DATABASE.md). Never `double`/`float`
  for amounts, rates or quantities used in money calculations. Use explicit scale and `RoundingMode`.
- **No double counting:** each financial fact is stored once.
  - Revenue is recognised from billed installments, not from payments. A payment never creates revenue.
  - A payment is counted once, against one obligation.
  - Stock purchases are not project costs. Issuing stock to a project is the project cost.
  - Gross wages are the labour cost. Advances are deducted from pay and are not a second cost.
  - Delay deductions stay separate from retention money and never change the contract value.
- **Tests:** add or update automated tests for every business rule you add or change.
- **Docs:** when meaningful work is completed, update `docs/IMPLEMENTATION_STATUS.md` (and other docs
  if a decision changed).
- **API:** REST under `/api/v1`.

## Secrets and destructive operations

- `.env` (repo root) holds local development configuration and is git-ignored. You **may** create or
  edit it when the project needs it, but:
  - never print, log, or copy its values into docs, code, commits or responses;
  - preserve existing credentials. **Ask before rotating** credentials that may already be in use;
  - keep `.env.example` in sync (placeholders only, never real values).
- Never bake secrets into Dockerfiles or images, and never expose secrets via `VITE_*` variables
  (they are shipped to the browser).
- Ask the owner before destructive operations: deleting data or volumes, `docker compose down -v`,
  dropping or resetting the database, deleting files, force-pushes, or rewriting history.
- Follow any explicit owner instruction about credentials, destructive operations and database resets.
