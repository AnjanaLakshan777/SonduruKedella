# Implementation Status

Last updated: 2026-10-25

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
- [x] Owner decisions recorded (2026-10-24): material returns, low-stock removal, weighted average costing (changed by the owner to FIFO the same day, see "Inventory costing changed to FIFO"), freezing paid payroll weeks, fixed revenue categories.
- [x] Payroll freeze rule finalized (2026-10-24): it applies to each employee and week at that employee's first payment. Corrections go in `payroll_corrections`.
- [x] Initial Flyway migration `V1__initial_schema.sql` written and verified against PostgreSQL 17.11.
- [x] Owner sign-off on the schema (2026-10-24), with these clarifications: construction-only overtime and stock issues are enforced in the service; payroll weeks run Monday to Sunday; corrections need an audit trail before they can be edited.
- [x] V1 applied to the development database (2026-10-24). V1 is now immutable.

## Planned Frontend Changes
Required by the owner's decisions. Unticked items are not yet made. See docs/DATABASE.md, "Frontend alignment".
- [x] Inventory: "Return from project" action linked to the original issue, with returned movements in the history (2026-10-24, local store only; see "Inventory frontend aligned with FIFO").
- [x] Inventory: remove the low-stock KPI, the stock-level filter, the "Reorder soon" banner, the reorder level column and field, and `Material.reorderLevel` (2026-10-30, phase 3d).
- [x] Inventory (FIFO) (2026-10-24, local store only): the FIFO stock value, the `q × unitCost` preview replaced, a unit cost field for positive adjustments. It still has to be wired to the API.
- [x] Payroll: paid employee-weeks read-only in attendance, overtime and adjustments, plus a "Record correction" action on paid lines (2026-10-30, phase 3a).
- [x] Settings: revenue categories shown read-only (2026-10-30, phase 3d).

## Backend Foundation
- [x] Modular package structure established (2026-10-24).
- [x] Global exception handling and validation configured (2026-10-24): `common.exception.GlobalExceptionHandler` returns RFC 9457 problem details. Status codes: 400 for validation errors (field list included), 404 not found, 409 conflict or stale version, 422 business rule.
- [ ] API response and error conventions established.
- [x] Spring Security configuration implemented and tested (2026-10-25): session sign-in, the two roles `SUPER_ADMIN` and `ADMIN`, user administration, first Super Admin bootstrap, security audit trail. See the completion log, "Authentication, users and roles".
- [ ] Automated test strategy established.

## Business Modules
- [x] Settings (2026-10-24): company profile, commission policy, wage settings, expense categories, units, payment methods, and read-only revenue categories.
- [x] Clients (2026-10-24): create, list (search, type filter, paging), get and update. Financial summaries per client are not included yet; they depend on projects and revenue.
- [x] Projects (2026-10-24): create (with an inline client, the payment schedule and delay terms, all in one transaction), list, get, status changes, and reading the saved schedule and delay terms. The agreement PDF is handled by the Agreements module.
- [x] Contract payment schedules and receipts (2026-10-24): schedule created with the project and readable; billing; client receipts (idempotent, voidable); write-offs (create only); delay deductions (apply and reverse); receivables per project and a company-wide summary. Since 2026-10-26 also: revising the unbilled schedule, variations, write-off reversal (V7).
- [x] Revenue and expenses (2026-10-26): project revenue recognised at billing, manual project and company expenses with their payments, and other income with its receipts. See the completion log, "Other income".
- [x] Commission management (2026-10-25, backend): per-project recipient arrangements (draft, confirm with stored amount, policy warning and owner exception), voids with a reason, payments (partial, idempotent, voidable), the directory, summary, by-recipient and payment views. The frontend still runs on its local store. See the completion log, "Commissions".
- [x] Suppliers and payables (2026-10-25): the supplier directory, the supplier link on expenses, supplier invoices (STOCK into FIFO stock, PROJECT as project material cost) and supplier payments (partial, idempotent, voidable). See the completion log, "Supplier invoices and payables".
- [x] Inventory and stock movements (2026-10-24): the materials catalogue; receipts, issues to construction projects, returns against the original issue and adjustments, costed FIFO from persistent cost layers with issue allocations (changed from the weighted average the same day, migration V4); balances, valuation, movement history and project material usage. Receipts from supplier invoices are not built yet.
- [x] Employee attendance, overtime, advances, and payroll (2026-10-24, backend): employees, the attendance register, overtime and approval, advances, weekly payroll worked out live, payments that freeze the week, closing a zero week, corrections, history, period summaries and project labour cost. The frontend still runs on its local store.
- [x] Financial reports (2026-10-25, backend): dashboard, financial summary, project financials and cost breakdown, receivables aging, inventory valuation and payroll weeks, all read-only. Supplier payables wait for supplier invoices. See the completion log, "Reports and dashboard".
- [x] Private agreement document storage (2026-10-24): upload or replace, metadata and download of one current PDF per project. Removing (retiring) the current agreement since 2026-10-26.

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

### 2026-10-24: Project schedule and delay-term read APIs
- Endpoints (both `GET`, both need a signed-in user):
  - `/api/v1/projects/{id}/schedule`: a list of `{id, projectId, description, dueDate, amount, kind, billed, billedOn, version}`, ordered by due date and then id.
    - `billed` is `billedOn != null`; V1 has no separate billing flag.
    - The list is empty for a project with no fee (a consultation).
  - `/api/v1/projects/{id}/delay-terms`:
    - 200 with `{id, projectId, enabled, contractDeadline, rule, ratePerDay, percentPerWeek, fixedAmount, graceDays, maxDeduction, notes, actualCompletion, appliedAmount, appliedOn, appliedToInstallmentId, version}`.
    - 204 No Content when the project has no terms. That is always the case for design and consultation projects, because the table only accepts construction. Nothing is invented for them.
  - Both return 404 problem details for an unknown project.
  - These are separate endpoints rather than fields on `GET /{id}`, because `ProjectResponse` is also the list row.
- Flow: `ProjectController` → `ProjectService` (checks the project exists) → `InstallmentScheduleService.findSchedule` / `DelayTermsService.findTerms` (read-only, mapped by `revenue.mapper.RevenueMapper`). Revenue's write methods keep `MANDATORY` propagation, and Revenue still never imports `projects`.
- Tests: 242 passed on JDK 25 against a throwaway PostgreSQL 17 container, 12 of them new.
  - Unit tests: the 404 is raised before Revenue is called; `billed` is derived from `billedOn`; a project without terms reads as empty.
  - Web-layer tests: JSON shape and ISO dates, 200/204/404, 401 for anonymous users, and 405 for POST/PUT/DELETE on both paths.
  - API integration tests against V1:
    - a three-row schedule read back in due-date order, with one row billed directly in the database to show `billed` and `billedOn` are read, not assumed;
    - per-week-percentage terms with the stray per-day value dropped and the applied fields null;
    - 204 for construction without terms and for design and consultation projects;
    - an empty schedule for a no-fee consultation;
    - 404 for a missing project;
    - repeated reads leave versions, `updated_at`, billing and activity counts unchanged;
    - 401 for anonymous users.

  The throwaway database was back to 0 rows and was removed afterwards. The development database was not touched.
- Limitations and frontend differences:
  - Paid, remaining and overdue amounts and the installment status (Paid, Overdue, Partially Paid) are not returned. They need client payments and write-offs.
  - `rule` uses API constants (`PER_DAY`, `PERCENT_PER_WEEK`, `FIXED_AMOUNT`) where the frontend uses `per_day` and so on.
  - Ids are numbers.
  - The frontend has to treat 204 from `/delay-terms` as "no terms" (its `Project.delay` is undefined).
  - `actualCompletion` and the `applied*` fields stay null until applying deductions is built.
  - Still not implemented: editing or billing the schedule, variations, receipts, write-offs, applying or reversing deductions, agreement upload, the activity feed, and the financial summary columns.

### 2026-10-24: Agreements module (agreement PDF upload and download)
- Endpoints (all need a signed-in user; `PUT` also needs a CSRF token):
  - `GET /api/v1/projects/{projectId}/agreement`: current metadata, `{id, projectId, fileName, contentType, size, sha256, uploadedAt, version}`. `fileName`, `size` and `uploadedAt` match the frontend `AgreementDoc`. The storage key and paths are never returned.
  - `PUT /api/v1/projects/{projectId}/agreement`: `multipart/form-data` with the PDF in part `file`.
    - Returns 201 for the first agreement and 200 for a replacement, with the metadata.
    - 400: empty file, wrong extension, a content type other than `application/pdf`, a body that doesn't start with `%PDF-`, or no `file` part.
    - 413: over the size limit, whether caught by the servlet multipart limit or while streaming.
    - 409: a concurrent replacement (`@Version` on the old row) or concurrent first uploads (the one-current-agreement index).
    - 500, with no paths in the response: a storage failure.
  - `GET /api/v1/projects/{projectId}/agreement/download`: the PDF as `application/pdf`, `Content-Disposition: attachment`, with the cleaned original name (RFC 5987 `filename*`).
  - 404 problem details: "Project not found." for an unknown project, and "No agreement has been uploaded for this project." when there is none.
  - No POST or DELETE (405).
- Storage, configured in `application.properties`:
  - `agreements.storage-dir` (`AGREEMENTS_STORAGE_DIR`, default `data/agreements`)
  - `agreements.max-size` (`AGREEMENTS_MAX_SIZE`, default 20MB)
  - `spring.servlet.multipart.max-file-size` and `max-request-size` (default 21MB)

  Spring's built-in limit of 1MB was below the UI's 20MB, so these had to be added. Docker:
  - `compose.yaml` sets `AGREEMENTS_STORAGE_DIR=/app/data/agreements` on a new `agreement_files` named volume.
  - `Dockerfile.backend` creates that directory owned by the non-root `app` user. Without it, the container could not write files.
  - `backend/data/` is git-ignored for local runs.
  - The backend image has not been rebuilt, so the endpoints are not yet live in the local stack.
- Replacement: no migration. V1 already has `removed_at` and the partial unique index. The old row gets `removed_at` and the new row is inserted in one transaction. Both the old row and its file are kept.
- Integrity:
  - The file is stored before the metadata. If the transaction fails, the new file is deleted and the old agreement keeps working.
  - Uploads are streamed to disk, never held in memory. Writing stops as soon as the limit is passed.
  - Client file names are only metadata. Directory parts, control characters and quotes are removed.
  - Each upload writes a `DOCUMENT` activity entry ("Agreement uploaded: …" or "Agreement replaced: …") in the same transaction.
- Tests: 307 passed on JDK 25 against a throwaway PostgreSQL 17 container, 65 of them new.
  - File name and type validation: 22.
  - File store, using a temp directory: 16. Real size and hash; `%PDF-` signature; empty files; an endless stream cut off at the limit; traversal and invalid keys refused; an unwritable directory; no temp files left behind.
  - Service: 8. A database failure deletes the new file; a storage failure never reaches the database; an unknown project or wrong type is rejected before anything is stored.
  - Web layer: 9. 401 for anonymous users, 403 without CSRF, 201/200, a missing part, 413 from both layers, no path leaks in 500 responses, attachment headers with a UTF-8 name, 405.
  - Integration tests that commit for real: 10.
    - upload, read and download, plus replacement with the old row and file kept;
    - each invalid file rejected with no rows and no files left;
    - 404 cases, and no cross-project access;
    - traversal and header-injection names;
    - a database failure during replacement leaves the old agreement and file intact and removes the new file;
    - a storage failure changes no metadata;
    - stored files are not reachable at any static path.

    Afterwards the database was back to 0 rows, and the temp storage and container were removed. The development database and its volumes were not touched.
