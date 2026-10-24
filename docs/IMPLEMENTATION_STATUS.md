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
- [x] Clients (2026-10-24): create, list (search, type filter, paging), get and update. Financial summaries per client are not included yet; they depend on projects and revenue.
- [x] Projects (2026-10-24): create (with an inline client, the payment schedule and delay terms, all in one transaction), list, get and status changes. Agreement upload and metadata are not implemented.
- [ ] Contract payment schedules and receipts. Partial (2026-10-24): only the initial schedule and delay terms, created with the project. Reading, editing and billing the schedule, variations, receipts, write-offs and applying deductions are not implemented.
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
  - The API differs from the frontend `Settings` type in these ways, which the frontend must map when it is integrated:
    - Enum values are API constants (`FRIDAY`, `DESIGN`, `COMPANY`).
    - `supplierCreditDays` and the financial-year start (`financialYearStartMonth`, a number) are inside `company`.
    - Revenue categories are `{code, name}` objects.
    - Expense categories are a flat list with `group`, `id` and flags, not a map of name lists.
    - Units and payment methods are `{id, name, active, sortOrder}` objects, so entries can be removed by id.
  - The running backend container has not been rebuilt, so these endpoints are not yet live in the local stack.

### 2026-10-24: Clients module
- Endpoints (all under `/api/v1/clients`):
  - `GET` list: `q` searches name, city and phone (case-insensitive, `%` and `_` matched literally); `kind` filters by `INDIVIDUAL` or `BUSINESS`; `page` (zero-based), `size` (default 20, at most 100) and `sort` (`name`, `city`, `createdAt` or `id`; default `name`, with `id` as a tie-breaker). Returns `{content, page, size, totalElements, totalPages}`.
  - `GET /{id}`, `POST` (201 with a `Location` header) and `PUT /{id}`.
- Rules:
  - Validation follows V1 and the client dialog: name required (at most 200); phone required and, once trimmed, 9 to 15 digits, spaces or "+"; email optional and in the form something@something.something (at most 150); address at most 300; city at most 100; `kind` required.
  - Text is trimmed and blank optional fields are stored as null.
  - Updates require the current `version`; a stale version returns 409 and leaves the client unchanged.
  - No duplicate check: V1 defines no unique identifier for clients.
  - No delete or archive: V1 has no active flag and the UI has no such action.
  - No activity log entry: `activity_log` is the project feed and has no client event kind.
- Shared code added: `common.web.PageResponse`, the paging contract for list endpoints.
- Authorization: unchanged from Settings. Every endpoint requires an authenticated user and writes require a CSRF token. No roles are defined in the docs.
- Tests: 88 passed on JDK 25 (local, `C:\Tools\Java\jdk-25.0.4.1`) against a throwaway PostgreSQL 17 container, 51 of them new:
  - Unit tests: 9 for the service, 13 for the phone validator
  - Web-layer tests: 15, covering authentication, CSRF, validation messages, unknown types, paging parameters, 404, 409 and the absence of delete
  - Repository tests: 7 against the real V1 schema (search, type filter, literal wildcards, audit defaults, database constraints)
  - API integration tests: 7, each in a transaction that is rolled back
  - The throwaway database held no clients after the run, which confirms the rollback. The container was then removed. The development database was not touched.
- Limitations:
  - `created_by` stays null until application users and login exist.
  - The API differs from the frontend `Client` type in these ways, which the frontend must map when it is integrated:
    - `id` is a number, not a string.
    - `kind` is an API constant (`INDIVIDUAL`, `BUSINESS`), not `Individual` or `Business`.
    - `createdAt` is an ISO-8601 timestamp, not a `YYYY-MM-DD` date. The frontend can use its date part for "Client since".
    - `address` and `city` can be null; the frontend type has them as required strings.
    - Responses add `updatedAt` and `version`, and `PUT` requires `version`.
    - The directory's contract value, received, outstanding, overdue and project codes are not returned. They need the projects and revenue modules.
  - The running backend container has not been rebuilt, so these endpoints are not yet live in the local stack.

### 2026-10-24: Clients review fix
- A whitespace-only email was rejected with 400, because `@Email` fails on `"   "`. It is now stored as null. The new `clients.validation.OptionalEmail` constraint checks the trimmed value, so a padded valid email is accepted and saved trimmed, and an invalid non-empty email is still rejected.
- The frontend dialog itself rejects a whitespace-only email, so the API is more lenient than the UI here. That is deliberate: a blank optional field is not an error.
- Docs: DATABASE.md now lists the tables that have JPA entities (Settings and Clients). ARCHITECTURE.md lists the `clients/validation` test package.
- Tests: 103 passed on JDK 25 against a throwaway PostgreSQL 17 container: 13 new email validator tests and 2 new API integration tests that check the stored column. The container was removed afterwards. The development database was not touched.

