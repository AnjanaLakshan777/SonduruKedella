# Development Workflow

## Repository Layout
- backend/: Spring Boot Maven application.
- frontend/: existing React application.
- docs/: persistent project documentation.
- compose.yaml: local Docker Compose configuration.
- .env: local, untracked environment configuration.
- .env.example: tracked template without secrets.

## Environment Variables
Read .env.example for required variable names. Keep actual development credentials in the root .env file and never commit it. Claude Code is authorized to edit .env when required for implementation, but must preserve valid existing credentials unless a change is necessary. Do not print secrets, include them in logs, or copy them into documentation. Ask before rotating credentials that may already be in use.

Optional, with defaults, so not in .env.example: `AGREEMENTS_STORAGE_DIR` (default `data/agreements`, relative to the working directory; compose sets `/app/data/agreements`), `AGREEMENTS_MAX_SIZE` (default `20MB`) and `AGREEMENTS_MAX_REQUEST_SIZE` (default `21MB`). Raise the last two together.

## Docker Commands
Validate configuration:
  docker compose config --quiet

Start services:
  docker compose up -d --build

Check services:
  docker compose ps

View service logs:
  docker compose logs --tail=100

Follow backend logs:
  docker compose logs -f backend

Stop services without deleting persistent volumes:
  docker compose down

Do not use docker compose down -v as routine cleanup. It deletes both persistent volumes: `postgres_data` (the database) and `agreement_files` (uploaded agreement PDFs). Never delete persistent data without explicit approval and a confirmed backup. Back the two volumes up together, because agreement rows point at files in `agreement_files`.

The backend image must be rebuilt (`docker compose up -d --build backend`) before the agreement endpoints work in Docker. The image creates the writable `/app/data/agreements` directory that the volume mounts over.

## Backend Commands (Windows PowerShell)
From the backend directory:
  .\mvnw.cmd test
  .\mvnw.cmd clean package

Use commands appropriate to the actual project and verify the available Maven goals and configuration before assuming they work.

## Frontend Commands
Inspect frontend/package.json and use the package manager matching the existing lockfile. Do not replace the lockfile or install a second package manager without approval.

From `frontend/`: `npm run build` (typecheck + production build), `npm run typecheck`, and `npm test` (Vitest, `vitest run`; unit tests next to the code as `*.test.ts`). The tests need no backend or database.

## Before Every Change
1. Inspect the relevant source and Git status.
2. Understand existing behavior.
3. Implement the smallest complete change.
4. Add or update tests.
5. Run relevant tests and builds.
6. Report what was verified and what was not.
7. Update implementation status and relevant documentation.

## Secrets and Destructive Operations
Do not commit .env or print its values. Do not run destructive database operations, remove volumes, reset migrations, or delete user files without explicit authorization.

## API and Database Changes
Keep API contracts compatible with the existing frontend or explicitly document required frontend changes. Add new Flyway migrations for schema changes. Never claim a migration or endpoint works unless it has been tested.

Pending migrations are applied by Flyway when the backend starts. `V2__suppliers_active.sql`, `V3__inventory_ledger_additions.sql` and `V4__inventory_fifo_cost_layers.sql` have not been applied to the development database yet; they run, in order, on the next backend start or rebuild. All three are additive (new columns with defaults or NULLs, new constraints, indexes, tables and triggers), so existing data is kept. V4 also inserts one FIFO layer per material that holds stock, and refuses to run, applying nothing, if any material's stock ledger does not reconcile (see DATABASE.md, "FIFO migration of existing data"). Before applying any migration to the development database, test it on a throwaway PostgreSQL 17 database, both fresh and with representative data.
## Backend Tests
Run the tests with JDK 25 against a throwaway PostgreSQL 17 container, never the development database. Point `DB_HOST`, `DB_PORT`, `POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD` at the container; environment variables override `.env`. Some integration tests commit for real (`integration/database/*`) and delete only the rows they created. `RevenueConcurrencyIntegrationTest`, `ExpenseConcurrencyIntegrationTest`, `InventoryConcurrencyIntegrationTest` and `PayrollConcurrencyIntegrationTest` briefly disable `trg_client_payments_guard`, `trg_expense_payments_guard`, `trg_stock_movements_immutable`, `trg_stock_cost_allocations_immutable` and the payroll guards (`trg_payroll_payments_guard`, `trg_payroll_lines_immutable`, `trg_payroll_line_costs_immutable`, `trg_advance_recoveries_immutable`, `trg_salary_advances_guard`) during their own cleanup, because those rows cannot otherwise be deleted. That is another reason these tests must never run against a database whose data matters.
