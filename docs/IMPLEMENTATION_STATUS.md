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
- [ ] Inventory: remove the low-stock KPI, the stock-level filter, the "Reorder soon" banner, the reorder level column and field, and `Material.reorderLevel`.
- [x] Inventory (FIFO) (2026-10-24, local store only): the FIFO stock value, the `q × unitCost` preview replaced, a unit cost field for positive adjustments. It still has to be wired to the API.
- [ ] Payroll: paid employee-weeks read-only in attendance, overtime and adjustments, plus a "Record correction" action on paid lines.
- [ ] Settings: revenue categories shown read-only.

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
- [x] Contract payment schedules and receipts (2026-10-24): schedule created with the project and readable; billing; client receipts (idempotent, voidable); write-offs (create only); delay deductions (apply and reverse); receivables per project and a company-wide summary. Not yet: editing a schedule, variations, write-off reversal (needs a migration).
- [ ] Revenue and expenses. Partial (2026-10-24): project revenue is recognised at billing, and manual project and company expenses with their payments are implemented. Other income is not implemented.
- [x] Commission management (2026-10-25, backend): per-project recipient arrangements (draft, confirm with stored amount, policy warning and owner exception), voids with a reason, payments (partial, idempotent, voidable), the directory, summary, by-recipient and payment views. The frontend still runs on its local store. See the completion log, "Commissions".
- [ ] Suppliers and payables. Partial (2026-10-24): the supplier directory (create, list or search, get, edit, activate or deactivate) and the optional supplier link on expenses. Supplier invoices, supplier payments and payables are not implemented.
- [x] Inventory and stock movements (2026-10-24): the materials catalogue; receipts, issues to construction projects, returns against the original issue and adjustments, costed FIFO from persistent cost layers with issue allocations (changed from the weighted average the same day, migration V4); balances, valuation, movement history and project material usage. Receipts from supplier invoices are not built yet.
- [x] Employee attendance, overtime, advances, and payroll (2026-10-24, backend): employees, the attendance register, overtime and approval, advances, weekly payroll worked out live, payments that freeze the week, closing a zero week, corrections, history, period summaries and project labour cost. The frontend still runs on its local store.
- [ ] Financial reports.
- [x] Private agreement document storage (2026-10-24): upload or replace, metadata and download of one current PDF per project. Removing an agreement is not implemented.

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
  | PUT `/api/v1/users/{id}` | Super Admin | display name (`version` required) |
  | PUT `/api/v1/users/{id}/role` | Super Admin | change role (`version` required) |
  | PUT `/api/v1/users/{id}/status` | Super Admin | activate or deactivate (`version` required) |
  | PUT `/api/v1/users/{id}/password` | Super Admin | reset someone else's password (204, never echoed) |

- Rules:
  - Nobody changes their own role or status, or resets their own password, through user administration (422).
  - There is always at least one active Super Admin. Account changes lock the active Super Admin rows and re-check the actor.
  - Deactivation, a role change or a password reset bumps `auth_version`, so that user's sessions end on their next request.
  - Generic 401 `Invalid username or password.` for an unknown user, a wrong password and a disabled account.
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
  - No report module yet. Project profitability will read the project totals from this module when the reports are built.