- Limitations:
  - Removing an agreement (the dropzone's remove action) is not implemented.
  - There is no API for superseded agreements, and their files are kept indefinitely.
  - A crash between storing the file and committing can leave an orphaned file; it is logged, and there is no cleanup job.
  - The frontend dropzone accepts a file that ends in `.pdf` even when the browser reports another content type; the API rejects that.
  - `id` is a number, and `uploadedAt` is a timestamp rather than a date.

### 2026-10-24: Revenue: billing, client receipts, write-offs and delay deductions
- No migration: V1 already had every column needed (`billed_on`, `client_payments` with `request_id` and the void columns, `receivable_write_offs`, and the applied columns of `delay_deduction_terms`).
- Endpoints. All need a signed-in user, and every `POST` needs a CSRF token. Paths are under `/api/v1/projects/{projectId}` unless shown in full.

  | Method | Path | Request | Response |
  | --- | --- | --- | --- |
  | GET | `/receivables` | — | `{projectId, installments[], totals{billed, deductions, revenueRecognised, received, writtenOff, outstanding, overdue, unbilled}}` |
  | GET | `/installments/{installmentId}` | — | `{installment, receipts[], writeOffs[]}` |
  | POST | `/installments/{installmentId}/bill` | `{billedOn?, version}` | installment receivable |
  | POST | `/installments/{installmentId}/receipts` | `{requestId, paymentDate, amount, paymentMethodId, reference?, notes?}` | 201 receipt, or 200 for a resent `requestId` |
  | GET | `/receipts` | — | all of the project's receipts, voided ones flagged |
  | GET | `/receipts/{receiptId}` | — | receipt |
  | POST | `/receipts/{receiptId}/void` | `{reason, version}` | receipt |
  | POST | `/installments/{installmentId}/write-offs` | `{date, amount, reason}` | 201 write-off |
  | GET | `/delay-terms/calculation?completionDate=` | — | `{completionDate, contractDeadline, daysLate, graceDays, chargeableDays, calculatedAmount, maxDeduction, capped, finalAmount}` |
  | POST | `/delay-terms/deduction` | `{installmentId, completionDate?, amount?, notes?, version}` | delay terms |
  | POST | `/delay-terms/deduction/reverse` | `{reason?, version}` | delay terms |
  | GET | `/api/v1/receivables/summary` | — | `{outstanding, overdue, partiallyPaid, writtenOff, openInstallments, overdueInstallments, partiallyPaidInstallments}` |

  - An installment receivable is `{id, projectId, description, dueDate, amount, kind, billed, billedOn, deduction, net, received, writtenOff, outstanding, status, daysOverdue, version}`.
  - A receipt is `{id, installmentId, projectId, paymentDate, amount, paymentMethodId, method, reference, notes, requestId, voided, voidedAt, voidReason, createdAt, version}`.
  - Errors:
    - 404: unknown project, installment, receipt or delay terms, and an installment or receipt of another project.
    - 400: invalid input or a business rule. That covers a receipt or write-off on an unbilled installment, more than the outstanding, an inactive payment method, a reason that is too short, a deduction that isn't late after grace, an amount above the agreed terms or the payable, a deduction on an unbilled installment, and no completion date.
    - 409: a stale version, billing twice, applying a deduction twice, reversing when none is applied, voiding twice, or a `requestId` reused for a different payment.
  - No PUT or DELETE on financial records.
- Calculations: as documented in DATABASE.md, "Calculations". Outstanding = `max(0, amount − deduction − received − written off)` for billed installments. Statuses are `UNBILLED`, `BILLED`, `PARTIALLY_PAID`, `OVERDUE` and `PAID`, using the Colombo date.
- Integrity:
  - Every money change locks the installment row before reading its balance, then writes the record and its activity entry in one transaction.
  - Billing logs `NOTE`, receipts and voids log `PAYMENT`, write-offs `WRITEOFF`, and deductions `DEDUCTION`.
  - Billing never changes the contract value. Deductions never change the contract value or the project's dates.
- Tests: 350 passed on JDK 25 against a throwaway PostgreSQL 17 container, 43 of them new.
  - Calculator unit tests: 14. Every deduction rule, grace days, full weeks only, `HALF_UP` rounding, the cap, the docs' worked example, all five statuses, write-offs as settlement, and the zero floor.
  - Web-layer tests: 8. 401 for anonymous users, 403 without CSRF on every write, field validation, 201 then 200 on a resent receipt, 404/400/409 mapping, and no PUT or DELETE.
  - API integration tests: 16, each rolled back, with a fixed clock at 19:00 UTC, which is already the next day in Colombo.
    - billing once, a stale version, and the Colombo billing date and overdue status;
    - partial and full receipts, overpayment, a resent receipt and a reused `requestId`, an inactive payment method;
    - voiding a receipt;
    - write-offs and their rules;
    - the deduction calculation for every rule, the project's completion date as the default, applying within the terms and the payable, reversing;
    - design projects with no terms;
    - totals and the summary;
    - reads that change nothing;
    - 404 cases, including cross-project ids.
  - Concurrency tests that commit for real: 5. Six simultaneous billings succeed once. Eight receipts of 20,000 against 100,000 give exactly five accepted. Six copies of one submission record one receipt. A receipt and a write-off racing for the last 40,000 cannot both succeed. A failed activity entry rolls the receipt or write-off back.
  - Afterwards all affected tables were back to 0 rows and the guard trigger was re-enabled. The container was removed, and the development database was not touched.
- Limitations and frontend differences:
  - Write-offs cannot be reversed; V1 has no columns for it (see DATABASE.md).
  - Variations and editing a schedule are not implemented.
  - The cross-project Receivables list (one row per installment with client and project names) is not an endpoint yet; only the summary KPIs and per-project receivables are.
  - Status names differ from the UI labels (mapping in DATABASE.md).
  - The UI must send a `requestId` (UUID) per receipt submission and the `version` it last read.
  - `rule` values are `PER_DAY` and so on, not `per_day`.
  - The UI lets the owner raise the deduction above the calculated amount; the API does not.
  - The UI's default target installment may be unbilled; the API requires a billed one.
  - The UI keeps `actualCompletion` on the terms after a reversal; the API clears it, as the V1 check requires.
  - No date rules are enforced on payment, write-off or billing dates; none are documented.
  - `created_by` stays null until application users exist.

### 2026-10-24: Expenses (manual project and company expenses, and their payments)
- No migration: V1's `expenses` and `expense_payments` already had every column, constraint and trigger needed.
- Endpoints (all under `/api/v1/expenses`; signed-in users only, and every `POST`/`PUT` needs a CSRF token):

  | Method | Path | Request | Response |
  | --- | --- | --- | --- |
  | GET | `/` | `q, scope, projectId, categoryId, group, status, from, to, page, size (default 20, max 100), sort (expenseDate, amount, dueDate, createdAt, id)` | `PageResponse` of expenses, newest first |
  | GET | `/summary` | same filters | `{count, total, projectTotal, companyTotal, paid, outstanding, overdueOutstanding}` |
  | GET | `/{id}` | — | `{expense, payments[]}` |
  | POST | `/` | `{date, scope, projectId?, categoryId, description, payee, amount?, dueDate?, invoiceNo?, notes?, quantity?, unitId?, unitPrice?, machineType?, usageFrom?, usageTo?, billingPeriod?, initialPayment?}` | 201 expense |
  | PUT | `/{id}` | the same fields without `initialPayment`, plus `version` | expense |
  | GET | `/{id}/payments` | — | payments, voided ones flagged |
  | POST | `/{id}/payments` | `{requestId, paymentDate, amount, paymentMethodId, reference?, notes?}` | 201 payment, or 200 for a resent `requestId` |
  | POST | `/{id}/payments/{paymentId}/void` | `{reason, version}` | payment |

  - An expense is `{id, date, scope, projectId, group, categoryId, category, description, payee, amount, dueDate, invoiceNo, notes, quantity, unitId, unit, unitPrice, machineType, usageFrom, usageTo, billingPeriod, paid, outstanding, status, createdAt, updatedAt, version}`.
  - A project's expenses are `GET /api/v1/expenses?projectId={id}`; there is no separate project endpoint.
  - Errors:
    - 404: an unknown expense or payment.
    - 400:
      - validation, and an amount ≤ 0 or with more than 2 decimals;
      - a project that doesn't exist or is cancelled;
      - a scope that doesn't match the project;
      - a category that is missing, inactive, from the wrong group or system-generated;
      - a materials line that doesn't add up;
      - a usage period that ends before it starts;
      - an inactive payment method;
      - an overpayment;
      - an amount lowered below what was paid;
      - a bad filter or sort.
    - 409: a stale version, a payment voided twice, or a `requestId` reused for a different payment.
  - No DELETE.
- Business rules: see DATABASE.md, Expenses, "Implemented". In short:
  - only manual costs;
  - Labour and Commission are refused;
  - the category must fit the project type, or `COMPANY`;
  - outstanding = `max(0, amount − active payments)`;
  - statuses `PAID`, `OVERDUE`, `PARTIALLY_PAID` and `UNPAID`, using the Colombo date (the same labels as the UI);
  - payments lock the expense row;
  - each change writes an `EXPENSE` activity entry (with the project id for project expenses, none for company ones).
- Tests: 392 passed on JDK 25 against a throwaway PostgreSQL 17 container, 42 of them new.
  - Unit tests: 11. Every status and the zero floor; the materials amount and its rounding, a mismatch and a partial line; scope and project consistency; the usage period.
  - Web-layer tests: 9. 401 for anonymous users, 403 without CSRF, field errors (including `initialPayment.*`), malformed dates and enums, 201 then 200 on a resent payment, 400/404/409 mapping, filter binding, no DELETE.
  - API integration tests: 19, each rolled back, with a fixed clock at 19:00 UTC (already the next day in Colombo).
    - project and company expenses, including paid in full at creation;
    - the materials amount;
    - Labour refused;
    - the wrong group, and missing or inactive categories (an existing expense keeps its now-inactive category when edited);
    - missing or cancelled projects;
    - invalid dates and amounts;
    - 404 cases;
    - partial and full payments, overpayment, a resent payment, an inactive payment method, voiding (the V1 trigger still blocks deletes);
    - the Colombo overdue status;
    - edit rules, including the version and moving to company level;
    - filters, paging, sorting and the summary;
    - the database refusing a mismatched category;
    - reads that change nothing.
  - Concurrency tests that commit for real: 3. Eight payments of 20,000 against 100,000 give exactly five; six copies of one submission give one payment; a failure after the expense was written rolls back the expense and its first payment.
  - Afterwards every table was back to 0 rows and both payment guard triggers were enabled again. The container was removed, and the development database was not touched.
- Limitations and frontend differences:
  - Supplier link: `supplierId` isn't accepted yet (there is no suppliers module), so `payee` is always required. The UI allows a supplier instead of a payee.
  - Receipt attachments are not uploaded or stored.
  - The UI's free-form `details` map becomes typed fields. `unitId` replaces the unit name.
  - The category is sent by id, not name.
  - Enums are API constants (`PROJECT`, `COMPANY`, `PARTIALLY_PAID`).
  - Every payment needs a `requestId` (UUID) and the `version` of the last read.
  - The Expenses page's ledger also shows supplier invoices, stock issues, payroll and commissions; this API returns manual expenses only, and those other sources arrive with their modules.
  - No rules are enforced on expense, due or payment dates; none are documented.
  - Payments are recorded through `/expenses` only; there are no supplier payables yet.
  - `created_by` stays null until application users exist.

### 2026-10-24: Suppliers (directory and the expense link)
- Migration `V2__suppliers_active.sql` adds `suppliers.active BOOLEAN NOT NULL DEFAULT TRUE`.
  - Why: V1 had no way to retire a supplier without deleting it, and expenses reference suppliers.
  - It is additive only.
  - Verified on a fresh PostgreSQL 17 database, and on a V1 database already holding a supplier with a linked expense: both rows were unchanged, and the supplier was active.
  - Not applied to the development database yet; Flyway runs it on the next backend start.
  - The expense link needed no migration: `expenses.supplier_id` and `ix_expenses_supplier` were already in V1.
- Endpoints (all under `/api/v1/suppliers`; signed-in users only, and every `POST`/`PUT` needs a CSRF token):

  | Method | Path | Request | Response |
  | --- | --- | --- | --- |
  | GET | `/` | `q` (name, contact person, phone, supplies), `active`, `page`, `size` (default 20, max 100), `sort` (name, creditDays, createdAt, id; default name) | `PageResponse` of suppliers |
  | GET | `/{id}` | — | supplier |
  | POST | `/` | `{name, phone, contactPerson?, email?, address?, supplies?, creditDays?}` | 201 supplier, with a `Location` header |
  | PUT | `/{id}` | the same fields, with `creditDays` required, plus `version` | supplier |
  | PUT | `/{id}/status` | `{active, version}` | supplier |

  - A supplier is `{id, name, contactPerson, phone, email, address, supplies, creditDays, active, createdAt, updatedAt, version}`.
  - Errors: 400 for validation or a bad sort or page size, 404 for an unknown supplier, 409 for a stale version.
  - No DELETE (405).
  - A supplier's expenses come from `GET /api/v1/expenses?supplierId={id}`, and `/api/v1/expenses/summary` takes the same filter.
- Rules:
  - Name (at most 200) and phone (at most 30) are required, as in the supplier dialog.
  - Optional: contact person (150), email (150, format checked only when given), address (300), supplies (300), and `creditDays` from 0 to 3650.
  - `creditDays` defaults to Settings > Company `supplierCreditDays`.
  - Text is trimmed, and blanks are stored as null.
  - No uniqueness on name or phone.
- Expense integration:
  - `supplierId` is optional on expense create and edit. Responses include `supplierId` and `supplier` (the name).
  - A newly linked supplier must exist (otherwise 400) and be active (otherwise 400). An expense that already names a supplier stays readable and editable after the supplier is deactivated.
  - `supplierId: null` clears the link.
  - `payee` is still required; the expense dialog sends the supplier's name as the payee.
  - Payments, idempotency, locking and the overpayment check are unchanged.
- Shared code: `OptionalEmail` moved from `clients.validation` to `common.validation`, now that Clients and Suppliers both use it. Its behaviour is unchanged, and the Clients tests still pass.
- Tests: 414 passed on JDK 25 against a throwaway PostgreSQL 17 container, 22 of them new.
  - Web-layer tests: 9. 401 for anonymous users, 403 without CSRF, required fields, lengths, email and credit days, 201 with a `Location` header, the version needed on update and status, 404/409, filter binding, no DELETE.
  - API integration tests: 13, each rolled back.
    - creating with trimming and the company credit default;
    - invalid input saving nothing;
    - 404 cases;
    - optimistic-lock updates;
    - search by name, contact and phone, the active filter, paging and sorting;
    - deactivating and reactivating;
    - expenses linked to a supplier and filtered by it;
    - unknown and inactive suppliers refused for new links;
    - an existing linked expense kept, readable and editable, with its payments intact after deactivation;
    - edits that can't link an unknown or newly inactive supplier;
    - the V1 foreign key refusing a missing supplier;
    - the V2 default.
  - All existing Expense tests (including concurrency) and Client tests still pass.
  - Afterwards all tables were back to 0 rows and both payment guard triggers were enabled. The container was removed, and the development database was not touched.
- Limitations:
  - Supplier invoices, supplier payments, payables and credit-term due dates are not implemented. Those are the supplier invoices part of the Suppliers module.
  - The frontend has no active/inactive UI yet; it would call `PUT /{id}/status` and pass `active=true` for pick lists.
  - Ids are numbers, and `contactPerson`, `address` and `supplies` can be null.
  - Supplier changes write no activity entry, matching Clients; there is no project to attach one to.

### 2026-10-24: Inventory (materials and the stock ledger)

> Superseded in part: costing was changed to FIFO later the same day (see "Inventory costing changed to FIFO" below). The weighted-average costing, `averageUnitCost`, the frozen default cost and the WAC-specific tests described in this entry no longer apply; everything else does.

- Migration `V3__inventory_ledger_additions.sql`. All of it is additive:
  - `materials.active`: materials couldn't be retired without deleting them.
  - `stock_movements.supplier_id`: receipts only. V1 reached a supplier only through supplier invoice lines, which don't exist yet.
  - `stock_movements.request_id`: unique. Without it, a double-submitted receipt or issue would be permanent, since the ledger is append-only.

  It was verified on a fresh database and on a V1+V2 database with representative suppliers, expenses, materials and ledger rows. It is not applied to the development database yet.
- Endpoints. All are under `/api/v1/inventory`, for signed-in users only, and every `POST`/`PUT` needs a CSRF token.

  | Method | Path | Request | Response |
  | --- | --- | --- | --- |
  | GET | `/materials` | `q` (SKU, name, category), `category`, `active`, paging (default 20, max 100), `sort` (sku, name, category, createdAt, id) | `PageResponse` of materials with stock |
  | GET | `/materials/{id}` | — | material |
  | POST | `/materials` | `{sku, name, category, unitId, defaultUnitCost?}` | 201 material |
  | PUT | `/materials/{id}` | the same fields, plus `version` | material |
  | PUT | `/materials/{id}/status` | `{active, version}` | material |
  | GET | `/materials/{id}/movements` | paging | that material's ledger, newest first |
  | GET | `/valuation` | — | `{materials, materialsInStock, stockValue}` |
  | GET | `/movements` | `materialId, type, projectId, supplierId, from, to`, paging, `sort` (movementDate, id) | `PageResponse` of movements, newest first |
  | GET | `/movements/{id}` | — | movement |
  | POST | `/receipts` | `{requestId, materialId, date, quantity, unitCost, supplierId?, reference?, notes?}` | 201 movement, or 200 for a resent `requestId` |
  | POST | `/issues` | `{requestId, materialId, projectId, date, quantity, reference?, notes?}` | 201, or 200 for a resent `requestId` |
  | POST | `/returns` | `{requestId, issueId, materialId?, projectId?, date, quantity, reference?, notes?}` | 201, or 200 for a resent `requestId` |
  | POST | `/adjustments` | `{requestId, materialId, date, quantityChange (signed), reason, reference?}` | 201, or 200 for a resent `requestId` |
  | GET | `/projects/{projectId}/usage` | — | the project's issued, returned and net quantity and cost per material |

  - A material is `{id, sku, name, category, unitId, unit, averageUnitCost, quantityOnHand, stockValue, active, createdAt, updatedAt, version}`.
  - A movement is `{id, materialId, material, date, type, quantityChange, unitCost, totalCost, quantityAfter, valueAfter, projectId, supplierId, supplier, returnedFromMovementId, returnedQuantity, returnableQuantity, reference, notes, requestId, createdAt}`, signed like the ledger.
  - Errors:
    - 400:
      - validation;
      - insufficient stock;
      - an over-return;
      - a return that doesn't match its issue;
      - an inactive material or supplier;
      - an unknown material, project, supplier or issue in the request body;
      - a project that isn't construction or isn't open;
      - an edit of the unit or cost after movements;
      - a bad filter or sort.
    - 404: an unknown material or movement in the URL.
    - 409: a duplicate SKU, a stale version, or a `requestId` reused for something else.
  - No PUT or DELETE on movements, and no DELETE on materials.
- Costing: moving weighted average, exactly as DATABASE.md "Stock costing".
  - Receipt cost is `round(q × price, 2)`.
  - The new average is `(V + cost) / (Q + q)`, stored to 4 decimals and rounded `HALF_UP`.
  - An issue costs `round(q × V / Q, 2)` and fixes that cost on the movement; issuing everything takes exactly `V`.
  - A return comes back at the issue's unit cost; the last return of an issue takes exactly what is left of its cost, so a full return nets to 0.
  - At zero stock the last average is kept.
- Project cost:
  - A project's stock material cost is `Σ issued − Σ returned`, at each movement's own cost (`/projects/{id}/usage`).
  - Buying into stock is not a project cost.
  - Manual `Materials` expenses and `PROJECT` supplier invoices are separate sources and never generated from stock, so nothing is counted twice.
- Tests: 456 passed on JDK 25 against a throwaway PostgreSQL 17 container, 42 of them new.
  - Unit tests: 11.
    - receipt rounding, the moving average over three receipts, keeping the average at zero stock;
    - an issue of everything taking exactly V, partial issue rounding, never exceeding V;
    - returns at the issue cost, with the last one taking the remainder and a full return netting to 0;
    - adjustment rules.
  - Web-layer tests: 7. 401 for anonymous users, 403 without CSRF on every write, field validation, 201 then 200 on a resend, 400/404/409 mapping, no editing or deleting of the ledger.
  - API integration tests: 19, each rolled back.
    - the catalogue: a case-insensitive unique SKU, unit checks, search, filters, paging and sorting, the unit and cost frozen after movements, a stale version;
    - the weighted average over receipts;
    - issue cost fixed even after a dearer receipt, project usage and activity;
    - insufficient stock, zero stock, and a fresh average after running out;
    - issues only to open construction projects;
    - partial, multiple, full and over-returns, and returns that don't match their issue;
    - adjustments;
    - inactive materials;
    - suppliers on receipts, including an inactive supplier with its history kept;
    - resends;
    - history filters;
    - the running balance equalling the plain sums;
    - the ledger being append-only;
    - reads that change nothing;
    - 401 for anonymous users.
  - Concurrency tests that commit for real: 5.
    - Eight issues of 20 against 100 in stock: exactly five.
    - Ten receipts at once: an exact value and average.
    - Six returns of 10 against an issue of 30: exactly three.
    - Six copies of one submission: one movement.
    - A failed issue leaves no movement and an unchanged average.

    Each one checks that every running balance equals the plain sums.
  - All existing Suppliers, Expenses, Clients, Projects, Revenue and Settings tests still pass.
  - Afterwards every table was back to 0 rows and all four ledger and payment triggers were enabled. The container was removed, and the development database was not touched.
- Limitations and frontend differences:
  - Receipts from supplier invoices (`supplier_invoice_line_id`) are not implemented; the column is read-only here.
  - Receipts create no payable.
  - The UI's `Material.reorderLevel` is gone (owner decision); the UI's `MovementType` has no `Returned` yet; `unit` is now `unitId` plus the unit name; ids are numbers.
  - Every movement needs a `requestId` (UUID).
  - No date rules are enforced on movement dates. The ledger is ordered by posting (id), not by date, as documented.
  - A wrong movement is corrected only by an adjustment or a return; there is no reversal of a receipt as such.
  - Project profitability (combining stock usage, expenses, payroll and commissions) is not built yet.

### 2026-10-24: Inventory costing changed to FIFO
- Owner decision: inventory is costed FIFO (the oldest stock is used first). This replaces the moving weighted average. Posted history is kept as it was.
- Migration `V4__inventory_fifo_cost_layers.sql`, additive only:
  - `stock_movements.costing_method`: existing rows are `WAC`, new rows `FIFO`. No row was updated.
  - `stock_cost_layers`: one layer per receipt or positive adjustment, with its remaining quantity and value.
  - `stock_cost_allocations`: append-only. It records which layers each issue or negative adjustment drew from, and what returns put back, each reversal naming its draw.
  - Two triggers: allocations are immutable, and a reversal can't exceed its draw.
  - A backfill that carries each material's existing balance forward as one `LEGACY_BALANCE` layer, after checking that every ledger reconciles. If any ledger doesn't, it stops.

  It was verified on a fresh PostgreSQL 17 database, and on a populated V1 + V2 + V3 database: all pre-existing rows were identical by checksum, and the layers equalled the balances. It was also verified to refuse a ledger that doesn't reconcile, applying nothing. Details are in DATABASE.md, "Current Schema Status". Not yet applied to the development database.
- Costing, as DATABASE.md "Stock costing (FIFO)" describes (`inventory.service.FifoCosting`, `StockLedger`):
  - A receipt is a new layer at its price.
  - An issue draws from the oldest open layers first, with one allocation per layer, and its cost (the sum of its draws) is fixed when it is posted.
  - A return puts stock back into the layers its issue drew from, newest draw first, at their cost.
  - Each draw or reversal is rounded once (`HALF_UP`, 2 dp). The last of a layer, or of a draw, takes the exact remainder, so emptied layers are worth 0.00 and a full return reverses the issue to the cent.
  - The owner's example (10 at 2,000, then 10 at 2,400, then an issue of 12) gives 24,800, leaving 8 worth 19,200. This is tested end to end.
  - Every posting checks that the open layers add up exactly to the ledger's running balance, or it is rolled back.
- API changes. Endpoints and status codes are as before, with these additions and changes:
  - New: `GET /api/v1/inventory/materials/{id}/layers?openOnly=true` returns the material's FIFO layers in the order they will be used, as `{id, materialId, origin, sourceMovementId, date, originalQuantity, unitCost, originalCost, remainingQuantity, remainingValue}`.
  - Movements gain:
    - `costingMethod` (`WAC` | `FIFO`);
    - `costLayerId`, the layer a receipt or positive adjustment created;
    - `allocations[]`, as `{id, layerId, layerSourceMovementId, layerOrigin, layerDate, quantity, unitCost, cost, reversesAllocationId}`, signed like the ledger.

    On a multi-layer movement `unitCost` is `|totalCost| / quantity`, for display only.
  - Materials: `averageUnitCost` is renamed **`defaultUnitCost`**. It is `materials.unit_cost`, a reference price only, and can now be edited even after movements; editing it revalues nothing. `stockValue` is the sum of the open layers. The unit is still frozen once stock has moved.
  - `/valuation` sums the open layers.
  - Adjustments take an optional `unitCost`:
    - an increase **must** state it (400 otherwise), and it becomes an `ADJUSTMENT` layer;
    - a reduction must not state it (400), and is costed FIFO.
  - Returns against a pre-FIFO (`WAC`) issue come back at that issue's own cost as a `LEGACY_RETURN` layer. The last such return takes the issue's remaining cost, as before.
- Concurrency and idempotency:
  - Every posting still takes the material row lock first. The open layers (for issues) and the issue's layers (for returns) are also locked `FOR UPDATE`.
  - A resent `requestId` returns the first movement and writes no new layer or allocation. A reused id with different details is still 409.
- Tests: 462 passed (`./mvnw -B -o test`, JDK 25, throwaway PostgreSQL 17 container). The inventory tests went from 42 to 48. The WAC tests were replaced or rewritten to FIFO, and no other module's test changed.
  - `FifoCostingTest`: 8 tests.
    - receipt rounding;
    - the owner's example;
    - a partial draw from the oldest layer;
    - skipping used-up layers;
    - the last of a layer taking the exact remainder (33.33 + 33.33 + 33.34);
    - never more than the layer is worth;
    - layers that can't cover a draw being a bug;
    - the display-only unit cost.
  - `InventoryControllerWebTest`: 7 tests, now covering the layers endpoint's authentication, the new movement fields, and a negative adjustment `unitCost` being rejected.
  - `InventoryApiIntegrationTest`: 24 tests, each rolled back. They cover:
    - receipts as layers, and the stock value as their sum;
    - the owner's example, with its allocations, remaining layer and unchanged cost after a dearer receipt, plus project usage and activity;
    - successive issues walking through three layers, with the value after each;
    - independent layers per material;
    - insufficient stock leaving nothing behind;
    - running out and restocking;
    - issues only to open construction projects;
    - partial and multiple returns going back into their original layers, with project cost reversed to 0;
    - full returns of single-layer and multi-layer issues;
    - returns of awkward costs reconciling to the cent;
    - returns that don't match their issue;
    - FIFO reductions, additions needing a stated cost, a reduction with a cost rejected, and insufficient stock;
    - inactive materials;
    - suppliers on receipts;
    - resends, with no duplicate layers or allocations, and 409 on a changed resend;
    - history filters;
    - ledger, layer and allocation reconciliation over a mixed sequence;
    - legacy WAC data: a carried-forward layer used first, a return against a WAC issue at its own cost, and the house's WAC charge untouched;
    - the unit frozen while the default cost is free to change;
    - the database guards (immutable ledger and allocations, layer bounds, an over-reversal refused);
    - reads that change nothing;
    - 401 for anonymous users.
  - `InventoryConcurrencyIntegrationTest`: 6 tests that commit for real.
    - Eight issues of 20 against two layers of 50: exactly five succeed, each layer is drawn exactly once, and the total is 250,000.
    - Ten receipts at once: ten layers in posting order, and an exact value.
    - Six returns of 10 against a two-layer issue of 30: exactly three succeed, 65,000 comes back, and the layers are restored exactly.
    - Six copies of one receipt: one movement and one layer.
    - Six copies of one issue: one movement and two allocations.
    - A failed issue leaves no movement, no allocation and no changed layer.

    Every test checks that the running balances, the layer totals and the allocation totals reconcile.
  - All Suppliers, Expenses, Clients, Projects, Revenue, Agreements and Settings tests still pass.
  - Afterwards every table was back to 0 rows, and the ledger, allocation and payment triggers were enabled. The scratch databases and the container were removed. The development database and its volume were not touched.
- Limitations and open decisions:
  - **Pre-FIFO history is not re-costed.** Exact FIFO allocations can't be reconstructed from weighted-average postings without changing what projects were charged, so V4 carries each balance forward as one `LEGACY_BALANCE` layer at its recorded value. Old issues stay `WAC` and have no allocations.
  - FIFO order is posting order (`id`), not `movement_date`. A back-dated receipt is used after every layer posted before it.
  - A positive adjustment must state its cost. There is no automatic price for found stock.
  - Returns reverse the issue's newest draw first. This is a deterministic rule; it is not chosen by the user.
  - The frontend still shows `unitCost` and previews issue cost as `q × unitCost`. Its adjust dialog has no cost field.
  - Receipts from supplier invoices and payables are still not built.

### 2026-10-24: Inventory frontend aligned with FIFO (local store)
- Context: the frontend is not connected to the backend yet. It has no API client and no sign-in, and every page reads the local zustand store seeded from `data/seed.ts`. The owner chose to align Inventory with FIFO **inside the local store** for now. Wiring it to `/api/v1/inventory` is a later task: it needs a sign-in design (the backend still has only Spring's default user) and projects from the API.
- `frontend/src/lib/fifo.ts`: the backend's FIFO rules, ported to the local ledger.
  - A receipt, or an increase adjustment, is a layer at its own price.
  - Issues and reductions draw from the oldest layer first, with one allocation per layer.
  - Returns go back into the layers their issue drew from, newest draw first.
  - Each draw is rounded once (`HALF_UP`), and the last of a layer or draw takes the exact remainder. The arithmetic uses integer cents and thousandths.
  - The same pre-posting checks and messages as the backend.
- Changes in the app:
  - The stock value (KPI, table, material sheet) is the sum of the open FIFO layers, never `quantity × unitCost`.
  - `Material.unitCost` is labelled "Default unit cost" (reference only).
  - Movement costs are the FIFO cost posted with the movement, with the layers each one used ("10 × 2,000.00 + 2 × 2,400.00"). There is a `Returned` movement type with its labels.
  - The material sheet shows the open FIFO layers (remaining quantity, unit cost, value), plus a "Return from project" action.
  - In the movement dialog:
    - a "Return from project" option picks the original issue and shows its returnable quantity;
    - the `q × unitCost` "Project cost" preview is replaced by a note that the cost is worked out FIFO on posting, and the toast then shows the actual cost;
    - an increase adjustment requires a unit cost, while a reduction has no cost field and never sends one.
  - `addStockMovement` validates and returns the posted FIFO cost. It throws, saving nothing, on an invalid movement.
  - Supplier invoices no longer overwrite a material's default unit cost.
  - Project material cost (`materialsStock`, used by the project page and reports) is issues at FIFO cost minus returns.
- Tests: Vitest 2.1.9 added as a dev dependency, with an `npm test` script. 19 tests pass (`npm test`), and `npm run build` succeeds.
  - `fifo.test.ts`: 14 tests.
    - the owner's example and its allocation text;
    - partial and multi-layer draws;
    - a later receipt not changing an earlier issue;
    - independent materials;
    - valuation as the sum of the layers;
    - receipts with no allocations;
    - partial, full and awkward-rounding returns;
    - adjustments;
    - the pre-posting checks.
  - `store/inventory.test.ts`: 5 tests.
    - the seed valued from its layers;
    - an unchanged value when the default cost changes;
    - project issue lines at FIFO cost;
    - stock refreshed after a receipt, an issue, a return and an adjustment, with the project cost reversed;
    - invalid adjustments and issues refused without saving;
    - invoices leaving the default cost alone.
- Not applicable while the frontend runs on the local store (to do when it is wired to the API): `requestId` idempotency and retry reuse, double-submit protection for slow requests, `WAC` legacy movements (the local data has none), the `/materials/{id}/layers` endpoint, and the API's error responses and version conflicts.
- Unchanged:
  - The seed data, including the `unitCost` values on seed issues; those are now ignored, because issues are costed FIFO.
  - Low stock and the reorder level, which remain a separate planned change.
  - The backend.

### 2026-10-24: Employees and weekly payroll
- Model: weekly wages, exactly as V1 and DATABASE.md "Payroll" define them. The pay comes from daily attendance (present, half day) and approved overtime, for Monday-to-Sunday weeks paid on the payday from Settings. A week freezes at its first payment, and later changes are corrections. There are no monthly salaries, approval workflow, EPF/ETF, PAYE or statutory rates, because nothing in the requirements defines them.
- **No migration.** V1 already has all ten tables with their constraints, the freeze triggers and the immutability triggers. Entities map them with `ddl-auto=validate`, and the test context started cleanly on V1 to V4.
- Endpoints. All require sign-in, and every `POST`/`PUT` needs a CSRF token.

  | Method | Path | Purpose |
  | --- | --- | --- |
  | GET/POST | `/api/v1/employees` | list (`q`, `role`, `status`, paging, sort) / create |
  | GET/PUT | `/api/v1/employees/{id}` | get / update (with `version`) |
  | PUT | `/api/v1/employees/{id}/status` | `ACTIVE` / `INACTIVE` |
  | GET | `/api/v1/attendance?from&to&employeeId&projectId` | attendance, at most 93 days |
  | PUT | `/api/v1/attendance/{date}` | save the day's register |
  | GET/POST | `/api/v1/overtime` | list (`from`, `to`, `employeeId`, `approved`) / record |
  | PUT | `/api/v1/overtime/{id}/approval` | approve or un-approve (with `version`) |
  | GET | `/api/v1/payroll/weeks?from&to` | period summary, one row per week (Mondays, at most 53 weeks) |
  | GET | `/api/v1/payroll/weeks/{weekStart}` | the week, with every worker's line |
  | GET | `/api/v1/payroll/weeks/{weekStart}/employees/{employeeId}` | one line, with payments, corrections and recoveries |
  | PUT | `…/adjustment` | the owner's advance and other deduction overrides |
  | POST | `…/payments` | pay (the first payment freezes the week); 201 new, 200 resend |
  | POST | `…/payments/{paymentId}/void` | void a payment |
  | POST | `…/close` | freeze a week whose advance deduction takes all of its pay |
  | POST | `…/corrections` | correct a paid week |
  | GET | `/api/v1/payroll/employees/{employeeId}/history?from&to` | a worker's weeks (default: the last 12) and outstanding advances |
  | GET | `/api/v1/payroll/projects/{projectId}/labour` | a project's labour cost |
  | GET/POST | `/api/v1/payroll/advances` | list / record an advance (201 new, 200 resend) |
  | POST | `/api/v1/payroll/advances/{id}/void` | void an advance that nothing has recovered yet |

  There is no DELETE anywhere: employees are deactivated, and payments and advances are voided.
- Rules: see DATABASE.md "Payroll", "Implemented".
- No double counting:
  - Labour cost = gross wages split by project (`payroll_line_costs` once frozen), plus corrections.
  - Advances and payments are never a cost.
  - The Labour expense category is system-generated, so it can't be entered by hand.
  - Project labour counts each worker-week once, frozen or live.
- Concurrency:
  - Every payroll write, and every attendance or overtime write, takes the worker's `employees` row lock.
  - Payments can't overpay, and a week freezes once (`uq_payroll_lines_employee_week` is the backstop).
  - Attendance saved while the week is being paid either lands before the freeze or is refused by the trigger.
- Tests: **505 passed, 0 failures** (`./mvnw -B -o test`, JDK 25, throwaway PostgreSQL 17 container). 43 are new.
  - `PayrollCalculatorTest` (8): half days and rounding, approved overtime only, the project split adding up to gross, the rounding residue, absent days, default and capped advance deductions, the override, and a negative net.
  - `EmployeeControllerWebTest` (5) and `PayrollControllerWebTest` (6): 401 for anonymous users, 403 without CSRF on every write, field validation, 201 then 200 on a resend, 400/404/409 mapping, and no delete.
  - `EmployeeApiIntegrationTest` (7):
    - create, update and a stale version;
    - required fields;
    - search, filters, sort and deactivation;
    - the day register (save and replace);
    - the register rules (future, Sunday, joining date, duplicates, project type and status, inactive);
    - overtime (rate default, hours limits, approval, range cap);
    - 401 for anonymous users.
  - `PayrollApiIntegrationTest` (13):
    - a live week;
    - the freeze on first payment, then refused attendance, overtime and adjustment changes, the cap, a resend, and a conflict;
    - payment only from payday;
    - advances recovered oldest first, and only when paid;
    - advance idempotency;
    - adjustments, including a negative net that blocks payment;
    - closing a zero week, so the advance isn't deducted twice;
    - a voided payment;
    - corrections and their limits, and overpaid;
    - project labour (paid + corrections + open, with advances not counted);
    - the period summary;
    - the database guards;
    - 401 for anonymous users.
  - `PayrollConcurrencyIntegrationTest` (4, real commits):
    - six payments of 2,000 against 9,000: exactly four succeed, and the week freezes once;
    - six copies of one payment: one payment;
    - attendance racing the first payment: the frozen days equal the recorded days;
    - a refused payment leaves the week unfrozen.
  - All existing module tests still pass. Afterwards every table was back to 0 rows and all 17 triggers were enabled.
- Limitations and decisions for the owner:
  - ~~No role model.~~ Superseded 2026-10-25: payroll requires a signed-in `ADMIN` or `SUPER_ADMIN` (see "Authentication, users and roles"). Both roles may pay payroll; no finer split was requested.
  - Payment is allowed from payday. The original DATABASE.md wording said "once the week has ended" (after Sunday); the UI pays on payday.
  - The default advance deduction counts only recoveries already frozen, so two unpaid weeks can both show the same advance until one is paid. The UI instead schedules deductions week by week.
  - `payroll_corrections` has no `request_id`, so a resent correction is recorded twice. It can't be edited or deleted either: the audit trail it needs before that is not built.
  - No activity-log entries for payroll (the activity log is per project). The rows themselves, with their timestamps and void reasons, are the audit.
  - No staff number, email or NIC: none are in the schema or the UI.
  - An overpayment from a negative correction is recovered by hand, as an other deduction in a later week.
  - The frontend still runs on its local store; it isn't connected to these endpoints.

### 2026-10-25: Authentication, users and roles
- Approach: Spring Security with server-side sessions (`JSESSIONID`) and a JSON login endpoint.
  - Before this, only Spring Boot's default security existed: a generated in-memory user, HTTP Basic and form login, no roles, and no `SecurityFilterChain`. That is all replaced.
  - No JWT, no HTTP Basic, no form login, no public sign-up.
  - Details: ARCHITECTURE.md, "Authentication and Authorization".
- Roles (owner decision, exactly two):
  - `SUPER_ADMIN`: everything, plus user administration and the actuator endpoints other than health.
  - `ADMIN`: all business modules, and their own profile and password.
  - Every business endpoint was checked. All are open to both roles, because no requirement restricts any of them to Super Admin. Settings is included; the owner may want to reserve it.
- Endpoints:

  | Method and path | Access | Purpose |
  | --- | --- | --- |
  | GET `/api/v1/auth/csrf` | public | sets the `XSRF-TOKEN` cookie (204) |
  | POST `/api/v1/auth/login` | public (CSRF required) | `{username, password}` → `{id, username, displayName, role}` |
  | POST `/api/v1/auth/logout` | signed in or not (CSRF required) | invalidates the session (204) |
  | GET `/api/v1/auth/me` | both roles | the signed-in user |
  | PUT `/api/v1/auth/me/password` | both roles | `{currentPassword, newPassword}` (204) |
  | GET `/api/v1/users` | Super Admin | list (`q`, `role`, `enabled`, paging, sort `username`/`displayName`/`createdAt`/`id`) |
  | GET `/api/v1/users/{id}` | Super Admin | one user |
  | POST `/api/v1/users` | Super Admin | create (`role` defaults to `ADMIN`) → 201 + `Location` |
  | PUT `/api/v1/users/{id}` | Super Admin | display name and email (`version` required) |
  | PUT `/api/v1/users/{id}/role` | Super Admin | change role (`version` required) |
  | PUT `/api/v1/users/{id}/status` | Super Admin | activate or deactivate (`version` required) |
  | PUT `/api/v1/users/{id}/password` | Super Admin | reset someone else's password (204, never echoed) |

- Rules:
  - Nobody changes their own role or status, or resets their own password, through user administration (422).
  - There is always at least one active Super Admin. Account changes lock the active Super Admin rows and re-check the actor.
  - Deactivation, a role change or a password reset bumps `auth_version`, so that user's sessions end on their next request.
  - Generic 401 `Invalid username, email or password.` for an unknown user, a wrong password and a disabled account.
  - New session id at sign-in. CSRF on every write, login and logout included.
  - 429 + `Retry-After` after 5 failures per username or 20 per address in 15 minutes.
  - CORS only for the configured origins.
- First Super Admin: created once at startup from `BOOTSTRAP_SUPER_ADMIN_USERNAME` and `BOOTSTRAP_SUPER_ADMIN_PASSWORD`, only while no `SUPER_ADMIN` exists. It is never re-applied, and the password is never logged. The steps are in DEVELOPMENT_WORKFLOW.md, "Authentication setup".
- Audit: new append-only `security_events` table (sign-ins, sign-outs, every account change; never credentials). `created_by` on business rows is now the signed-in user.
- Migration: `V5__users_roles_and_security_events.sql` (see DATABASE.md, "Current Schema Status"). Verified on fresh and upgraded throwaway PostgreSQL 17 databases. Not applied to the development database.
- Files:
  - New: `security/config/*`, `security/user/*` (including `dto/*`), `security/auth/*`, `security/audit/*`, `config/web/CorsConfig` and `CorsProperties`, `common/audit/AuditUser` and `CurrentUser`.
  - Changed: `AuditableEntity`, `StockMovement`, `PayrollLine` and `ActivityEntry` (fill `created_by`), `application.properties`, and `compose.yaml` (passes the new variables through; local `SESSION_COOKIE_SECURE=false` and `CORS_ALLOWED_ORIGINS`).
  - `.env` and `.env.example` were not changed (owner instruction). The new variables are documented in DEVELOPMENT_WORKFLOW.md instead.
- Tests: full backend suite on a throwaway PostgreSQL 17 container (2026-10-25): 64 classes, 570 tests, 0 failures, 0 errors. Before this work there were 505 tests.
  - New:
    - `AuthApiIntegrationTest`: login, generic errors, disabled accounts, a legacy mixed-case username, session-id rotation, the real CSRF cookie flow, logout, CSRF on writes, security headers, the throttle, audit, password change, `created_by`, no sign-up.
    - `UserAdministrationApiIntegrationTest`: create and validation, every admin endpoint refused to an Admin (including self-promotion), filters, versions, promotion and demotion ending sessions, no self-change, the last Super Admin, deactivation ending sessions, password reset.
    - `EndpointAuthorizationIntegrationTest`: every mapped endpoint × anonymous, Admin and Super Admin; methods without a handler; unknown paths; actuator; CSRF.
    - `CorsIntegrationTest`.
    - `SuperAdminBootstrapIntegrationTest`.
    - `SecuritySchemaIntegrationTest`: the V5 constraints and the immutability trigger.
    - `UserAdministrationConcurrencyIntegrationTest`: two Super Admins deactivating each other at once.
    - `LoginAttemptThrottleTest`, `PasswordAndUsernameRulesTest`, `CorsPropertiesTest`.
  - Changed: the existing `@SpringBootTest` tests sign in as `@WithMockUser(roles = "ADMIN")` instead of the role-less default. `AgreementStorageIntegrationTest` now expects 403 rather than 404 for non-API paths: they are still never served, but the new rules deny them before routing.
- Known limitations:
  - Sessions and the sign-in throttle live in memory. They reset on restart and assume one backend instance.
  - Behind a reverse proxy, the per-address limit sees only the proxy until forwarded headers are configured deliberately.
  - No API reads `security_events` yet.
  - No self-service password reset by email (no email infrastructure), and no account lockout beyond the time-boxed throttle.
  - Lost the only Super Admin password: another Super Admin can reset it; otherwise a deliberate database-level action is needed. The bootstrap never runs twice.
  - The frontend is not connected yet. It needs a login screen, `credentials: 'include'` on every call, the `X-XSRF-TOKEN` header on writes, and handling of 401 (sign in again) and 403. Hiding Super Admin screens in the UI is cosmetic; the backend enforces the rules.
  - Tests read the repository-root `.env` like the application does. If it contains `BOOTSTRAP_SUPER_ADMIN_*`, the test context creates that Super Admin in the throwaway test database (harmless, but expected).

### 2026-10-25: Commissions
- What existed: V1 already had `commission_arrangements`, `commission_payments` (with the payment guard trigger) and the single-row `commission_policy` (design pool 20%, optional construction level), plus the documented rules. There was no Java code. Nothing new was invented on top: the module implements those tables and rules.
- Business rules (from DATABASE.md and the existing UI; nothing new decided):
  - Base: the agreed contract value, not the billed or collected amount. Each arrangement: `amount = round(contract value × rate / 100, 2, HALF_UP)`, stored at confirmation with `confirmed_on`. Later settings changes never move it.
  - Design 80/20: pool = `commission_policy.design_commission_pool_pct` (20), company share = 100 − pool. The pool amount is rounded half-up; company = contract value − pool. They add up exactly, and the residual cent goes to the company.
  - Rates above the pool (design) or the optional construction level are a warning. Confirming anyway needs `acceptPolicyException: true` and marks the rows `policyException`. A draft may be saved over the limit, with the warning shown.
  - Construction: each recipient's agreed rate on the contract value. No rate is assumed; the design 20% never applies.
  - Consultation projects: no commission (422, and the V1 composite key).
  - Recipients: free-text names per arrangement, as in the UI. There is one live arrangement per project, recipient and role (V6 unique index).
- Lifecycle:
  - `PENDING` drafts are edited or removed by saving the project's set; they are not a cost.
  - "Confirm" confirms every draft in the set.
  - `CONFIRMED` rows are frozen (service + V6 trigger). They show as `CONFIRMED`, `PARTIALLY_PAID` or `PAID` from their payments.
  - `VOIDED` needs a reason and no active payments. A correction is void + add again; both stay visible with the reason, plus `COMMISSION` activity entries.
- Payments:
  - partial or full, never above what is still owed, and not on drafts or voided rows;
  - no future date; an active payment method;
  - `requestId` idempotency (resend → 200 with the first payment, a different body → 409);
  - void with a reason;
  - the arrangement row is locked for each payment and void.
- Revenue, Expenses and Payroll integration:
  - No changes to those modules.
  - Commission cost = confirmed arrangements, recognised at confirmation (the documented "Project costs" rule). Payments only settle it.
  - No expense row is ever written for commission, and the Commission expense category stays system-generated, so it can't be entered by hand. Only construction has one; design has no Commission category at all.
  - Revenue recognition (at billing) is untouched.
  - A project profitability report doesn't exist yet. `GET /api/v1/projects/{id}/commissions` returns the project's committed, paid, outstanding and draft totals for it to use.
- Endpoints (both roles unless noted; every write needs CSRF):

  | Method and path | Purpose |
  | --- | --- |
  | GET `/api/v1/projects/{projectId}/commissions` | the project's arrangements, policy check, design split, totals |
  | PUT `/api/v1/projects/{projectId}/commissions` | save the set: `{confirm, acceptPolicyException?, arrangements:[{id?, version?, recipient, role?, ratePct}]}` |
  | GET `/api/v1/commissions` | directory: `recipient`, `q`, `projectId`, `type`, `status` (`PENDING`/`CONFIRMED`/`PARTIALLY_PAID`/`PAID`/`VOIDED`), `from`/`to` (confirmed date), paging; sort `recipient`/`project`/`amount`/`outstanding`/`confirmedOn`/`id` (default most outstanding first) |
  | GET `/api/v1/commissions/summary` | committed, paid, outstanding, counts (`type` optional) |
  | GET `/api/v1/commissions/recipients` | by recipient (confirmed only) |
  | GET `/api/v1/commissions/payments` | payments, newest first (`projectId`, `commissionId`, `from`, `to`, `includeVoided`) |
  | GET `/api/v1/commissions/{id}` | one arrangement with its payments |
  | POST `/api/v1/commissions/{id}/void` | `{reason, version}` |
  | GET/POST `/api/v1/commissions/{id}/payments` | list / record (`{requestId, paymentDate, amount, paymentMethodId, reference?, notes?}`; 201 new, 200 resend) |
  | POST `/api/v1/commissions/{id}/payments/{paymentId}/void` | `{reason, version}` |

- Authorization: changing the commission policy (`/api/v1/settings/commission-policy`, any method but GET) is now `SUPER_ADMIN` only. Reading it, and all commission work, is open to both roles. No new roles.
- Migration: `V6__commission_voids_and_guards.sql` (see DATABASE.md, "Current Schema Status"). Verified fresh, on a V5 database with data, and on duplicate data (it stopped and applied nothing). Not applied to the development database.
- Files:
  - New: `commissions/entity/*`, `repository/*`, `dto/*`, `mapper/CommissionMapper`, `service/CommissionCalculator`, `CommissionService`, `CommissionPaymentService`, `validation/CommissionRules`, `controller/CommissionController`, `ProjectCommissionController`.
  - Changed: `SecurityConfig` (the commission-policy rule).
  - The empty placeholder `package-info.java` files of the commissions package were removed.
- Tests: full backend suite on a throwaway PostgreSQL 17 container (2026-10-25): 69 classes, 616 tests, 0 failures, 0 errors, 0 skipped (570 before). 45 are new commission tests: calculator 7, API 19, schema 10, concurrency 4, controller slice 5. 1 new settings test.
  - New:
    - `CommissionCalculatorTest`: the documented example, half-up rounding, split adds up exactly with the residual to the company, policy checks, statuses.
    - `CommissionApiIntegrationTest`: drafts, confirmation, the residual cent, the pool exception, construction, consultation, validation, resend without duplicates, frozen confirmed rows, history after a settings change, payments, overpayment and future dates, idempotency, drafts and voids not payable, voiding payments, correction by void, directory, summary and recipients, no expense double count.
    - `CommissionSchemaIntegrationTest`: the V6 trigger, checks, unique index, consultation FK.
    - `CommissionConcurrencyIntegrationTest` (committed): six concurrent payments where only four fit, a duplicate request sent at once, a payment racing a void, two saves creating the same recipient.
    - `CommissionControllerWebTest`.
  - Changed:
    - `SettingsApiIntegrationTest`: the policy update now runs as Super Admin, plus a test that an Admin gets 403 and nothing changes.
    - `EndpointAuthorizationIntegrationTest`: expects 403 for an Admin on the policy update and checks the new endpoints exist.
- Known limitations and owner decisions:
  - **Recipients table not built.** The task asked for recipients with contact details and activate/deactivate. The approved schema (2026-10-24) deliberately removed a separate recipients table because the UI types a name per project, so the module keeps the name on each arrangement. A recipients table would need an owner decision, a migration and UI changes. As a result:
    - there is no recipient activation or deactivation;
    - the same person spelled differently is grouped only when the spelling differs just in case or spacing.
  - Confirmed rows can't be revised in place. The UI's "Revise" currently lets a confirmed rate be edited; the backend answers 422 ("void it and add the corrected one"). The frontend needs that flow when it is wired up.
  - Policy exceptions can be confirmed by both roles (the task restricted only the company-wide policy). The owner may want exceptions to be Super Admin only.
  - The cost is recognised at confirmation, with no further accrual basis. Commission doesn't follow billing or collection, by the documented rule, so a project billed in parts carries its full confirmed commission from the start.
  - The directory, summary and recipient views compute paid and outstanding in memory over the matching arrangements. That's fine for a company's hundreds of commissions; it would need SQL aggregation at much larger volumes.
  - No report module yet. Project profitability will read the project totals from this module when the reports are built. (Done: see "Reports and dashboard".)

### 2026-10-25: Reports and dashboard
- Read-only reports module (`reports`). No migration: every figure comes from the existing schema. Definitions, sources and date bases are in ARCHITECTURE.md, "Reports". The definitions match DATABASE.md and the frontend's `finance.ts`.
- Endpoints (GET only, `SUPER_ADMIN` and `ADMIN`, 401 when anonymous):

  | Path | What |
  | --- | --- |
  | `/api/v1/reports/dashboard` | active projects (`IN_PROGRESS` + `ON_HOLD`) by type and all statuses, active contract value, revenue recorded / payments received / written off (all time), receivables outstanding and overdue (now), project costs and estimated project profit (every project's own report, summed), company expenses, expense and commission payables, FIFO inventory value, monthly revenue recorded / payments received / manual expenses for the financial year to date |
  | `/api/v1/reports/financial-summary?from&to` | per-block period figures with their own date basis (billing, receipts, write-offs, manual expenses, materials issued less returns, payroll weeks, commission confirmed and paid) plus a position as of today |
  | `/api/v1/reports/projects/{id}/financial` | contract value; billed, deductions, revenue recorded, received, written off, outstanding, overdue, unbilled; costs (manual, materials, labour with its open-week part, commission, total); commission paid, outstanding and drafts; profit, margin, `RECORDED`/`ESTIMATED` |
  | `/api/v1/reports/projects/{id}/cost-breakdown` | the four cost categories with their lines (expense categories, materials, labour parts, confirmed arrangements); total = Σ categories to the cent; exclusions listed |
  | `/api/v1/reports/receivables` | billed installments with project, client, amounts, due date, days overdue and aging bucket; filters `projectId`, `status`, `aging`, `openOnly` (default true); paging; sort `daysOverdue`, `dueDate`, `outstanding`, `billedOn`, `project`; totals for the filter |
  | `/api/v1/reports/inventory-valuation` | FIFO totals plus a page of materials with their layer value (`q`, `category`) |
  | `/api/v1/reports/payroll?from&to` | payroll weeks (Mondays in range) with labour cost = gross + corrections, paid, remaining payable / not yet due |

- Commission summaries were not repeated: `/api/v1/commissions/summary`, `/recipients` and `/payments` already exist.
- Changes outside `reports`: one additive read method, `ReceivableService.billedReceivables()`, so the receivables report uses the Revenue module's own formula. Nothing else changed in any module.
- Files:
  - New: `reports/controller/ReportController`, `reports/service/ReportService`, `reports/repository/ReportQueries`, `reports/dto/*` (8 records).
  - Changed: `revenue/service/ReceivableService` (the new read method).
  - Removed: the reports placeholder `package-info.java` files (main 4, test 2).
- Tests: full backend suite on a throwaway PostgreSQL 17 container (2026-10-25): 71 classes, 629 tests, 0 failures, 0 errors, 0 skipped (616 before; 13 new).
  - `ReportApiIntegrationTest` (12). A fixture built through every module's real API: billing with a delay deduction, a voided receipt, a write-off, project and company expenses with a payment, a stock purchase, issue and return, two days of attendance, commissions confirmed, voided and draft, and a design project with commission. It checks:
    - every project figure by hand (profit 399,000, margin 68.79%);
    - the cost breakdown adding up to the cent;
    - the recorded basis after completion;
    - an empty project and a missing project (404);
    - dashboard and summary deltas;
    - inclusive date boundaries;
    - range validation;
    - receivables aging and filters;
    - FIFO valuation (75,000 left of 100,000 bought);
    - payroll gross only;
    - that reading every report changes no table (checksums).
  - `ReceivableAgingTest` (1): the bucket edges.
  - `EndpointAuthorizationIntegrationTest` now covers the 7 report endpoints automatically.
- Known limitations:
  - **Not available yet:** supplier invoices (project material cost, payables) and other income (revenue). Each report lists them in `notIncluded`/`excluded` instead of showing 0.
  - **Current state, not history:** period figures reflect records as they are now. For example, a commission confirmed in a period and voided later no longer counts for that period, and the dashboard's open-week labour can still change.
  - **The dashboard has no payroll liability:** payroll is per week, see `/reports/payroll`. The financial summary has none either for weeks outside its range.
  - **No combined period profit:** revenue is recognised at billing while costs carry their own dates, so the summary keeps them apart. Project profit is per project, to date.
  - **Scale:** receivables, the dashboard's project totals and payroll weeks are computed in memory (see ARCHITECTURE.md). That is fine at this company's volume.
  - **Frontend:** not connected yet. The UI's dashboard "Expenses" series also included stock, payroll and commission lines; the API's monthly series is manual expenses only, and named so.

### 2026-10-25: Supplier invoices and payables
- No migration: `supplier_invoices`, `supplier_invoice_lines` and `supplier_payments` already existed in V1.
- Rules (from DATABASE.md):
  - The total is the sum of the lines; each line = round(quantity × unit price, 2).
  - The due date defaults to invoice date + the supplier's credit days.
  - (supplier, invoice no) is unique: a resent invoice gets a 409.
  - The supplier must be active.
  - **`STOCK`:**
    - every line names a material in that material's own unit;
    - each line is posted as a FIFO receipt (its own layer at the line price), linked to the line;
    - it is stock value, not a cost.
  - **`PROJECT`:**
    - construction projects only;
    - the total is that project's material cost;
    - no stock movement.
  - **Payments:**
    - partial or full, never above what is owed;
    - `requestId` idempotency (resend → 200, a different body → 409);
    - voidable with a reason;
    - the invoice row is locked, so concurrent payments can't overpay.
  - Invoices are not edited or deleted.
- Endpoints (both roles; CSRF on writes):

  | Method and path | Purpose |
  | --- | --- |
  | POST `/api/v1/supplier-invoices` | create (optional `initialPayment`), 201 |
  | GET `/api/v1/supplier-invoices` | list (`supplierId`, `projectId`, `allocation`, `status`, `from`/`to` on invoice date), newest first |
  | GET `/api/v1/supplier-invoices/summary` | count, total, stock total, project total, paid, outstanding, overdue |
  | GET `/api/v1/supplier-invoices/{id}` | invoice, lines and payments |
  | GET/POST `/api/v1/supplier-invoices/{id}/payments` | list / record (201 new, 200 resend) |
  | POST `/api/v1/supplier-invoices/{id}/payments/{paymentId}/void` | void |

- Reports:
  - `PROJECT` invoices are now a project cost (`costs.supplierInvoices`, cost-breakdown category `SUPPLIER_INVOICES`).
  - The dashboard payables have a `suppliers` field.
  - The financial summary has a `supplierInvoices` block (stock purchases vs project invoices, `invoice_date`) and `position.suppliersOutstanding`.
  - "Supplier invoices" was removed from `notIncluded`.
- Changes outside `suppliers`:
  - `inventory`: `Posting` carries an invoice line id; `StockMovement` now writes `supplier_invoice_line_id` / `invoice_allocation`; new `StockMovementService.receiveInvoiceLine` and the `InvoiceLineReceipt` record.
  - `reports`: the fields above.
- Tests: full backend suite on a throwaway PostgreSQL 17 container (2026-10-25): 73 classes, 637 tests, 0 failures, 0 errors, 0 skipped (629 before).
  - `SupplierInvoiceApiIntegrationTest` (7): stock receipts and valuation, project cost, validation, duplicates, payments, idempotency and voids, initial payment and overdue, summary separation.
  - `SupplierPaymentConcurrencyIntegrationTest` (1, committed): five concurrent payments, only two fit.
  - `ReportApiIntegrationTest` updated for the new category.
- Not built: invoice attachments (the V1 columns stay empty), editing or cancelling an invoice, and the warning when a manual expense matches a supplier invoice number.

### 2026-10-26: Other income
- No migration: `other_income` and `other_income_receipts` already existed in V1.
- Lives in the `revenue` module (`OtherIncomeService`, `OtherIncomeController`), which owns those tables.
- Rules (from DATABASE.md and the frontend's `finance.ts`):
  - Income is recognised on `income_date`, as the Other Income category, separate from project revenue.
  - Receipts settle it, never more than is owed.
  - outstanding = amount − active receipts.
  - Status: `PAID`, `OVERDUE` (outstanding and past an optional due date), `PARTIALLY_PAID` or `DUE`.
  - The due date can't be before the income date.
  - Receipts use `requestId` idempotency (resend → 200, a different body → 409), are voidable with a reason, and lock the income row.
  - Income is not edited or deleted.
- Endpoints (both roles; CSRF on writes):

  | Method and path | Purpose |
  | --- | --- |
  | POST `/api/v1/other-income` | create, 201 |
  | GET `/api/v1/other-income` | list (`status`, `from`/`to` on the income date), newest first |
  | GET `/api/v1/other-income/summary` | count, amount, received, outstanding, overdue |
  | GET `/api/v1/other-income/{id}` | one record |
  | GET/POST `/api/v1/other-income/{id}/receipts` | list / record (201 new, 200 resend) |
  | POST `/api/v1/other-income/{id}/receipts/{receiptId}/void` | void |

- Reports:
  - New fields:
    - dashboard: `revenue.otherIncome`, `revenue.otherIncomeReceived`, `receivables.otherIncomeOutstanding`, `receivables.otherIncomeOverdue`;
    - financial summary: `revenue.otherIncome` (income date), `revenue.otherIncomeReceived` (receipt date), `position.otherIncomeOutstanding`.
  - Project revenue figures are unchanged. `notIncluded` is now empty.
- Tests: full backend suite on a throwaway PostgreSQL 17 container (2026-10-26): 74 classes, 641 tests, 0 failures, 0 errors, 0 skipped (637 before).
  - `OtherIncomeApiIntegrationTest` (4): receipts with idempotency and voids, validation, overdue and summary, report figures.
  - `ReportApiIntegrationTest` updated.
- Not built: editing or deleting an income record.

### 2026-10-26: Activity feed and config cleanup
- Activity: `GET /api/v1/activity` reads the activity log written by the other modules.
  - Filters: `projectId` (a project's Activity tab), `kind`, `from`/`to` (activity date).
  - Newest first, paged (at most 100 per page), both roles, read-only.
  - New: `ActivityController`, `ActivityResponse`, `ActivityMapper`; `ActivityService.feed`. The repository gained `JpaSpecificationExecutor`.
- Config: removed the empty `config.persistence` placeholder. Nothing needs JPA configuration beyond Spring Boot's defaults (audit columns come from `AuditableEntity` and `CurrentUser`), so none was added. `config` is `ClockConfig` plus `config.web` (CORS).
- Tests: full backend suite on a throwaway PostgreSQL 17 container (2026-10-26): 75 classes, 643 tests, 0 failures, 0 errors, 0 skipped (641 before).
  - `ActivityApiIntegrationTest` (2).
  - `ActivityServiceTest` updated for the new constructor.
  - `EndpointAuthorizationIntegrationTest` covers the endpoint automatically.

### 2026-10-26: Backend gaps closed
- Migration `V7__reversals_corrections_and_invoice_cancel.sql` (see DATABASE.md).
- **Revenue:**
  - `PUT /api/v1/projects/{id}/schedule` revises the scheduled installments.
    - Billed rows must be sent unchanged; unbilled rows may change, be removed or be added.
    - The scheduled rows must still equal the contract value. Variations are not touched.
    - Each row needs its version (409 if stale).
  - `POST /api/v1/projects/{id}/variations` adds a `VARIATION` installment, billed at once (default today). The contract value never changes.
  - `POST /api/v1/projects/{id}/write-offs/{writeOffId}/reverse` `{reason, version}` reverses a write-off: it is kept, stops counting, and the reports exclude it.
- **Payroll corrections:**
  - `requestId` is now required: a resend returns 200 with the first correction; the same id with different values is a 409.
  - `POST .../corrections/{id}/void` `{reason, version}` voids a correction under the same checks as recording one of −delta.
  - Voided corrections no longer change what is owed or the labour cost. Editing = void + new.
- **Clients:** `GET /api/v1/reports/clients/{id}/financial` returns the client's projects (each as its project report) and their totals.
- **Agreements:** `DELETE /api/v1/projects/{id}/agreement` (204) retires the current agreement. The row and its PDF are kept, like a replacement.
- **Supplier invoices:**
  - `PUT /{id}` edits the invoice. Due date and notes can always change; the number, date, project and lines can change only on a `PROJECT` invoice with no active payments.
  - `POST /{id}/cancel` `{reason, version}` cancels a `PROJECT` invoice with no active payments. It is then no payable and no cost, and its number can be reused. `STOCK` invoices are never cancelled.
  - `PUT /{id}/attachment` (multipart PDF) and `GET /{id}/attachment/download` handle a PDF attachment.
    - It is stored in the same private store as agreements (`agreements.storage-dir`), with the same checks.
    - A replaced file is deleted.
- **Expenses:** create and update responses carry a `warning` when the supplier and invoice number match a recorded supplier invoice. The expense is still saved.
- **Other income:** `PUT /{id}` edits it (the amount can't go below what was received). `DELETE /{id}` deletes it only when it has no receipts at all.
- Tests: full backend suite on a throwaway PostgreSQL 17 container (2026-10-26): 76 classes, 655 tests, 0 failures, 0 errors, 0 skipped (643 before).
  - New: `ScheduleAndWriteOffIntegrationTest` (3).
  - Added to existing classes: payroll idempotency and void (`PayrollApiIntegrationTest`); agreement removal and supplier invoice attachment (`AgreementStorageIntegrationTest`); edit, cancel and expense warning (`SupplierInvoiceApiIntegrationTest`); other income edit and delete; the client report.
  - Updated: `AgreementControllerWebTest` (DELETE now exists), the payroll correction helper (`requestId`), `InstallmentScheduleServiceTest` (constructor).

### 2026-10-30: User email and development sample data
- Migration `V8__app_users_email.sql`: optional `app_users.email`, lower case, unique when set (see DATABASE.md).
- Users API:
  - `POST /api/v1/users` and `PUT /api/v1/users/{id}` take an optional `email`. It is trimmed and lower-cased; a blank email clears it.
  - An invalid address gets 400, and an address another user already has gets 409.
  - The user responses and `GET /api/v1/auth/me` (and the login response) include `email`.
  - The `q` search also matches the email.
  - The email change is recorded in `security_events` (`USER_UPDATED`).
- The frontend has no user-management screen yet, so nothing in the UI shows the email.
- Development database:
  - V2 to V8 applied on 2026-10-30 (the running backend image had only V1).
  - The owner's Super Admin was created through the one-time bootstrap. The password was passed for that start only and isn't stored in `.env`.
  - One Admin was created through the API. User details are not written in the repository, because it is public.
  - Then the demo data was loaded with `frontend/scripts/seed-dev-data.ts` (DEVELOPMENT_WORKFLOW.md, "Sample data"):
    - 8 clients and 10 projects (19 of 35 installments billed, 7 agreement PDFs);
    - 17 client receipts and 2 other income;
    - 22 supplier invoices and 18 supplier payments;
    - 60 stock movements and 45 expenses;
    - 11 employees, 1,495 attendance marks and 251 payroll payments;
    - 11 commission arrangements and 7 commission payments.
  - Verified: both users sign in. The Admin gets 200 on `/api/v1/projects` and 403 on `/api/v1/users`.
- Tests:
  - `UserAdministrationApiIntegrationTest`: email normalised, unique ignoring case, rejected when invalid, optional, searchable, changed, cleared, and visible on `/me`.
  - `SecuritySchemaIntegrationTest`: `ck_app_users_email` and `uq_app_users_email`.
  - Full backend suite on a throwaway PostgreSQL 17 container (2026-10-30): 660 tests (first written as 681, see the correction of 2026-10-30 below), 0 failures, 0 errors, 0 skipped.

### 2026-10-30: Email required, sign-in with email
- Migration `V9__app_users_email_required.sql`: `app_users.email` is now required (see DATABASE.md).
- Users API: `email` is required on `POST /api/v1/users` and `PUT /api/v1/users/{id}` (400 when missing or blank). It can no longer be cleared.
- Sign-in: `POST /api/v1/auth/login` accepts the username or the email in `username`, both case-insensitive. The failure message is now `Invalid username, email or password.`
- Bootstrap: `BOOTSTRAP_SUPER_ADMIN_EMAIL` is required together with the username and password. It is validated and must not belong to another account. `compose.yaml` passes it through.
- Development database:
  - The Super Admin's username was changed to the one the owner chose, by SQL. There is no rename endpoint.
  - The change bumped `auth_version`, so open sessions end, and wrote a `USER_UPDATED` security event.
  - Verified: both users sign in with their username and with their email; the old username gets 401.
- `seed-dev-data.ts` now requires `SEED_ADMIN_EMAIL`. Its comments were rewritten.
- Tests:
  - `UserAdministrationApiIntegrationTest`: email required and can't be cleared; sign-in by email in any case; wrong password by email gets the generic message.
  - `SecuritySchemaIntegrationTest`: NOT NULL email.
  - `SuperAdminBootstrapIntegrationTest`: email missing, invalid or already used.
  - `TestUsers` and the SQL fixtures now set an email.
  - Full backend suite on a throwaway PostgreSQL 17 container (2026-10-30): 662 tests (first written as 683), 0 failures, 0 errors, 0 skipped.
- `frontend/tsconfig.scripts.json` (referenced from `tsconfig.json`) type-checks `scripts/` with the Node types, so the editor no longer reports `process`, `Buffer` and `node:crypto` as unknown.

### 2026-10-30: Frontend connected to the backend, phase 1 (sign-in and reading)
- Approach (owner's choice): the React UI and its zustand store keep their shape. The store is filled from the API instead of `data/seed.ts`, and `lib/finance.ts` `derive()` keeps working the figures out as before. Later phases turn the store's actions into API calls, one module at a time.
- Frontend:
  - `src/lib/api.ts`: fetch wrapper. It sends `credentials: 'include'` on every call and adds `X-XSRF-TOKEN` (from the `XSRF-TOKEN` cookie) on writes. Problem-details errors become `ApiError` (message, status, field errors). A 401 sends the user back to sign in. It also has paging helpers. The base URL is `VITE_API_BASE_URL` (default `http://localhost:8080/api/v1`).
  - `src/store/auth.ts`: session check (`/auth/me`), sign-in with username or email, and sign-out.
  - `src/pages/login.tsx`: the sign-in page, built from the existing card, field and button components.
  - `src/components/app/session-gate.tsx`: shows the sign-in page, then a loading screen, then the app. An error screen offers a retry.
  - `src/data/api-load.ts`: `loadAll()` reads every module and maps it onto `DataState`.
    - Voided payments, receipts and advances, reversed write-offs, cancelled supplier invoices and voided commissions are left out.
    - Stock movements are kept in posting (`id`) order, the backend's FIFO order.
    - Attendance and overtime are read in 90-day windows.
  - The store starts empty and gets `load()`. `reset()` (the demo data) stays for the unit tests only.
  - The topbar shows the signed-in user and role. "Reset sample data" became "Reload data", and "Sign out" works.
  - `lib/dates.ts` `TODAY` is now today's date in Colombo (it was fixed at 2026-10-22).
- Backend: read-only list endpoints for the loader (both roles):
  - `GET /api/v1/expenses/payments`;
  - `GET /api/v1/projects/{id}/write-offs`;
  - `GET /api/v1/payroll/payments?from&to`;
  - `GET /api/v1/payroll/adjustments?from&to` (new `PayrollAdjustmentResponse`).
  - Voided and reversed rows are included, flagged.
- Verified against the development database. The real `login` + `loadAll()` + `derive()` ran under vite-node with the seeded data, and every row count matched the database.
  - The frontend's dashboard figures equal `/api/v1/reports/dashboard`: revenue 44,119,000, received 35,313,000, project costs 24,164,400, profit 19,954,600.
  - The frontend's receivables include other income; the backend reports that 18,000 separately.
  - Not yet checked by hand in a browser.
- Limits of phase 1:
  - **Writes are still local only.** A change made in the UI shows until the next reload or sign-in, then disappears. Phase 2 connects the actions.
  - Loading takes about 4 s for the demo data: around 90 requests, including one per project and one per supplier invoice. Bulk endpoints for those can come later if it grows.
  - The dashboard's chart months and the "Financial year 2026/27" label are still written into `dashboard.tsx`.
  - Materials get `reorderLevel: 0`, so the low-stock UI stays quiet until it is removed (owner decision).
- Seed script fix: a re-run caps an issue at the stock on hand on that date, not at today's stock. An earlier re-run had posted one backdated issue (movement 61, 800 kg steel dated 2026-09-14) against stock received on 2026-10-13. It is valid under the backend's posting-order FIFO and is left as it is.
- Tests: the new list endpoints are covered in `PayrollApiIntegrationTest` (payments, adjustments), `ExpenseApiIntegrationTest` and `ScheduleAndWriteOffIntegrationTest`. Frontend: `tsc -b` is clean, and the 19 Vitest tests pass.
  - Full backend suite on a throwaway PostgreSQL 17 container (2026-10-30): 663 tests (first written as 684), 0 failures, 0 errors, 0 skipped.

### 2026-10-30: Frontend connected, phase 2a (clients and projects save to the backend)
- Store actions that now call the API. They are `async` and throw `ApiError` with the server's message. After a save the store reads back what the server has (`loadProject`, `loadClients`, `loadActivity`); nothing is guessed locally.
  - `addClient`, `updateClient`: `POST /clients`, `PUT /clients/{id}` (with the version last read).
  - `addProject`: `POST /projects`, schedule and delay terms included. A new client is created in the same transaction (`newClient`) instead of a separate call first. The toast shows the code the server assigned.
  - `setProjectStatus`: `PUT /projects/{id}/status`. The server sets the actual completion date.
  - `uploadAgreement`: `PUT /projects/{id}/agreement` (multipart). The dropzone uploads for real; the simulated progress and the browser-only copy are gone. "Download" links to `/agreement/download` (always an attachment, so "Preview" was dropped).
  - `saveDelayTerms`, `applyDelayDeduction`, `reverseDelayDeduction`: replace the old `setDelayTerms`, which did all three locally.
  - `reviseSchedule`: `PUT /projects/{id}/schedule`. Variations are no longer part of the edit dialog: the scheduled rows must equal the contract value, as the backend requires.
  - `addVariation`: `POST /projects/{id}/variations`, from the Revenue page's "additional work" form.
- `Installment.variation` is set from the API's `kind`. The edit dialog uses it instead of the "Variation:" description prefix.
- Record versions for optimistic locking are kept in `data/api-load.ts` (`versionOf`), refreshed whenever a row is read.
- Forms disable their save button while saving and show the server's refusal as an error toast.
- Backend:
  - New `PUT /api/v1/projects/{id}/delay-terms` `{enabled, terms, version}`. It adds terms (version null), changes them, or switches them off (`enabled: false`, the row is kept).
  - Construction projects only. A stale version gets 409, and so does any change while a deduction is applied ("Reverse it before changing the terms").
  - It writes activity entries: added, updated, removed.
  - `DelayTermsService.fill` keeps only the value of the chosen rule, for both create and edit.
  - Tests in `ScheduleAndWriteOffIntegrationTest`: add, stale version, rule change clearing the old value, blocked while applied, switch off, the activity log, design project 400, unknown project 404. `DelayTermsServiceTest` constructor updated.
- Tests: full backend suite on a throwaway PostgreSQL 17 container (2026-10-30): 665 tests (first written as 686), 0 failures, 0 errors, 0 skipped. Frontend: `tsc -b` is clean and the 19 Vitest tests pass. The development backend was rebuilt, and loading after sign-in was checked against it. Saving was not tried in a browser.
- Still local until later phases: billing, receipts, write-offs, other income (2b); expenses (2c); suppliers and inventory (2d); labour and payroll (2e); commissions and settings (2f).

### 2026-10-30: Frontend connected, phase 2b (revenue saves to the backend)
- Store actions that now call the API (`async`; the server's message is shown when it refuses):
  - `billInstallment`: `POST /projects/{p}/installments/{i}/bill` (with the installment's version).
  - `recordClientPayment`: `POST .../installments/{i}/receipts`.
  - `writeOff`: `POST .../installments/{i}/write-offs`.
  - `addOtherIncome`: `POST /other-income`, then "received now" as a receipt (`POST /other-income/{id}/receipts`).
  - `recordOtherIncomeReceipt`: `POST /other-income/{id}/receipts`.
- After a save, the store reads the project (or the other income list) and the activity feed back from the server.
- `PaymentDialog` (shared by every module) awaits the save, stays open with an error toast if it fails, and disables its button while saving.
  - It makes a `requestId` each time it opens; the backend uses it so a double click or a retry is recorded once. The "Add Revenue" dialog does the same for "received now".
- Payment methods are chosen by name in the UI and sent by id; `methodId()` maps them from the settings the loader read.
- Write-off dialog and the "Bill now" confirmation are async with error toasts.
- Tests: no backend change in this step. Frontend: `tsc -b` is clean, the 19 Vitest tests pass, and the production build succeeds. Not tried in a browser.
- The other modules still use `PaymentDialog` with their local actions until phases 2c to 2f.

### 2026-10-30: Frontend connected, phase 2c (expenses save to the backend)
- Store actions now calling the API:
  - `addExpense`: `POST /expenses`, with "paid in full / part" as `initialPayment` (one `requestId` per dialog opening).
  - `updateExpense`: `PUT /expenses/{id}` with the version last read.
  - `recordExpensePayment`: `POST /expenses/{id}/payments`.
- After a save, the store reloads the expenses with their payments (`loadExpenses`) and the activity feed.
- Categories and units are chosen by name in the UI and sent by id: `categoryId(group, name)` and `unitId(name)`, mapped from the settings the loader read.
- A materials line sends quantity, unit and unit price; the server works the amount out. Machinery sends the machine type and usage dates, and electricity sends the billing period.
- `Expense` gained the raw fields (`quantity`, `unit`, `unitPrice`, `machineType`, `usageFrom`, `usageTo`, `billingPeriod`). The edit dialog fills them in, so an edit sends them back instead of clearing them. The `details` keys the loader builds now match the dialog's (`Period`, formatted unit price).
- The server's duplicate warning (same supplier and invoice number as a supplier invoice) is shown as a warning toast after the save. The expense is still saved.
- Not saved: the dialog's "Receipt or invoice" attachment. The backend has no attachment for manual expenses, so the file chip still only lives in the open dialog.
- Tests: no backend change. Frontend: `tsc -b` is clean, the 19 Vitest tests pass, and the read-only load check against the development backend still matches. Not tried in a browser.

### 2026-10-30: Frontend connected, phase 2d (suppliers and inventory save to the backend)
- Store actions now calling the API:
  - `addSupplier`: `POST /suppliers`.
  - `addSupplierInvoice`: `POST /supplier-invoices`.
    - `allocation` is `PROJECT` when a project is chosen, otherwise `STOCK`; units are sent by id.
    - "Paid now" goes as `initialPayment`, with one `requestId` per form.
    - The chosen file is then uploaded to `PUT /supplier-invoices/{id}/attachment`. The form now keeps the real `File`, not just its name and size. If only the upload fails, the invoice stays recorded and the form says so with a warning.
  - `recordSupplierPayment`: `POST /supplier-invoices/{id}/payments`.
  - `addMaterial`: `POST /inventory/materials`. The reorder level in the dialog is not sent; the backend has none (owner decision).
  - `addStockMovement`: one of `/inventory/receipts`, `/issues`, `/returns` (against the original issue) or `/adjustments`, with one `requestId` per dialog opening. The server costs it FIFO; the toast shows the server's cost.
- After a save the store reloads what changed: the suppliers, the invoices with their payments, and the inventory. A stock invoice also brings stock in, so it reloads the inventory too.
- `store/inventory.test.ts`: the posting tests now append to the local ledger through a helper with the prototype's checks (`movementError`, `replayFifo`). They still check the frontend's FIFO maths on the demo data; the store action goes to the server.
- Tests: no backend change. Frontend: `tsc -b` is clean, the 19 Vitest tests pass, and the read-only load check against the development backend matches. Not tried in a browser.

### 2026-10-30: Frontend connected, phase 2e (labour and payroll save to the backend)
- Store actions now calling the API:
  - `addEmployee`: `POST /employees`.
  - `updateEmployee`: `PUT /employees/{id}`. When the status changed, a second call goes to `PUT /employees/{id}/status`, because the backend keeps active/inactive out of an ordinary edit.
  - `saveAttendance`: `PUT /attendance/{date}` with the whole day's register. The server refuses changes to a paid (frozen) week, and the error is shown.
  - `addOvertime`: `POST /overtime`. It starts unapproved. "Recorded by" is now the signed-in user's name; the prototype had a fixed name.
  - `approveOvertime`: `PUT /overtime/{id}/approval` (approve or withdraw).
  - `addAdvance`: `POST /payroll/advances` (one `requestId` per dialog opening).
  - `setPayrollAdjustment`: `PUT /payroll/weeks/{w}/employees/{e}/adjustment`, with the adjustment's version, or null when there is none yet.
  - `recordPayrollPayments`: one `POST .../payments` per worker. "Pay all" sends them one by one; if one fails it stops, the rest are kept, and the page reloads to show what is still owed.
- After a save the store reloads labour as one bundle (`loadLabour`): employees, attendance, overtime, advances, adjustments and payroll payments. A payment can change recoveries and frozen weeks, so all of these can change.
- Versions are kept for employees, overtime and adjustments (adjustments by week and worker).
- Tests: no backend change. Frontend: `tsc -b` is clean, the 19 Vitest tests pass, and the read-only load check against the development backend matches. Not tried in a browser.
- Known gap: the payroll page still works out what each week owes in the browser (`lib/finance.ts`). If that ever differs from the backend's figure (for example a frozen week whose rates changed later), the server refuses an overpayment and the error is shown. Phase 3 should read the week from `GET /payroll/weeks/{w}` instead.

### 2026-10-30: Frontend connected, phase 2f (commissions and settings save to the backend); phase 2 complete
- Commissions:
  - `saveCommissions`: `PUT /projects/{id}/commissions` with the project's whole set, each existing row carrying its id and version.
  - Confirming over the policy is sent as `acceptPolicyException`; this is the "confirm as an exception" the dialog already warns about.
  - Revising a confirmed row is refused by the server ("void it and add the corrected one") and the message is shown. The void-and-replace flow is phase 3.
  - `recordCommissionPayment`: `POST /commissions/{id}/payments`.
  - The store reloads commissions with their payments, plus the activity feed.
- Settings (`updateSettings`):
  - The company profile and supplier credit days go to `PUT /settings/company`.
  - The commission policy goes to `PUT /settings/commission-policy`. It is Super Admin only, so an Admin sees the permission message.
  - Wages go to `PUT /settings/wages`. Each sends the version from the last read.
  - Expense categories, units and payment methods are saved as differences: `POST` for each added name and `DELETE` for each removed one. The server refuses removing one that is locked or in use, and the settings are always read back afterwards, so the page shows what really happened.
  - Revenue categories are fixed in the backend. A change to them is refused with a message; making that section read-only is phase 3.
- Every store action now saves through the API. The local-only `log` action and `uid` are gone. `reset()` (the demo data) stays for the unit tests.
- Tests: no backend change. Frontend: `tsc -b` is clean, the 19 Vitest tests pass, and the read-only load check against the development backend matches. Not tried in a browser.
- Next (phase 3, UI alignment):
  - Remove the low-stock UI.
  - Payroll: paid weeks read-only, a "Record correction" action, and owed figures read from `GET /payroll/weeks/{w}`.
  - Commission void-and-replace flow.
  - Void, cancel and reverse actions in the UI.
  - Revenue categories read-only.
  - Dashboard months and financial year label taken from the data.
  - A Super Admin user-management screen.
  - Manual expense attachment (backend support or remove the field; owner to choose).

### 2026-10-30: Phase 3a, payroll figures from the backend, frozen weeks and corrections in the UI
- Backend: `GET /api/v1/payroll/frozen-weeks?from&to` lists the worker-weeks already paid or closed (`{employeeId, weekStart}`), new `FrozenWeek` DTO. A voided payment leaves its week frozen, and the list says so. Covered in `PayrollApiIntegrationTest`.
- Payroll page (`pages/labour/payroll.tsx`):
  - The selected week's figures come from `GET /payroll/weeks/{w}`: KPIs, the table, and each worker's gross, deductions, corrections, owed, paid, remaining and status. A paid week is frozen on the server with the rates of the day, so the page no longer works these out in the browser. Which weeks exist still comes from the local attendance.
  - The week is read again after any labour save.
  - Frozen workers show a lock. Their sheet offers "Record correction" instead of "Adjust deductions".
  - The correction dialog takes owed more or owed less, an amount, a date, the project (or company) the labour cost goes to, and a reason. One `requestId` per opening.
  - The worker sheet (`GET .../employees/{e}`) lists payments and corrections, each with a Void action that asks for a reason. Voided ones stay, struck through.
  - "Close" or "Close week" appears for a week the advance recovery swallows (nothing to pay).
  - Pay and "Pay remaining" respect the server's `payable` and `blockedReason`.
  - The labour cost by project comes from the server's split, with the company share shown as "Company (no project)".
- Attendance: a frozen worker's row is read-only (lock icon). "Mark all present" skips frozen rows. A frozen row with no mark is not sent, because the server would read that as a change.
- Overtime: entries in a frozen week show "Week paid" instead of Approve or Undo. Recording overtime in a frozen week is blocked with a message pointing to corrections.
- Store: `frozenWeeks` (loaded with the labour bundle and the first load), plus `recordPayrollCorrection`, `voidPayrollCorrection`, `voidPayrollPayment` and `closePayrollWeek`.
- Hooks subscribe to several store slices through `useShallow`, so zustand v5 doesn't loop on a new array each render.
- Verified against the development backend (read-only): the week response has every field the page maps, 251 frozen worker-weeks are listed, the worker detail returns payments and corrections, and the full load still works. The development backend was rebuilt for the new endpoint. Frontend: `tsc -b` is clean and the 19 Vitest tests pass. Not tried in a browser.
- Full backend suite on a throwaway PostgreSQL 17 container (2026-10-30): 665 tests (first written as 686), 0 failures, 0 errors, 0 skipped. The frozen-weeks checks were added to an existing test, so the count didn't change.

### 2026-10-30: Phase 3b, void, reverse and cancel actions in the UI
- Shared `components/app/void-dialog.tsx`, moved from the payroll page. It asks for a reason; the record stays in the history and stops counting. The verb and description change for Reverse and Cancel.
- New store actions, each sending the version last read and the reason, then reloading what changed:
  - `voidReceipt`: `POST /projects/{p}/receipts/{id}/void`.
  - `reverseWriteOff`: `POST /projects/{p}/write-offs/{id}/reverse`.
  - `voidExpensePayment`: `POST /expenses/{e}/payments/{id}/void`.
  - `voidSupplierPayment`: `POST /supplier-invoices/{i}/payments/{id}/void`.
  - `cancelSupplierInvoice`: `POST /supplier-invoices/{i}/cancel`.
  - `voidAdvance`: `POST /payroll/advances/{id}/void`.
  - `voidCommissionPayment`: `POST /commissions/{c}/payments/{id}/void`.
- Where the buttons are:
  - Project → Payments: in the payment history, Void on each receipt and Reverse on each write-off.
  - Expense details: Void on each payment.
  - Supplier invoice table (expanded row): Void on each payment. "Cancel invoice" on project invoices with no payments; stock invoices are already in the stock ledger.
  - Advances: Void on advances nothing has been deducted from yet.
  - Commissions → Payments, and the project's Commissions tab: Void on each payment.
- The loader now keeps versions for receipts, write-offs, expense, supplier, other-income and commission payments, supplier invoices and advances.
- The server's rules still decide; its refusal is shown. Examples: an advance already recovered, a payment in a frozen payroll week, an invoice with payments.
- Not in the UI yet: voiding an other-income receipt (no page lists the receipts) and voiding or replacing a commission arrangement (phase 3c).
- Tests: no backend change. Frontend: `tsc -b` is clean, the 19 Vitest tests pass, and the read-only load check against the development backend matches. Not tried in a browser.

### 2026-10-30: Phase 3c, void and replace a confirmed commission
- The backend never changes a confirmed commission. It is voided (reason required, payments voided first), and the corrected one is added as a new arrangement. Before this, the UI let a confirmed rate be edited and the save was then refused.
- Project → Commissions:
  - In the edit table ("Revise"), confirmed rows are read-only: name, role and rate are disabled.
  - Their remove button is replaced by Void, and a note explains the void-then-add flow.
  - Drafts can still be removed or changed freely.
  - In the confirmed view each row has a Void button next to Pay.
  - Voiding uses the shared `VoidDialog`. It sends the arrangement's version, then reloads the commissions and the activity feed.
  - The server's refusal is shown, for example "Void its payments first".
- Store: `voidCommission` → `POST /commissions/{id}/void` `{reason, version}`.
- Tests: no backend change. Frontend: `tsc -b` is clean and the 19 Vitest tests pass. Not tried in a browser.

### 2026-10-30: Phase 3d, low-stock UI removed, revenue categories read-only
- Owner decision (2026-10-24): no reorder levels. Removed:
  - `Material.reorderLevel` from the type, the loader and the demo data;
  - the "Low or out of stock" KPI;
  - the "Reorder soon" banner;
  - the "Stock levels" filter;
  - the "Reorder level" column;
  - the "Reorder level" field in Add material;
  - the inventory count in the sidebar.
- A material's status is now "In Stock" or "Out of Stock" (`lib/finance.ts`); "Low Stock" is gone from the badge map.
- Settings → Categories: revenue categories are shown as fixed chips (they follow the project types, plus Other Income). The section saves only the expense categories. The project statuses note now says they are fixed instead of "come with the backend".
- The "Planned Frontend Changes" checklist is ticked for these two items and for the payroll freeze (3a).
- Tests: no backend change. Frontend: `tsc -b` is clean and the 19 Vitest tests pass. Not tried in a browser.

### 2026-10-30: Phase 3e, dates and year labels from the data instead of the code
- The prototype had 2026 dates written into the pages. They now come from today (Colombo) and the financial year start in Settings:
  - Date-range filters (`components/app/filters.tsx`): `RANGE_PRESETS` became `rangePresets()`. It gives this financial year (with its label, e.g. 2026/27), this month, the last 3 months, and the year's quarters that have started.
  - Dashboard: the monthly chart starts at the financial year start; the title shows the year label and its first day; "payables due" covers the next 14 days.
  - Topbar notifications: "due soon" means within 3 days.
  - Inventory "Issued to projects", Advances "Advances given" and Employees "Days in" use the current month.
  - Projects list: the year filters use this year and next.
  - Reports: the page description shows the financial year label. The income chart stops at today. The labour report opens on the current month.
  - Create Project: the code preview follows the server's rule, `PRJ-<start year>-<next number in that year>`. The server still assigns the real one.
  - Settings: "Financial year starts" shows the server's value (read-only).
- `lib/dates.ts`: `monthStart`, `monthEnd`, `addMonths`, `fyLabel`, with `lib/dates.test.ts` (3 tests: month ends including leap years, crossing year ends, the year label).
- Tests: no backend change. Frontend: `tsc -b` is clean and the Vitest suite passes (22 tests, 19 before). Not tried in a browser.

### 2026-10-30: Phase 3f, user management screen and change password
- `pages/users.tsx` (`/users`, "Users" in the sidebar for Super Admins only; an Admin who opens the URL sees "Super Admins only", and the server answers 403 anyway):
  - Lists the users: name, username, email, role, active or inactive, and the date added.
  - Add user: name, username, email, password (at least 12 characters) and role. They sign in with the username or the email.
  - Edit name and email: `PUT /users/{id}` with the version.
  - Make Super Admin or Admin: `PUT /users/{id}/role`.
  - Activate or deactivate: `PUT /users/{id}/status`, after a confirmation that explains it signs the person out.
  - Reset password: `PUT /users/{id}/password`.
  - The UI doesn't offer what the server would refuse: changing your own role or status, resetting your own password here, or demoting or deactivating the last active Super Admin. The server still checks all of these.
  - Users are never deleted, only deactivated.
- `components/app/change-password-dialog.tsx`: "Change password" in the account menu for every user. It sends `PUT /auth/me/password` `{currentPassword, newPassword}`. This session stays signed in and the others end; a wrong current password counts toward the sign-in limit.
- `StatusBadge` gained "Super Admin" and "Admin".
- Verified against the development backend (read-only): `GET /users?sort=displayName` lists both users with name, username, email, role, status and version. Frontend: `tsc -b` is clean and the 22 Vitest tests pass. Not tried in a browser.
- Phase 3 is complete except the manual expense attachment, which waits for the owner's choice (backend support or remove the field).

### 2026-10-30: Manual expense attachments (owner's choice: keep the field and store the file)
- The "Receipt or invoice" field of the expense dialog was never saved. The owner chose to keep it and store the file. V1 already had the columns (`expenses.attachment_storage_key`, `attachment_filename`, `attachment_size`), so no migration was needed.
- Backend:
  - New `ExpenseAttachmentService`, the same pattern as supplier invoice attachments: the private document store, the agreement upload checks, the file stored first and then the row, and the old file deleted only once the new one is saved.
  - `PUT /api/v1/expenses/{id}/attachment` takes a multipart PDF in `file`, replaces any earlier one, returns the expense detail and writes a `DOCUMENT` activity entry.
  - `GET /api/v1/expenses/{id}/attachment/download` always serves an attachment (404 when there is none).
  - `Expense.attach(...)`. `ExpenseResponse` gained `attachmentFilename` and `attachmentSize`.
  - PDF only, like agreements and supplier invoices: the store checks the `%PDF-` signature. Accepting phone photos (JPEG/PNG) would mean changing the shared store, which was not done.
- Frontend:
  - The dialog keeps the picked `File` and uploads it after the expense is saved (new or edited). If only the upload fails, the expense stays saved and a warning says so.
  - An already saved attachment can be replaced, but not removed (there is no remove endpoint). The picker accepts PDF only.
  - The expense details show the file name as a Download link. The loader maps the attachment.
  - Store action `uploadExpenseAttachment`.
- Tests:
  - `AgreementStorageIntegrationTest.anExpenseCanCarryAPrivateReceiptPdf`: no file gives 404; a non-PDF gives 400; then upload, download, the activity entry, replacing removes the old file, and an unknown expense gives 404.
  - `ExpenseControllerWebTest` mocks the new service.
  - Frontend: `tsc -b` is clean and the 22 Vitest tests pass.
- Tests, backend: full suite (2026-10-30), run on a copy of `backend/` outside the workspace on a throwaway PostgreSQL 17: **666 tests, 0 failures, 0 errors, 0 skipped**.
  - Two runs in `backend/` itself failed with `NoClassDefFoundError` (`PayrollCalculator$1`, `CommissionCalculator$1`, `DelayTermsRules$1`, `DelayDeductionCalculator$1`). VS Code's Java builder (Eclipse compiler) writes to the same `target/classes` and deletes the `$1` classes javac makes for enum switches, even right after `mvn clean`. It is an environment clash, not a code fault.
  - Keep VS Code's Java auto-build off while running Maven tests (`"java.autobuild.enabled": false`), or run them on a copy.
- **Correction of earlier counts:** the totals recorded today (681, 683, 684 and 686) were too high by 21. They summed every report file in `backend/target/surefire-reports`, including two stale reports from tests that no longer exist under those names: `clients.validation.OptionalEmailValidatorTest`, moved to `common.validation` (13), and `inventory.service.StockCostingTest`, removed earlier (8). Those entries now carry the corrected numbers. Every class in those runs passed, so the pass/fail result is unchanged.

### 2026-10-30: Receipt photos (JPG, PNG) for expenses and supplier invoices
- Owner's request: bills and receipts can be phone photos, not only PDFs.
- `agreements/storage/DocumentType` (new): PDF, JPEG and PNG, each with its file signature, the name extensions it accepts (`.pdf`; `.jpg`/`.jpeg`; `.png`) and its media type. `PDF_ONLY` and `RECEIPTS` are the two allowed sets.
- `AgreementFileStore`:
  - `store(in, max)` is unchanged for agreements (PDF only).
  - The new `store(in, max, allowed)` reads the type from the first bytes and refuses anything outside the allowed set: "The file is not a valid PDF." or "The file must be a PDF, JPG or PNG.". It stores the file under `<uuid>.pdf|jpg|png`.
  - The key pattern allows the three extensions; keys are still only ones the store creates.
- `AgreementFiles.validateReceiptUpload`: the name must be `.pdf`, `.jpg`, `.jpeg` or `.png`, and the declared content type must match it. It returns the type, and the service then requires the bytes to be exactly that type, so a photo renamed `.pdf` is refused. Shortened long names keep any short extension.
- Expense and supplier invoice attachments use it. The download content type comes from the stored type (`image/jpeg`, `image/png` or `application/pdf`), always as an attachment, never shown inline. "No PDF is attached" became "No file is attached".
- **Agreements stay PDF only** (signed contracts); an image upload there is refused.
- Frontend: the expense dialog ("Attach file", with a hint about phone photos) and the supplier invoice form accept PDF, JPG and PNG.
- Tests:
  - `AgreementFileStoreTest`: photos stored under their real type; agreements still refuse a JPEG; a JPEG held to PDF is refused; a GIF is refused with the combined message; nothing is left on disk.
  - `AgreementFilesTest`: receipt name and type rules, plus extension kept when shortening.
  - `AgreementStorageIntegrationTest.aReceiptPhotoCanBeUploadedButNotPassedOffAsAPdfOrAnAgreement`.
  - These classes pass on the isolated copy. Frontend: `tsc -b` is clean and the 22 Vitest tests pass.
- Two test-cleanup bugs, found and fixed on the way:
  - My new attachment tests in `AgreementStorageIntegrationTest` left their expenses behind. That class isn't transactional and cleans up by name. They are now `AgTest…` rows, deleted in `@AfterEach` with their activity entries. These leftovers caused the `ExpenseApiIntegrationTest` count failures in the earlier full runs.
  - `RevenueConcurrencyIntegrationTest` cleanup failed whenever a race had produced a write-off: V7's `trg_receivable_write_offs_guard` refuses deletes. Its cleanup now lifts that trigger only around the delete, like the other concurrency tests. Its leftovers had broken `ProjectRepositoryTest` and its own next `setUp`.
- Tests, backend: full suite on an isolated copy of `backend/` with a throwaway PostgreSQL 17 (2026-10-30): **671 tests, 0 failures, 0 errors, 0 skipped**.
- The development backend was not rebuilt for this change yet, because Docker Desktop was paused.

### 2026-10-30: Project timeline order and duplicates fixed
- Two faults in the project page's Activity timeline (`lib/finance.ts` `projectActivity`):
  - **Duplicates.** It built entries from the records (billing, receipts, write-offs, expenses, commission payments, the agreement, "Project created") *and* added the backend's activity log. The backend logs all of those itself, so each event appeared twice, the two copies with different dates (business date versus the date recorded).
  - **Order within a day.** Entries with the same date were ordered by id as text and ascending: oldest first, and "100" before "99".
- Now the timeline is the backend's activity log only, one entry per event. It is ordered by date (newest first), then by when the entry was recorded (`createdAt`, newly mapped by the loader onto `ActivityEntry`), then by the numeric id.
- Tests: `lib/finance.test.ts` (2) covers newest first, a later recording first within a day, other projects left out, and the numeric id tie-break.
  - Against the development data, PRJ-2026-001 shows 46 entries for 46 log rows, from the delay deduction and the status change at the top down to "Project created" at the bottom.
  - Frontend: `tsc -b` is clean and the Vitest suite passes (24 tests). No backend change.

### 2026-10-31: Add a new supplier from the expense form
- In the Record Expense dialog, the Supplier select (shown for a materials line) now has a "New" button. It opens the same "Add supplier" dialog as the Suppliers page. The supplier is saved through `POST /suppliers` and selected in the expense straight away, so a purchase from a shop that isn't on the list no longer means leaving the form.
- `components/app/supplier-dialog.tsx`: the dialog moved out of `suppliers-page.tsx` so both places use the same one. It gained `onCreated(id)` and an optional `defaultName`. The Suppliers page uses it unchanged.
- Tests: no backend change. Frontend: `tsc -b` is clean and the 24 Vitest tests pass. Not tried in a browser.

### 2026-10-31: Reports download as PDF and Excel
- Each report's Export menu (income, expenses, project profitability, client receivables, supplier payables, labour payments) now offers PDF, Excel (.xlsx), Download CSV and Copy as CSV. The old "Excel export isn't connected yet" placeholder is gone.
- The files are built in the browser (`lib/export.ts`) from the same rows the CSV uses, so they match what is on screen and respect the page's filters. There is no backend endpoint for this.
  - Excel (`exceljs` 4.4.0): a bold, frozen header row with an autofilter. Amounts are stored as numbers formatted `#,##0.00`. Day and hour counts are plain numbers. Column widths fit the content.
  - PDF (`jspdf` 4.2.1 + `jspdf-autotable` 5.0.9): A4 landscape. The company name, report title, the date range (for income, expenses and labour) and the generation date are at the top. Numbers are right-aligned and every page is numbered.
  - Both libraries load only when someone exports. They are separate build chunks and are not in the main bundle.
- Known limits:
  - jsPDF's built-in font has no Sinhala or Tamil glyphs, so such text would not print correctly in the PDF. Excel and CSV are not affected.
  - `exceljs` brings in `uuid` 8.3.2, which `npm audit` flags (moderate, `uuid <11.1.1`). The existing `@remix-run/router` advisory comes from react-router and is unrelated.
- Verified: `tsc -b` is clean and the 24 Vitest tests pass. `vite build` succeeds. A sample .xlsx and .pdf were generated in Node and read back to check the number formats and layout. Not tried in a browser.
- The frontend Docker container needs a rebuild (`docker compose up -d --build -V frontend`) to pick up the new npm packages. `-V` replaces its anonymous `node_modules` volume, which would otherwise keep the old packages. It does not touch the database volume.

### 2026-10-31: Accrual and cash basis for expenses and profit
- Until now the figures were accrual only. Revenue counted when billed, and costs counted when recorded, paid or not. Money received showed next to revenue, but nothing showed money paid out or a cash profit.
- An **Accrual / Cash** toggle (`components/app/basis-toggle.tsx`) now sits on four screens: the Profitability report, the Expense report, the Dashboard (header) and the project page ("Costs and result" card). The browser remembers the last choice. Accrual stays the default, and its figures are unchanged.
- Cash basis rules (`lib/finance.ts`):
  - **Cash ledger (`cashLines`):** one row per payment made, dated on the day it was paid. This covers:
    - expense payments;
    - supplier payments (a payment for a stock invoice is company-level "Stock purchases");
    - wage payments;
    - salary advances, on the day they were given;
    - commission payments.
  - **Stock issued to a project** counts towards that project at FIFO issue cost, flagged `fromStock` (owner decision). No new cash moves; the stock was already paid for.
    - It is left out of company totals (`totals.cashPaidOut`), so the stock purchase is counted once.
    - The Expense report shows these issue rows only when a single project is selected.
  - **Wage payments** are split over the projects the worker was on that week, in proportion to the wages earned on each. The last share takes the rounding remainder.
  - **Advances** stay company-level. The advance plus the net wage paid equals the gross, so nothing is counted twice.
  - **Project cash profit** = client payments received − the project's cash lines. Unpaid bills, unpaid wages, committed but unpaid commission and amounts clients still owe are left out.
  - `projectFigures(p, basis)` gives a project's figures on either basis for the screens.
- Not changed: revenue recognition (billed installments), the backend and its `/reports` endpoints. The cash figures are computed only in the frontend, from data it already loads.
- Tests: `lib/finance.test.ts` has 4 new tests:
  - money received and paid, with stock issued at cost;
  - the stock purchase counted once in company cash;
  - a wage payment split across projects, with the advance kept at company level;
  - commission counted when paid, not when confirmed.
- Verified: `tsc -b` is clean and 28 Vitest tests pass. Not checked against the development data (the stack was down) and not tried in a browser.
- Dashboard tiles: the top row is now Active Projects, Contract Value and **Total Clients** (with how many have active projects). The bottom row is **Total Revenue**, **Total Expenses** and Profit, and all three follow the basis toggle.
  - Revenue: billed revenue plus other income on accrual, and money received on cash. The amount still owed and the amount overdue moved into the accrual revenue caption.
  - Expenses: all expense lines, project and company-level, on accrual, and `totals.cashPaidOut` on cash. Cash includes stock purchases and salary advances.
  - Profit is still project profit, so company-level expenses are not deducted from it. That is why revenue − expenses on the dashboard does not equal the profit tile.
  - Checked against the development data: 9 clients. Accrual revenue is 54.2M and cash revenue 45.4M. Accrual expenses are 28.9M and cash expenses 25.2M. Wage payments split across projects add back exactly to the payments recorded.
- Active projects on the dashboard now means **In Progress only**; On Hold projects are no longer counted (owner decision). This applies to the Active Projects tile, its per-type caption, Contract Value (active) and the "with active projects" count under Total Clients. The attendance and stock-issue project pickers still offer On Hold projects.
- The dashboard no longer mentions the financial year (owner decision). Its figures were always all-time, so the old "Financial year … to date" header was misleading.
  - The header now reads "All figures to date · as of <today>".
  - Revenue by Type says "Revenue recorded to date".
  - The monthly revenue vs expenses chart shows the last 12 months instead of starting at the financial year start.
  - The financial year setting itself is unchanged.

### 2026-10-31: Overdue counts from the billing day, with no separate due dates
- Owner decision: revenue is recorded when billed (accrual), whether paid or not, and counted as cash once paid (both unchanged). Revenue no longer has a separate due date. Anything still unpaid after the day it was recorded is overdue.
- Backend:
  - `ReceivableCalculator` now marks an installment `OVERDUE` when it is billed, still owed and `billed_on < today`. `daysOverdue` = today − `billed_on`. Ageing buckets, report sorts and totals follow from this unchanged.
  - Other income (`OtherIncomeService`) is overdue when still owed after `income_date`. Its `due_date` column stays nullable and is still accepted, but it no longer affects anything.
  - Schedule validation messages now say "planned date".
- Frontend:
  - `lib/finance.ts` uses the same rule. A billed installment with no `billedOn` falls back to the planned date.
  - The installment schedule's date is now the **Planned date** (schedule editor, the Revenue page's upcoming list, the bill picker, client history).
  - Receivable lists show **Billed on**: the receivables table, the dashboard's overdue list, the project payments table, the notifications menu and the receivables CSV/PDF/Excel. "N days past <due>" became "N days since billed on <date>".
  - Add Revenue no longer asks for a "Payment due" date. A variation sends its billing date as the stored date.
- No migration: `installments.due_date` stays `NOT NULL` and holds the planned date.
- Tests:
  - backend `ReceivableCalculatorTest`: billed today is not overdue, every day after billing is overdue and counted, and an unbilled installment is never overdue whatever its planned date;
  - frontend `lib/finance.test.ts` (2 new tests): installments and other income.
  - Six backend integration tests were rewritten for the new rule (other income, the revenue API, receivables ageing, the schedule message). One new test checks that income recorded today is still Due.
- Verified:
  - backend: 673 tests, all passing on the isolated copy;
  - frontend: `tsc -b` is clean and 30 Vitest tests pass;
  - the dev backend was rebuilt.
  - Not tried in a browser.

### 2026-10-31: Revenue is shown after write-offs
- Owner decision: an amount written off as bad debt comes out of billed revenue.
- `lib/finance.ts`:
  - New `totals.revenueNet` = revenue recorded − written off.
  - The monthly series takes each installment's write-offs off its billing month.
  - The stored facts are unchanged: billing and write-offs are still separate records. A write-off is still not money received, and receivable = billed − received − written off as before.
- Screens now show revenue after write-offs:
  - the dashboard Total Revenue tile on accrual (and the accrual figure in its cash caption);
  - Revenue by Type and the collection progress bar;
  - the Reports page summary;
  - the Income report rows, totals, chart and exports (the column is "Revenue recorded, after write-offs");
  - the Revenue Recorded tile on the project and client pages, with an "After N written off" caption.
- The project page's "Client money" card still lists billed revenue and then subtracts the write-off, so the steps stay visible.
- Profitability accrual revenue was already net of write-offs, so it is unchanged.
- Backend `/reports/dashboard` still returns `revenueRecorded` and `writtenOff` as separate fields. It was not changed.
- Development data: 54,200,000 recorded − 4,950,000 written off = 49,250,000 revenue.
- Tests: 1 new test in `lib/finance.test.ts`. `tsc -b` is clean and 31 Vitest tests pass. Not tried in a browser.

### 2026-10-31: Dashboard charts follow the Accrual / Cash toggle
- **Monthly Revenue vs Expenses**, last 12 months:
  - accrual: revenue recorded after write-offs against expenses recorded;
  - cash: money received (client payments and other income receipts) against money paid out (the cash ledger without stock issues), by the month the money moved.
  - The legend and description change with the basis.
- **Revenue by Type**: revenue after write-offs on accrual, and money received per project type plus other income receipts on cash.
- Project Profitability already followed the toggle. The collection progress bar compares received with recorded, so it shows the same on both bases.
- `lib/finance.ts`: the monthly series gained `paid`. `received` now includes other income receipts. Nothing else read `received`.
- Tests: 1 new test, 32 Vitest tests pass. `tsc -b` is clean. Not tried in a browser.

### 2026-10-31: Project overview shows part-payments on the schedule
- Reported: a 75,000 overdue installment was part-paid (5,000), and the project's Payment schedule card still showed 75,000 "Overdue" with no sign of the payment.
  - The figures were right. 70,000 is still owed after the billing day, so it stays overdue, the same in the backend (`OVERDUE`, received 5,000, outstanding 70,000).
  - The overview card didn't show it, and it still showed the planned date.
- Fixed (`pages/projects/project-detail.tsx`):
  - The Payment schedule card now shows "N paid · N written off · N left" under a part-settled installment. The amount left is red when it is overdue.
  - Billed installments show their billing date. Unbilled ones show the planned date marked "planned".
  - The Next due / Overdue box shows "billed <date>" or "planned <date>" instead of the planned date for everything.
- Client page history: a billed row's detail said "Planned <date>" and now says "Revenue recorded".
- Checked the other places that show installment status: the Payments tab table, the receivables table, the dashboard overdue list, notifications, the Revenue page and the reports. They already show received and remaining amounts and the billing date.
- `tsc -b` is clean and 32 Vitest tests pass. Not tried in a browser.

### 2026-10-31: Reminder when an installment's planned date passes unbilled
- The notification bell now lists installments that are not billed yet and whose planned date has passed: "<project> — <installment> not billed", with the amount, the planned date and how many days ago.
  - Up to 4 are listed, longest late first. All of them count in the bell badge. Clicking one opens the project's Payments tab.
  - Only a reminder. The installment isn't revenue and isn't overdue until it is billed.
  - On Hold and Cancelled projects are skipped. Planned today is not late yet.
- Worked out in the browser (`billingLate()` in `lib/finance.ts`, used by `components/app/topbar.tsx`). No backend, email or SMS notification.
- Tests: 2 new Vitest tests (34 pass). `tsc -b` is clean. Not tried in a browser.

### 2026-10-31: Late billing in yellow, overdue in red
- An unbilled installment past its planned date now shows in yellow wherever the schedule appears. Billed and unpaid after the billing day stays red "Overdue".
  - The badge says "Billing Late" (warning tone) instead of "Upcoming". The planned date is yellow with "N days late".
  - Screens: the project overview Payment schedule card (the description also counts "N not billed on time"), the Next due box ("Not billed on time"), the project Payments tab, and the Revenue page Upcoming list.
  - The bell already used a red icon for overdue and a yellow icon for late billing.
- `InstallmentView.daysLate` holds the days past the planned date. It is 0 when billed, when not late, or on On Hold / Cancelled projects. `scheduleStatus()` gives the badge word.
  - The real status stays `Upcoming`, so the counts, revenue and overdue figures don't change.
- Tests: the billing-late Vitest test also checks the badge words (34 pass). `tsc -b` is clean. Not tried in a browser.

### 2026-10-31: Project warnings split into red and yellow
- Each project's "needs attention" items now have a tone (`ProjectFinance.attention` is `{ text, tone }[]`).
  - Red (`danger`): money billed and unpaid after the billing day ("LKR N overdue").
  - Yellow (`warning`): everything else, plus a new item "<installment> not billed · planned date passed" (or "N installments not billed").
- Project overview: a red "Overdue" banner and a separate yellow "Needs attention" banner. Before, the overdue amount sat in the yellow banner.
- Payments tab: a yellow banner under the red overdue one lists unbilled installments past their planned date, with a "Bill now" button.
- Dashboard "Needs attention" list colours by tone instead of matching the word "overdue".
- Fixed the wording "Due for completion 0 days from now". It now says "Due for completion today" or "in N days".
- Tests: 1 new Vitest test (35 pass). `tsc -b` is clean. Not tried in a browser.

### 2026-10-31: Billed and unpaid is overdue from the billing day
- Owner decision: no separate "due" stage. An installment billed today and not paid is already overdue (0 days). The same goes for other income recorded today.
  - Backend: `ReceivableCalculator` uses `billed_on <= today`; `OtherIncomeService` uses `income_date <= today`.
  - Frontend: `installmentStatus` and the other income status in `lib/finance.ts` follow the same rule.
  - So "Cash to collect" and "Cash overdue" on the dashboard are now the same figure, and the dashboard shows it once.
- Wording: "0 days since billed" now reads "billed today". The Payments tab says "unpaid since today". The bill dialog no longer says "overdue if still unpaid tomorrow". Captions read "Billed and still unpaid". The ageing bucket is "0–30 days".
- `BILLED` / `PARTIALLY_PAID` (backend) and `Due` / `Partially Paid` (frontend) stay in the code but only apply to a future billing date, which the app never sets.
- Tests: backend unit and integration tests updated (673 pass); Vitest updated (35 pass). `tsc -b` is clean. The dev backend container was rebuilt. Not tried in a browser.

### 2026-10-31: Notification bell: newest first, mark as read, scrolling
- The bell list now comes from `lib/notifications.ts` (`notifications()`). It covers:
  - overdue installments;
  - installments past their planned date and not billed;
  - supplier and expense payments due within 3 days;
  - overtime awaiting approval.
- Order: newest first, by the day each item came up.
  - Overdue: the billing day.
  - Billing late: the day after the planned date.
  - A payable: 3 days before it is due.
  - Overtime: the latest entry.
  - Same-day items keep money owed on top.
- All items are listed (no more top-4 limit) in a scrolling panel (`max-h: min(28rem, 70vh)`). Each item shows its date.
- Read state:
  - "Mark all as read" clears the badge. Clicking an item marks it read and opens it.
  - Unread items have a dot and bold title. The badge counts unread only.
  - Kept in this browser's localStorage per user (`sk.notifications.read.<userId>`, guarded with try/catch). It is not shared across devices.
  - An item stays read while its cause lasts. A new overtime entry or a new due date brings it back as unread.
- The bell moved into `components/app/notifications-menu.tsx`.
- Tests: 2 new Vitest tests (37 pass). `tsc -b` is clean. Not tried in a browser.

### 2026-10-31: Client page shows total projects
- The client detail KPI strip has a new first tile, "Total Projects": the count of the client's projects, with "N in progress" underneath. The other five tiles moved one place right; the strip now lays out 6 across on wide screens.
- `tsc -b` is clean. Not tried in a browser.

### 2026-10-31: Clients receivables tab counts overdue items
- The first tile on Clients → Receivables showed "Outstanding", which is now always the same amount as "Overdue" (unpaid is overdue from the billing day).
  - It now shows "Overdue Items": the number of overdue installments, with "Across N clients" underneath.
  - The Overdue amount tile next to it is unchanged.
- `tsc -b` is clean. Not tried in a browser.
- Same tab: the third tile, "Partially paid" (what was still owed on part-paid installments), is replaced by "Received".
  - It shows the amount already paid on installments that are still owed (live: 2,615,000 across 4 installments).
  - It sits next to the overdue amount: paid so far versus still owed.

### 2026-10-31: Project codes are never cut off
- `CellMain` (`components/app/data-table.tsx`) has a `code` prop. The code (project code or SKU) never shrinks or wraps. Only the text after it ("· project name") is cut with "…" when the column is narrow. `Mono` is now `whitespace-nowrap` everywhere.
- Moved to it: project lists, client page projects, dashboard needs-attention list, receivables table, Revenue page (records and upcoming), inventory SKUs, reports (by-project tables, profitability, write-offs).
- The Clients list "Projects" column wraps the list of codes onto more lines instead of cutting it.
- `tsc -b` is clean and 37 Vitest tests pass. Not tried in a browser.

### 2026-10-31: Every dashboard tile explains itself
- "Active Projects" and "Total Clients" now have an info (i) icon. Hovering or focusing it explains what the number counts. The other four tiles already had one, in both the Accrual and Cash views.
- `tsc -b` is clean. Not tried in a browser.

### 2026-10-31: Info hints on every KPI tile
- Every KPI tile in the app now has the (i) icon with a one-line explanation of what it counts. That is 88 tiles across clients, client detail, project detail, revenue, expenses, commissions, suppliers, supplier detail, inventory, employees, advances, payroll and every report (both bases where a report toggles).
- The text only describes the existing formulas. No figures changed.
- Found while doing this, not changed: the Suppliers page tile "Purchases this year" adds up every supplier invoice ever recorded, not only this year. Its hint says what it really counts.
- `tsc -b` is clean and 37 Vitest tests pass. Not tried in a browser.

### 2026-10-31: Light mode page background is light grey
- Owner request, with a reference screenshot: in light mode the page and the cards were both pure white, so cards didn't stand apart the way they do in dark mode.
- `--background` (light only) changed from `oklch(1 0 0)` to `oklch(0.967 0 0)`. Cards and popovers stay white. Dark mode is unchanged.
- The topbar search box uses `bg-card` so it stays white on the grey bar.
- This changes a theme token in `index.css` (owner-approved). `tsc -b` is clean. Not tried in a browser.
- Follow-up: with the grey page, the filter boxes and other see-through controls blended into the page. In light mode these now have a white background (`bg-card`); dark mode keeps `dark:bg-input/30` as before:
  - inputs, selects, textareas, date pickers, amount inputs and search filters;
  - outline buttons;
  - the active pill tab;
  - side sheets.
- The pill tab track is `bg-black/5` in light mode so it shows on both the grey page and white cards (`bg-muted` in dark, unchanged).

### 2026-10-31: Sidebar scrollbar shows on hover
- The navigation sidebar hid its scrollbar completely. It now shows a thin scrollbar while the pointer is over it (or keyboard focus is inside it), and hides it again otherwise.
- New `scroll-on-hover` utility in `index.css` (standard `scrollbar-width` / `scrollbar-color`). The scrollbar space stays reserved, so the menu doesn't shift when it appears.
- `tsc -b` and `vite build` are clean. Not tried in a browser.

### 2026-11-01: Reports page note removed
- Removed the out-of-date line "Every report can be exported to CSV. Excel export arrives with the backend." from the Reports page. Every report already exports to PDF, Excel, CSV and Copy.

### 2026-11-01: Swept the UI for out-of-date wording
- Status filters offered states that no longer happen for client money (unpaid billed money is overdue from the billing day), so choosing them showed nothing:
  - Clients → Receivables offered "Due" and "Partially Paid" and now has "Unpaid (all overdue)", "Part-paid" (some paid, some still owed), "Paid" and "Written Off";
  - the Revenue page offered "Due" and "Partially Paid" and now has "Overdue", "Paid" and "Written Off".
- Ageing: dropped the "Not yet due" bucket, which is always empty now. Clients and Reports ageing show four buckets: 0–30, 31–60, 61–90 and over 90 days.
- Reports page description: "Cash-flow reports are not part of this version" is replaced with "Expense and profitability reports switch between accrual and cash basis".
- Checked and left as they are: supplier and expense due dates (still real), "Check that the backend is running" (the error when the API can't be reached), and the fixed project statuses note in Settings.
- `tsc -b` is clean and 37 Vitest tests pass. Not tried in a browser.

### 2026-11-01: Expense and profitability reports back to one basis, no toggle
- Owner decision: these two reports don't need an Accrual / Cash toggle or a separate cash view. The recorded amount with its Paid / Unpaid figures is enough.
  - The Expense report shows recorded expenses with Paid and Unpaid.
  - The Profitability report shows profit to date with Receivable.
  - Both are as they were before the cash basis was added, without the toggle.
- The cash basis stays on the Dashboard and the Project page, which keep their toggles. `ProjectFinance.cash` and the cash ledger are unchanged.
- Reports page description: dropped the line about accrual and cash.
- `tsc -b` is clean and 37 Vitest tests pass. Not tried in a browser.