### 2026-10-24: Projects module, with minimal Revenue and Activity support
- Endpoints (all under `/api/v1/projects`):
  - `GET` list:
    - `q` searches name, code, location and client name.
    - Filters: `type`, `status`, `clientId`, and the inclusive date ranges `startFrom`/`startTo` and `dueFrom`/`dueTo`.
    - Paging: `page`, `size` (default 10, at most 100) and `sort` (`code`, `name`, `startDate`, `expectedCompletion`, `contractValue`, `createdAt` or `id`). The default is `code` descending, with `id` as a tie-breaker.
    - Returns `PageResponse`.
  - `GET /{id}`, `POST` (201 with a `Location` header) and `PUT /{id}/status` (`{status, version}`).
  - No DELETE and no general edit.
- Owner decisions applied (2026-10-24):
  - The code year comes from `startDate`.
  - Creation is atomic.
  - Any status can change to any other, and `actual_completion` is kept when a project is reopened or completed again.
  - "Today" is the Asia/Colombo date, from an injectable `Clock`.
- Creation: `ProjectCreator` runs in one transaction, in this order:
  1. Validate the request.
  2. Create the inline client, or check the existing one, through `ClientService`.
  3. Take the per-year advisory lock and generate the next code.
  4. Insert the project.
  5. `InstallmentScheduleService` validates and inserts the schedule.
  6. `DelayTermsService` validates and inserts the delay terms, if any.
  7. `ActivityService` records "Project created in <status>."

  `ProjectService.create` retries the whole transaction, up to 3 times, only when the error is a `uq_projects_code` violation. If the retries run out it returns 409.
- Rules:
  - Name is required (at most 200). Location is at most 200. Exactly one of `clientId` and `newClient` is required.
  - Contract value: more than 0 for design and construction, at least 0 for consultation, at most 2 decimal places.
  - Expected completion must be after the start date.
  - A new project can start only as Planning, In Progress or On Hold.
  - Schedule:
    - It must add up exactly to the contract value.
    - Every row needs a description (at most 300), a due date and an amount above 0 with at most 2 decimal places.
    - There is no schedule when the fee is 0.
  - Delay terms:
    - Construction projects only; on other types they are rejected, not ignored.
    - A deadline and a rule are required, plus the value that rule uses, which must be above 0. Values for the other rules are dropped.
    - Grace days are at least 0, and the maximum deduction, if given, is above 0.
- Status changes use optimistic locking (409 on a stale version). Each change writes a `STATUS` entry to `activity_log` in the same transaction. Setting the current status again changes nothing.
- Shared code added:
  - `config.ClockConfig`
  - `common.money.Money`
  - `ClientService.exists`, `findNames` and `findIdsByName`, which Projects uses instead of the clients repository
- Tests: 230 passed on JDK 25 against a throwaway PostgreSQL 17 container, 127 of them new (the last run was after the review fixes below).
  - Unit tests:
    - all 20 status transitions
    - completion dates with a fixed clock in Colombo
    - the retry and its limit
    - code generation by start year
    - contract, date, initial-status, schedule and delay-term rules
  - Web-layer tests: authentication, CSRF, field errors (including `schedule[0].amount`, `newClient.phone` and `delayTerms.*`), enums, malformed filters, 404, 409, and the absence of DELETE and general edit.
  - Repository tests against V1: search, filters, date ranges, stable paging, the advisory lock, and V1 constraints (duplicate code, contract value, dates, client FK, delay terms on a design project, rule values, zero installments). They also check that a real Postgres duplicate-code error is recognised as a collision.
  - API integration tests, each rolled back: the full create, numbering per year, consultation with no fee, client-name search, the status sequence with its activity messages, stale versions, and rejected inputs.
  - Tests that commit for real:
    - a failure at the schedule, the delay terms or the activity step leaves no client, project, installment, delay-term or activity rows;
    - a status change rolls back with its failed activity entry;
    - 8 concurrent creates get `PRJ-2092-001` to `-008`.

    Every table was back to 0 rows afterwards. The container was removed, and the development database was not touched.
- Limitations and frontend differences:
  - The project response has no received, costs or outstanding figures, and those columns can't be sorted on; they need Revenue and Expenses. Zeros are not returned in their place.
  - Not implemented: reading the schedule or delay terms, agreement upload and download, the activity feed, and commission, payment and expense data for the project tabs.
  - Contract differences:
    - Enums are API constants (`IN_PROGRESS`, `DESIGN`, `PER_DAY`), not display labels or `per_day`.
    - Ids are numbers.
    - `createdAt` is a timestamp.
    - `location`, `description` and `notes` can be null.
    - The create body uses `schedule` and `delayTerms` (no `enabled` flag), and `newClient` uses the full client rules.
    - The server assigns the code, so the page's `PRJ-2026-NNN` preview can differ.
    - The date presets become `startFrom`/`startTo` and `dueFrom`/`dueTo`.
  - `created_by` stays null until application users exist.
  - The running backend container has not been rebuilt.
- Review fixes (2026-10-24, before commit):
  - Code numbering ignores suffixes over 9 digits and works for any year length. Previously one oversized hand-entered code would have failed every create for that year.
  - The contract value and delay-term values are rejected, not rounded, when they have too many decimals, even when the service is called from code: 2 places for money, 4 for the weekly percentage, which is also capped at what `NUMERIC(7,4)` holds.
  - Removed three unused repository finders. Their presence suggested read-back features that don't exist.
  - A new test confirms the schedule, delay-term and activity services refuse to run outside a transaction.
