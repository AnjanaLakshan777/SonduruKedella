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
- [x] Owner sign-off on the schema (2026-10-24), with these clarifications: construction-only overtime and stock issues are enforced in the service; payroll weeks run Monday to Sunday; corrections need an audit trail before they can be edited.
- [x] V1 applied to the development database (2026-10-24). V1 is now immutable.

## Planned Frontend Changes
Required by the owner's decisions. Not yet made. See docs/DATABASE.md, "Frontend alignment".
- [ ] Inventory: "Return from project" action linked to the original issue, with returned movements in the history.
- [ ] Inventory: remove the low-stock KPI, the stock-level filter, the "Reorder soon" banner, the reorder level column and field, and `Material.reorderLevel`.
- [ ] Inventory: show the weighted average unit cost from the API.
- [ ] Payroll: paid employee-weeks read-only in attendance, overtime and adjustments, plus a "Record correction" action on paid lines.
- [ ] Settings: revenue categories shown read-only.

## Backend Foundation
- [x] Modular package structure established (2026-10-24).
- [x] Global exception handling and validation configured (2026-10-24): `common.exception.GlobalExceptionHandler` returns RFC 9457 problem details. Status codes: 400 for validation errors (field list included), 404 not found, 409 conflict or stale version, 422 business rule.
- [ ] API response and error conventions established.
- [ ] Spring Security configuration implemented and tested.
- [ ] Automated test strategy established.

## Business Modules
- [x] Settings (2026-10-24): company profile, commission policy, wage settings, expense categories, units, payment methods, and read-only revenue categories.
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

### 2026-10-24: V1 applied to the development database
- Target verified before applying:
  - Compose service `postgres` (postgres:17-alpine, volume `sonduru-kedella_postgres_data`), database `sonduru_kedella`, profile `dev`. The running container matched the Compose configuration.
  - The database held only an empty `flyway_schema_history`: no other tables, views, sequences, functions, types or extensions.
  - `.env` is ignored and untracked, and nothing was staged.
- Applied with `docker compose up -d --build --no-deps backend`. Only the backend was rebuilt and restarted. The database and its volume were not touched.
- Verified:
  - Flyway: "Successfully applied 1 migration … now at version v1". The history row shows v1 with `success = true`.
  - 38 tables (including the Flyway history table) and 15 triggers.
  - Seed data in place, with zero business rows.
  - The backend started, `/actuator/health` returns `UP`, and `/api/v1/*` returns 401.
- Docs: recorded the owner's clarifications (service-layer construction-only rules, Monday-to-Sunday weeks, the correction audit requirement) and that V1 is immutable.

### 2026-10-24: Backend package structure
- Created 141 `package-info.java` files (package declaration only) under `com.sondurukedella`. There are no classes and no business logic.
  - Business modules: settings, clients, projects, agreements, revenue, commissions, expenses, suppliers, inventory, employees, payroll. Each has `controller`, `service`, `repository`, `entity`, `dto`, `mapper` and `validation`.
  - `reports` has `controller`, `service`, `repository` and `dto`, because reporting is read-only. `activity` has every layer except `validation`.
  - `common` (`exception`, `web`, `audit`, `money`, `validation`), `config` (`persistence`, `web`) and `security` (`config`, `user`, `auth`).
  - Tests: `controller`, `service` and `repository` for each module, plus `common`, `security`, `integration/database`, `integration/api` and `support`.
- Verified: the backend built in Docker on JDK 25, and `./mvnw test` passed against a disposable database (1 test, 0 failures). The development database was not touched and is still at v1.
- No migrations, configuration, dependencies, frontend or environment files were changed.

### 2026-10-24: Settings module
- Endpoints (all under `/api/v1/settings`):
  - `GET` overview
  - `GET/PUT /company`, `/commission-policy`, `/wages`
  - `GET /revenue-categories` (read-only)
  - `GET/POST /expense-categories` (filter with `?group=`) and `DELETE /expense-categories/{id}`
  - `GET/POST /units` and `DELETE /units/{id}`
  - `GET/POST /payment-methods` and `DELETE /payment-methods/{id}`
- Rules:
  - Updates require the current `version`; a stale version returns 409.
  - Validation follows the V1 constraints and the Settings form: pool between 0 and 100 exclusive; daily wage greater than 0; hours greater than 0 and at most 12; multiplier greater than 0; payday Friday or Saturday; credit days at least 0; company name required.
  - List names are unique ignoring case. Removing an entry deactivates it, and re-adding the name reactivates it.
  - Locked expense categories cannot be removed.
  - At least one unit and one payment method must stay active.
  - Revenue categories have no write endpoints.
- Shared code added:
  - `common.audit.AuditableEntity`
  - `common.exception` (`GlobalExceptionHandler`, `NotFoundException`, `ConflictException`, `BusinessRuleException`, `InvalidRequestException`)
- Authorization: Spring Boot's default security is unchanged. Every endpoint requires an authenticated user, and writes require a CSRF token. No roles are defined in the docs, and no security configuration was added.
- Tests: 37 passed on JDK 25 in Docker against a disposable PostgreSQL 17 database.
  - Unit tests: 18
  - Web-layer tests: 10, covering authorization, CSRF, validation and read-only revenue categories
  - API integration tests: 8, running against the real schema, each in a transaction that is rolled back
  - The existing `contextLoads` test
  - The disposable database still held its seed values after the run, which confirms the rollback. It was then dropped. The development database was not touched.
- Limitations:
  - `created_by` stays null until application users and login exist.
  - The JSON enum values (`FRIDAY`, `DESIGN`, `COMPANY`) are the API constants, so the frontend maps them to its labels when it is integrated.
  - The running backend container has not been rebuilt, so these endpoints are not yet live in the local stack.
