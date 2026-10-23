# Implementation Status

Last updated: 2026-10-24

This document must be updated as implementation progresses. Mark work complete only after inspecting or testing the actual result.

## Existing Project
- [x] React frontend exists.
- [x] Spring Boot Maven project has been created.
- [x] Verify all selected Spring Boot dependencies in backend/pom.xml.
- [x] Inspect existing frontend build and package configuration.

## Repository and Infrastructure
- [x] Root environment configuration finalized.
- [x] .gitignore and .dockerignore verified.
- [x] Docker Compose configuration validated.
- [x] PostgreSQL container starts and passes health check.
- [x] Spring Boot connects to PostgreSQL using environment variables.
- [x] Flyway runs a verified initial migration: V1 was verified on disposable databases and has not yet been applied to the development database.
- [x] Backend and frontend Docker builds succeed.

## Database Design
- [x] Proposed schema documented in docs/DATABASE.md (v2, aligned with the existing frontend).
- [x] Retention money and the separate machinery rental module removed from scope.
- [x] Owner decisions recorded (2026-10-24): material returns, low-stock removal, weighted average costing, freezing paid payroll weeks, fixed revenue categories.
- [x] Payroll freeze rule finalized (2026-10-24): it applies to each employee and week at that employee's first payment. Corrections go in `payroll_corrections`.
- [x] Initial Flyway migration `V1__initial_schema.sql` written and verified against PostgreSQL 17.11.
- [ ] Owner sign-off on the final schema in docs/DATABASE.md.
- [ ] V1 applied to the development database (rebuild and restart the backend).

## Planned Frontend Changes
Required by the owner's decisions. Not yet made. See docs/DATABASE.md, "Frontend alignment".
- [ ] Inventory: "Return from project" action linked to the original issue, with returned movements in the history.
- [ ] Inventory: remove the low-stock KPI, the stock-level filter, the "Reorder soon" banner, the reorder level column and field, and `Material.reorderLevel`.
- [ ] Inventory: show the weighted average unit cost from the API.
- [ ] Payroll: paid employee-weeks read-only in attendance, overtime and adjustments, plus a "Record correction" action on paid lines.
- [ ] Settings: revenue categories shown read-only.

## Backend Foundation
- [ ] Modular package structure established.
- [ ] Global exception handling and validation configured.
- [ ] API response and error conventions established.
- [ ] Spring Security configuration implemented and tested.
- [ ] Automated test strategy established.

## Business Modules
- [ ] Clients.
- [ ] Projects and agreement metadata.
- [ ] Contract payment schedules and receipts.
- [ ] Revenue and expenses.
- [ ] Commission management.
- [ ] Suppliers and payables.
- [ ] Inventory and stock movements.
- [ ] Employee attendance, overtime, advances, and payroll.
- [ ] Financial reports.
- [ ] Private agreement document storage.

## Completion Log
For each completed task, record the date, files or migrations changed, verification performed, and known limitations. Do not mark a task complete solely because code was generated.

### 2026-10-23: Repository foundation and Docker setup
- Files: `.env.example`, `.env` (untracked), `.gitignore`, `.dockerignore`, `compose.yaml`, `Dockerfile.backend`, `Dockerfile.frontend`, `backend/src/main/resources/application.properties`, `backend/src/main/resources/db/migration/.gitkeep`, docs.
- Verified:
  - `docker compose config --quiet` passes.
  - `.env` is git-ignored and only `.env.example` is tracked.
  - Both images build.
  - PostgreSQL 17 is healthy.
  - The backend starts on Java 25, connects to `postgres:5432` through environment variables, and Flyway runs with zero migrations.
  - `/actuator/health` returns `UP`.
  - The Vite dev server responds.
  - `npm run build` passes on the host.
- Limitations:
  - Backend tests were not run: the host has JDK 21, and `contextLoads` needs a database.
  - The backend container has no health check.

### 2026-10-23: Database schema design (v1)
- Files: `docs/DATABASE.md`.
- Verified that no migrations exist and that the active database contains only `flyway_schema_history`.
- Design only. It is awaiting owner review. No migrations or entities have been written.

### 2026-10-24: Database schema design (v2)
- Files: `docs/DATABASE.md`, `docs/IMPLEMENTATION_STATUS.md`.
- Compared the schema with the frontend types, the store actions, the forms (projects, payments, revenue, expenses, supplier invoices, inventory, labour, commissions, settings) and the calculations in `lib/finance.ts`.
- Changes:
  - Removed retention money and the separate machinery rental tables.
  - Machinery rental is a manual expense, as it is in the UI.
  - Added variation installments, payroll adjustments, the activity log, and lookup tables for the editable Settings lists.
  - Commission policy checks are now warnings the owner can override.
- Frontend unchanged. No backend code, migrations or endpoints.

### 2026-10-24: Owner decisions applied to schema v2
- Files: `docs/DATABASE.md`, `docs/IMPLEMENTATION_STATUS.md`.
- Changes:
  - Returns must reference the original issue of the same material and project. This is enforced by a composite foreign key.
  - Weighted average costing, with running `quantity_after` and `value_after` on each stock movement.
  - `reorder_level` removed.
  - Paid employee-weeks are frozen, with a new `payroll_corrections` table. Corrections never touch advance recoveries.
  - Revenue categories fixed, with no table.
  - Added a frontend type to database mapping.
- Design only. Frontend, backend, migrations and environment files unchanged.
### 2026-10-24: Initial Flyway migration (V1)
- Files: `backend/src/main/resources/db/migration/V1__initial_schema.sql`, `docs/DATABASE.md`, `docs/IMPLEMENTATION_STATUS.md`. No backend configuration changes were needed: Flyway was already set to `classpath:db/migration`.
- Schema review corrections in DATABASE.md:
  - finalized payroll freeze rule (each employee and week)
  - commission amount stored on confirmation
  - `active` flag on expense categories
  - financial year stored as a start month
  - copied guard columns for composite foreign keys
  - adjustments at zero stock use `materials.unit_cost`
- Verified:
  - The migration applied on disposable PostgreSQL 17.11 databases: 37 tables and 15 triggers.
  - 92 of 92 constraint and trigger checks passed, each one confirming the expected constraint fired. They covered duplicates, payment immutability, project-type rules, system-generated expense categories, invoice and stock links, returns, payroll freeze, and corrections.
  - Spring Boot (Flyway + Hibernate `validate`) applied V1 and started successfully.
  - `./mvnw test` ran in Docker on JDK 25: 1 test, 0 failures, BUILD SUCCESS.
  - The disposable databases and test images were removed afterwards.
  - The development database was left unchanged (zero migrations applied).
- Limitations:
  - No JPA entities, services or endpoints exist yet.
  - The service-level rules (balance caps, weighted-average posting, schedule totals) are not implemented yet.
  - The constraint checks were run ad hoc and are not yet part of the automated test suite.
