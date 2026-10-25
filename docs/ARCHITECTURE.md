# Architecture

## Architecture Style
Use a modular monolith. A single Spring Boot application contains clearly separated business modules. Do not introduce microservices without explicit approval.

## Components
- React + TypeScript frontend.
- Spring Boot REST API.
- PostgreSQL relational database.
- Flyway-managed schema migrations.
- Docker Compose for local development.
- Private object storage for agreement PDFs when implemented.

## API Convention
Use the /api/v1 prefix. Keep HTTP controllers thin and place business rules in service classes. Use request/response DTOs rather than exposing JPA entities directly.

## Package Structure
Base package: `com.sondurukedella`.

Packages that have no classes yet contain only a `package-info.java` placeholder, which is removed when real classes are added. A package's existence does not mean its module is implemented. See `docs/IMPLEMENTATION_STATUS.md` for actual progress.

```text
com.sondurukedella
├── settings      controller service repository entity dto mapper validation
├── clients       controller service repository entity dto mapper validation
├── projects      controller service repository entity dto mapper validation
├── agreements    controller service repository entity dto mapper validation storage
├── revenue       controller service repository entity dto mapper validation
├── commissions   controller service repository entity dto mapper validation
├── expenses      controller service repository entity dto mapper validation
├── suppliers     controller service repository entity dto mapper validation
├── inventory     controller service repository entity dto mapper validation
├── employees     controller service repository entity dto mapper validation
├── payroll       controller service repository entity dto mapper validation
├── reports       controller service repository dto
├── activity      controller service repository entity dto mapper
├── common        exception web audit money validation
├── config        web (plus ClockConfig at the package root)
└── security      config user auth audit
```

Test packages mirror the main packages they test, and exist only where there are tests (no placeholder `package-info.java` files on the test side). Shared test code lives in `support` and `integration/api/ApiFixtures`; database-backed tests are in `integration/database` and `integration/api`.

## Module Responsibilities
Tables are listed in `docs/DATABASE.md`.

| Module | Responsibility | Owns |
| --- | --- | --- |
| `settings` | Company profile, commission policy, wage settings, and the editable lists in Settings. Revenue categories are fixed and read-only. | `company_profile`, `commission_policy`, `wage_settings`, `expense_categories`, `units`, `payment_methods` |
| `clients` | Client records. | `clients` |
| `projects` | Projects, contract value, dates and status. The contract value and project type are fixed after the project is created. | `projects` |
| `agreements` | Uploading agreement PDFs, storing their metadata and controlling download access. The PDFs are kept in private storage. | `project_agreements` |
| `revenue` | Installment schedules, billing and variations, client payments, bad-debt write-offs, delay deductions (applied and reversed), and other income with its receipts. | `installments`, `client_payments`, `receivable_write_offs`, `delay_deduction_terms`, `other_income`, `other_income_receipts` |
| `commissions` | Commission arrangements based on the contract value, policy warnings, confirmation (with the stored amount), voids, payments and the company-wide commission views. | `commission_arrangements`, `commission_payments` |
| `expenses` | Manual project and company expenses, including machinery rental as an expense category, and their payments. | `expenses`, `expense_payments` |
| `suppliers` | Suppliers, supplier invoices with their lines, and supplier payments (payables). | `suppliers`, `supplier_invoices`, `supplier_invoice_lines`, `supplier_payments` |
| `inventory` | Materials and the stock movement ledger: receipts, issues, returns to stock and adjustments, costed FIFO from persistent cost layers. | `materials`, `stock_movements`, `stock_cost_layers`, `stock_cost_allocations` |
| `employees` | Workers and their daily inputs: attendance and overtime. | `employees`, `attendance`, `overtime_entries` |
| `payroll` | Weekly payroll: salary advances and their recovery, adjustments, the per-employee weekly freeze, corrections and payroll payments. | `salary_advances`, `payroll_adjustments`, `payroll_lines`, `payroll_line_costs`, `advance_recoveries`, `payroll_corrections`, `payroll_payments` |
| `reports` | Read-only reports: dashboard, company financial summary, project financials and cost breakdown, client receivables (aging), inventory valuation and payroll weeks. Supplier payables wait for supplier invoices. | Nothing |
| `activity` | The project activity feed, written by the other modules. | `activity_log` |
| `common` | Shared code that contains no business rules. | — |
| `config` | Application-wide configuration. | — |
| `security` | Sign-in, sessions, the two roles, user administration, the first Super Admin bootstrap and the security audit trail. | `app_users`, `security_events` |

## Layer Responsibilities
- `controller`: HTTP endpoints under `/api/v1`. Controllers bind and validate request DTOs, call a service and return response DTOs. They contain no business rules and never return entities.
- `service`: Business rules and transaction boundaries. Services lock rows where a balance must not be exceeded. A module's public service interface is the only way other modules use it.
- `repository`: Spring Data JPA access to the module's own tables, plus the module's own queries.
- `entity`: JPA mappings of the tables the module owns. The Flyway migrations define the schema, and Hibernate only validates it.
- `dto`: API request and response contracts, as Java records. Monetary values use `BigDecimal`.
- `mapper`: Conversion between entities and DTOs. Mappers contain no business logic.
- `validation`: Validators and constraints specific to the module, for example matching a category to its project type. Rules that need data from other modules belong in the service.

Shared packages:
- `common.exception`: The application's exception types and the global error handler.
- `common.web`: Shared API response, error and paging contracts.
- `common.audit`: Audit fields (`created_at`, `created_by`, `updated_at`) and how the current user is resolved. Each entity whose table has a `version` column declares it for optimistic locking.
- `common.money`: `BigDecimal` scale and rounding helpers (`HALF_UP`, 2 decimal places for amounts).
- `common.validation`: Generic validators shared by several modules, for example `OptionalEmail` (used by Clients and Suppliers).
- (No `config.persistence` package: nothing needs JPA configuration beyond Spring Boot's defaults. Audit columns are filled by `common.audit.AuditableEntity` and `CurrentUser`. The placeholder was removed on 2026-10-26.)
- `config.web`: Web configuration. `CorsConfig` allows credentialed requests from the origins in `security.cors.allowed-origins` only.
- `config.ClockConfig`: the application `Clock`, in the Asia/Colombo zone. Business dates such as completion and activity dates come from it, so tests can fix the time.
- `security.config`: `SecurityConfig` (the filter chain and every URL rule), `SessionUserFilter` and the JSON 401/403 responses.
- `security.user`: Application users (`app_users`), roles, the user-details lookup, user administration (`/api/v1/users`), the user's own account, and the first Super Admin bootstrap.
- `security.auth`: Sign-in (`/api/v1/auth`), the sign-in throttle, and changing your own password.
- `security.audit`: The append-only security audit trail (`security_events`).
- `common.audit.CurrentUser`: reads the signed-in user id for `created_by`, without `common` depending on `security`.

## Module Boundaries
- A module reads and writes only its own tables through its own repositories. It never uses another module's repositories or entities.
- Modules call each other through service-level interfaces or clearly defined contracts that pass DTOs or IDs. Examples:
  - Posting a `STOCK` supplier invoice: `suppliers` asks `inventory` to record the receipts.
  - Issuing stock or entering overtime: `inventory` and `employees` check with `projects` that the project is a construction project.
  - Editing attendance or overtime (implemented): the V1 freeze trigger refuses a change to a paid employee-week, and `employees` turns that refusal into a 409. `employees` does not ask `payroll`: `payroll` reads attendance and overtime from `employees`, so asking back would make the two modules depend on each other. Both modules take the same `employees` row lock before writing, so a day saved while that week is being paid can't slip past the freeze.
  - Calculating commission: `commissions` reads the contract value from `projects`.
  - Commissions (implemented 2026-10-25): `/api/v1/projects/{projectId}/commissions` (the project's set) and `/api/v1/commissions` (directory, summaries, voids, payments).
    - Both controllers live in `commissions`, because `projects` must not depend on it (the same pattern as the agreement endpoints).
    - It reads projects only through `ProjectService.get`, the policy through `settings.CommissionPolicyService`, and payment methods through `PaymentMethodService`. It records `COMMISSION` entries through `ActivityService`. Nothing imports `commissions`.
    - Saving a project's set is one transaction. It locks the project's live arrangements (`SELECT … FOR UPDATE`, id order), validates the whole set, checks the policy, deletes the drafts that were left out, then saves and confirms. Any failure rolls back everything.
    - A payment or a void locks the arrangement row first. Concurrent payments therefore queue and can't overpay, a resent `requestId` finds the first payment, and a payment and a void of the same commission can't cross.
    - Cost boundary:
      - the commission cost is the confirmed arrangements' stored amounts, recognised on the date of confirmation;
      - payments only settle that liability; they are never a second cost;
      - drafts and voided arrangements are not costs;
      - the Commission expense category is system-generated, so a manual expense can't count it again (the V1 check refuses it too).
    - The 80/20 design split is the commission policy's pool (default 20%) against the company share (100 − pool). It is a view and a warning level, not a ledger entry; the company share is simply what is left of the contract value.
  - Delay deductions and schedules: `projects` passes the project type (as the plain code `revenue` stores in `project_type`) and the contract value into the `revenue` service operations. `revenue` never imports `projects` or queries its repositories, because `projects` already depends on `revenue`.
  - Recording an event: any module calls the `activity` service. Reading the feed (implemented 2026-10-26): `GET /api/v1/activity` (`projectId`, `kind`, `from`/`to`, paging), newest first. It never checks the project, because `projects` depends on `activity`.
  - Creating a project (implemented): `projects` checks or creates the client through `ClientService` (`exists`, `create`, `findNames`, `findIdsByName`), creates the schedule and delay terms through `revenue` (`InstallmentScheduleService`, `DelayTermsService`), and records the status event through `ActivityService`. All of this runs in one transaction. The revenue and activity methods use `MANDATORY` propagation, so they cannot commit on their own.
  - Reading a project's schedule and delay terms (implemented): `ProjectService` checks that the project exists (404 if not), then calls the read-only `InstallmentScheduleService.findSchedule` and `DelayTermsService.findTerms`, which return `revenue` DTOs. These reads use ordinary read-only transactions; only the `revenue` write methods are `MANDATORY`. `revenue` still takes just the project id and never imports `projects`.
  - Agreements (implemented): `agreements` checks the project through `ProjectService.requireExists` and never touches the projects repository. It records the upload event through `ActivityService` (`DOCUMENT`).
  - Employees (implemented): the worker register (`/api/v1/employees`), the daily attendance register (`/api/v1/attendance`) and overtime (`/api/v1/overtime`).
    - It checks projects through `ProjectService.get`, never the projects repository.
    - It hands `payroll` the days and overtime of a date range (`AttendanceService.payrollInputs`, `workerWeeksOnProject`) and workers through `EmployeeService` (`find`, `findAll`, `lockForPayroll`). Nothing else imports it.
  - Payroll (implemented): weekly payroll (`/api/v1/payroll`). It depends on `employees`, `projects` (`get`, `requireExists`) and `settings` (`WageSettingsService` for the payday, `PaymentMethodService`).
    - Each write is one transaction that first locks the worker's `employees` row (`lockForPayroll`, `MANDATORY` propagation). That covers recording an advance, saving an adjustment, a payment (whose first one freezes the week), closing a week with nothing to pay, a correction, and voiding.
    - A week stays live (`PayrollLedger` + the pure `PayrollCalculator`) until it is frozen into `payroll_lines`, `payroll_line_costs` and `advance_recoveries`. A failed payment rolls the freeze back with it.
    - Cost boundary: labour cost is gross wages split by project, plus corrections. Advances are cash ahead of pay, recovered from net pay, and payments settle wages; neither is a cost. The Labour expense category is system-generated, so a manual expense can't count wages again.
  - Inventory (implemented): materials and the stock ledger (`/api/v1/inventory`).
    - It checks projects through `ProjectService` (`get`, `requireExists`), suppliers through `SupplierService` (`find`, `findNames`) and units through `MeasurementUnitService`, never their repositories. Nothing imports `inventory`.
    - Every stock change is one transaction that locks the `materials` row (`SELECT … FOR UPDATE`), checks the `request_id` and validates. `StockLedger` then posts it:
      - It appends one immutable `stock_movements` row.
      - A receipt or a positive adjustment also creates its FIFO cost layer (`stock_cost_layers`).
      - An issue or a negative adjustment locks the open layers and draws from the oldest first, writing one `stock_cost_allocations` row per layer.
      - A return locks the layers its issue drew from and puts the stock back into them, newest draw first.
      - Issues and returns also write the project's `EXPENSE` activity entry.
      - Finally, the open layers must add up exactly to the ledger's running balance, or the whole posting is rolled back.

      The movement, layers, allocations, project cost and activity entry therefore change together or not at all. The arithmetic lives in the pure `FifoCosting` class.
    - Concurrency: the material row lock serialises every posting for one material. Two issues can never draw the same layer quantity, receipts queue as layers in posting (`id`) order, and concurrent returns are checked against the remaining quantity one at a time. The database backs this up: the V1 return trigger, and the V4 reversal trigger, which locks the draw being reversed. A resent `requestId` returns the first movement and writes no new allocations.
    - Cost boundary: buying into stock (`RECEIVED`) is stock value (a layer), not a project cost and not an expense. Issuing (`ISSUED`) is what makes material a project cost, at the FIFO cost fixed on the movement and its allocations; later purchase prices never change it. Returning (`RETURNED`) reverses it at that same cost. Manual `Materials` expenses and `PROJECT` supplier invoices are separate sources, so no purchase is counted twice. Movements posted before FIFO (`costing_method = WAC`) keep the average cost they were posted at.
    - A manual receipt creates no supplier payable; credit purchases go through supplier invoices, whose STOCK lines post their receipts through `receiveInvoiceLine`.
  - Suppliers (implemented): the supplier directory (`/api/v1/suppliers`). It reads only the default credit period from `settings.CompanyProfileService`, and nothing it does reaches into other modules. `expenses` depends on it, never the reverse: a supplier's expenses are listed with `GET /api/v1/expenses?supplierId=`. Suppliers are deactivated, never deleted.
  - Supplier invoices and payables (implemented 2026-10-25, `/api/v1/supplier-invoices`, `SupplierInvoiceService`):
    - Saving an invoice is one transaction: invoice, lines and the optional first payment.
    - A `STOCK` invoice also posts each line as a FIFO receipt through `StockMovementService.receiveInvoiceLine` (`MANDATORY`). Each receipt is linked to its line by the V1 key. The line must name the material and use the material's unit, and the price becomes the layer's unit cost.
    - A `PROJECT` invoice (construction projects only) is that project's material cost and posts no stock.
    - `inventory` already reads the supplier directory (`SupplierService`), so the invoice service is a separate bean and there is no bean cycle.
    - Payments lock the invoice row (no overpayment under concurrency), use `request_id` idempotency, and are voided, never deleted.
    - Invoices are not edited or deleted. A resent invoice hits the unique (supplier, invoice no) key and gets a 409.
    - Cost boundary: `STOCK` invoices are stock value, never cost; `PROJECT` invoices are cost; payments only settle the payable.
  - Expenses (implemented): `expenses` checks a project through `ProjectService.get`, categories through `ExpenseCategoryService.findById`, units through `MeasurementUnitService`, payment methods through `PaymentMethodService` and suppliers through `SupplierService.find` and `findNames`, never their repositories. Nothing imports `expenses`. Creating an expense (with an optional first payment), editing one, and recording or voiding a payment each run in one transaction, together with the `EXPENSE` activity entry, with the expense row locked for payments and edits. It stores only manual costs; labour, commission, supplier invoices and stock issues stay in their own modules, so nothing is counted twice.
  - Billing, receipts, write-offs and delay deductions (implemented):
    - Endpoints that need a project live in `projects` (`ProjectRevenueController`, `ProjectRevenueService`). They check the project exists, so an unknown one is a 404. For deductions they pass a `revenue.dto.ProjectFacts` (id, type code, contract value, actual completion) into `revenue`. `revenue` never imports `projects`.
    - Each operation is one `revenue` transaction (`BillingService`, `ReceiptService`, `WriteOffService`, `DelayDeductionService`). It locks the installment row with `SELECT … FOR UPDATE` before reading its balance, saves the change and writes the activity entry (`NOTE` for billing, `PAYMENT`, `WRITEOFF`, `DEDUCTION`). If any part fails, all of it rolls back.
    - Lock order: installment, then delay terms. Receipts, write-offs and applying a deduction all follow it, so they serialise on one installment without deadlocking. Reversing a deduction locks only the terms; it can only raise the balance.
    - `revenue` reads payment methods through `settings.PaymentMethodService` (`isActive`, `findNames`), never through the settings repository.
    - Reads (`ReceivableService`) use read-only transactions and change nothing. The company-wide `/api/v1/receivables/summary` is in `revenue` itself, because it needs no project data.
- Operations that span modules run in one transaction started by the calling module's service.
- Avoid circular dependencies between modules. If two modules need each other, move the shared contract to the module that owns the data.
- `reports` reads data from the business modules, either through their services or through dedicated read-only queries in `reports.repository`. It uses the same calculation rules as those modules. It owns no entities, never writes, and never duplicates business records.

## Reports (implemented 2026-10-25)
All under `/api/v1/reports`, GET only, both roles through the central `/api/v1/**` rule (anonymous callers get 401). They are a **management view**, not formal financial statements. Nothing is cached, and no report writes.

### Where each figure comes from
Rule-heavy figures always come from the owning module's service. `reports.repository.ReportQueries` holds only plain SELECT sums over a clear, indexed date column.

| Figure | Authoritative source | Date basis |
| --- | --- | --- |
| Contract value | `ProjectService` | none (agreed; never revenue) |
| Revenue recorded (per project) | `ReceivableService.forProject`: billed installments net of the applied delay deduction | `installments.billed_on` |
| Revenue recorded, receipts, write-offs (company, by period) | `ReportQueries.billing/receipts/writeOffs`: the same per-installment rule, `max(amount − deduction, 0)`; voided receipts excluded | `billed_on`, `payment_date`, `write_off_date` |
| Receivables outstanding, overdue, aging | `ReceivableService` (`summary`, `forProject`, `billedReceivables`) | as of today (Colombo) |
| Manual expenses | `ExpenseService.summary` (expenses are never voided; payments don't change cost) | `expense_date` |
| Materials (per project) | `StockMovementService.projectUsage`: issued − returned at the FIFO cost fixed on each movement | movement date |
| Supplier invoices | `SupplierInvoiceService` (`forProject`, `summary`): PROJECT invoices are project material cost; STOCK invoices are stock purchases (not cost) | `invoice_date` |
| Materials (company, by period) | `ReportQueries.materialUse`: the same movement costs | `movement_date` |
| Labour (per project) | `PayrollService.projectLabour`: frozen split + corrections + open weeks priced live | payroll week |
| Labour (company, by period) | `PayrollService.weeks`: labour cost = gross pay + corrections | weeks whose Monday falls in the range |
| Commission cost (per project) | `CommissionService.forProject`: confirmed arrangements' stored amounts | — |
| Commission confirmed and paid (company, by period) | `ReportQueries.commissionConfirmed/commissionPaid`: status `CONFIRMED` only; voided payments excluded | `confirmed_on`, `payment_date` |
| Commission outstanding | `CommissionService.summary` | as of today |
| Inventory value | `MaterialService.valuation` and `search`: Σ remaining value of open FIFO layers | as of today |

### Project figures
- Project costs = manual project expenses + materials (issued − returned) + PROJECT supplier invoices + labour + confirmed commission.
- Profit = revenue recorded − written off − project costs. This is the rule in DATABASE.md "Profitability" and the frontend's `finance.ts`.
- The basis is `RECORDED` when the project is `COMPLETED`, otherwise `ESTIMATED`.
- Margin = profit ÷ revenue recorded × 100, rounded half-up to 2 places. It is null when nothing has been billed.
- The cost breakdown's lines add up to each category, and the categories to the total, to the cent. The project report's `costs.total` is the same number.

### Double counting, prevented by construction
- Stock purchases are never cost; only issues less returns are.
- Labour is gross wages plus corrections. Payroll advances and payroll payments are cash, never cost.
- Commission cost is confirmed arrangements only. Commission payments settle it; drafts and voided arrangements are excluded.
- Payroll and commission can't appear as manual expenses: their categories are system-generated, and the V1 check refuses them.
- Company expenses are never project costs.
- The financial summary keeps every block separate (revenue, expenses, materials, labour, commission), because each is recognised on a different date. It never adds them into one profit figure. Current inventory value is a position, not a period expense.

### Consistency and scale
- Each report runs in one read-only `REPEATABLE READ` transaction, so all its figures come from one database snapshot.
- Period sums are SQL aggregates on indexed date columns.
- These run in memory:
  - receivables: every billed installment through `ReceivableService`, then filtered and paged;
  - the dashboard's project totals: every project's own report, summed, so the dashboard and the project pages always agree;
  - payroll weeks: up to 53, each through `PayrollService.week`.

  That is fine for this company's volumes (tens to hundreds of projects). At much larger volumes the dashboard project loop is the first thing to replace with aggregated queries.
- Ranges (`financial-summary`, `payroll`) need both dates and are at most 366 days.

### Not available yet (and not shown as zero)
- Other income (since 2026-10-26) is reported apart from project revenue: dashboard `revenue.otherIncome` / `otherIncomeReceived` and `receivables.otherIncomeOutstanding` / `otherIncomeOverdue`; summary `revenue.otherIncome` (`income_date`), `otherIncomeReceived` (receipt `payment_date`) and `position.otherIncomeOutstanding`. `notIncluded` is now empty.
- Supplier invoices are counted since 2026-10-25:
  - `PROJECT` invoices are a project cost category (`supplierInvoices`, cost-breakdown `SUPPLIER_INVOICES`).
  - Outstanding supplier invoices appear in the dashboard payables and in the summary's position.
  - The summary shows stock purchases and project invoices separately (date basis `invoice_date`).

## Financial Integrity
- Use BigDecimal for Java monetary calculations.
- Use appropriate PostgreSQL NUMERIC columns for monetary amounts.
- Use database transactions for operations that update related financial records.
- Prevent duplicate payment recording and double counting.
- Keep original contract values and applied deductions separately.
- Preserve finalized payroll and confirmed commission history.
- Do not count stock purchases and stock issues twice as expenses.

## Authentication and Authorization
Implemented 2026-10-25. Spring Security, server-side sessions, two roles. No JWT, no HTTP Basic, no form login, no public sign-up.

### Roles (owner decision: these two only)
| Role | May use |
| --- | --- |
| `SUPER_ADMIN` | Everything an Admin can, plus user administration (`/api/v1/users/**`: list, view, create, edit, change role, activate or deactivate, reset password) and the actuator endpoints other than health. Only a Super Admin can make someone a Super Admin. |
| `ADMIN` | All business modules under `/api/v1` (settings, clients, projects, agreements, revenue, receipts, expenses, suppliers, inventory, employees, attendance, overtime, payroll, commissions), plus their own profile, password change and sign-out. No user administration, and no change to the commission policy. |

Roles are the `app_users.role` column (`UserRole` enum, authorities `ROLE_SUPER_ADMIN` / `ROLE_ADMIN`). Every rule is in one place, `security.config.SecurityConfig`. Controllers carry no role checks. The user-administration service checks again against locked rows, because the actor's own role may have just changed.

| Path | Who |
| --- | --- |
| `POST /api/v1/auth/login`, `GET /api/v1/auth/csrf` | anyone |
| `GET /actuator/health` | anyone (shows UP/DOWN only, no details), for container and load-balancer probes |
| `/api/v1/users`, `/api/v1/users/**` | `SUPER_ADMIN` |
| `/api/v1/settings/commission-policy`: GET | `SUPER_ADMIN` or `ADMIN` |
| `/api/v1/settings/commission-policy`: any other method (the update is PUT) | `SUPER_ADMIN` (since 2026-10-25) |
| `/actuator/**` (other than health) | `SUPER_ADMIN` |
| every other `/api/v1/**` | `SUPER_ADMIN` or `ADMIN` |
| anything else | denied |

Apart from the single GET exception on the commission policy, the rules match paths only, never HTTP methods, so GET, POST, PUT, PATCH and DELETE are covered alike. `EndpointAuthorizationIntegrationTest` reads every mapped endpoint from Spring MVC and checks it as an anonymous caller, an Admin and a Super Admin, so a new endpoint is checked automatically.

Changing the company-wide commission policy (design pool and construction level) is Super Admin only. This was requested for the Commissions module on 2026-10-25. The rest of Settings (company profile, wages and the lists) is open to both roles. Nothing in the requirements reserves those for Super Admin; the owner can change that with one rule in `SecurityConfig`. Everyday commission work (drafts, confirming, policy exceptions on a project, voids, payments) is open to both roles, like the other business modules.

### Sign-in flow (React app)
1. `GET /api/v1/auth/csrf` sets the `XSRF-TOKEN` cookie (readable by JavaScript). Returns 204.
2. `POST /api/v1/auth/login` with JSON `{username, password}` and the header `X-XSRF-TOKEN: <cookie value>`.
   - On success: 200 with `{id, username, displayName, role}`. The session id is changed (no session fixation), a new CSRF token is issued, and the session (`JSESSIONID`) holds the principal without the password hash.
   - On failure: 401 `Invalid username or password.` The same answer for an unknown user, a wrong password and a disabled account. The password is checked before the account status, so even the timing is alike.
3. Later requests send the `JSESSIONID` cookie automatically (`credentials: 'include'`, or `withCredentials` in axios). Every POST, PUT, PATCH and DELETE also sends `X-XSRF-TOKEN`.
4. `GET /api/v1/auth/me` returns the signed-in user; it returns 401 once the session has ended.
5. `POST /api/v1/auth/logout` (with the CSRF header) invalidates the session, deletes the cookie, and returns 204.

Errors from the security layer are RFC 9457 JSON: 401 `Sign in to continue.`, 403 `You don't have permission to do this.` (also for a missing or wrong CSRF token). There is no `WWW-Authenticate` header, so the browser never shows a Basic login box.

### Sessions
- The session lives in memory in the one backend instance. It is lost on restart, and everyone signs in again. Idle timeout is `SESSION_TIMEOUT` (default 30 minutes).
- Cookie: `HttpOnly`, `SameSite=Lax`, `Secure` unless `SESSION_COOKIE_SECURE=false` (compose sets false for plain-HTTP local development). Session ids are never put in URLs.
- `SessionUserFilter` checks every signed-in request against `app_users` (one primary-key lookup). Each session remembers the `auth_version` it signed in with. Deactivating a user, changing their role, or changing or resetting their password bumps `auth_version`, so their existing sessions end on the next request (401). Changing your own password keeps the current session, under a new id, and ends all the others.

### Brute-force protection
- `LoginAttemptThrottle`, in memory:
  - at most 5 failed sign-ins per typed username, and 20 per client address, per 15 minutes (`LOGIN_MAX_FAILURES_PER_USERNAME`, `LOGIN_MAX_FAILURES_PER_ADDRESS`, `LOGIN_FAILURE_WINDOW`);
  - past the limit, sign-in answers 429 with `Retry-After`, even with the right password;
  - wrong "current password" entries on the password change count too.
- It is keyed by what was typed, so unknown usernames are throttled the same way and nothing reveals which accounts exist.
- The client address is the direct peer address. `X-Forwarded-For` is not trusted.
- Limits: it resets on restart and isn't shared between instances. Behind a reverse proxy, every request has the proxy's address, so the per-address limit applies to everyone together until forwarded headers are configured deliberately (`server.forward-headers-strategy`).

### Passwords
- BCrypt via Spring's delegating encoder (stored as `{bcrypt}$2…`). They are never returned, logged or stored in plain text.
- Rules for a new password (`PasswordPolicy`): at least 12 characters, at most 72 bytes (BCrypt's limit).
- Request DTOs override `toString` so a logged request can't show a password.

### CSRF and CORS
- CSRF stays on for every state-changing request, login and logout included (cookie-to-header pattern, `csrf.spa()`). The `XSRF-TOKEN` cookie is readable by JavaScript by design; it is useless without the HttpOnly session cookie.
- CORS (`config.web.CorsConfig`) allows credentialed requests only from the exact origins in `CORS_ALLOWED_ORIGINS` (comma separated). A `*`, a path or a trailing slash is refused at startup. Empty means same-origin only. Locally, compose sets `http://localhost:${FRONTEND_PORT}`.
- Response headers: Spring's defaults (`X-Content-Type-Options`, `X-Frame-Options: DENY`, no-store caching, and HSTS over HTTPS), plus `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` and `Referrer-Policy: no-referrer`.

### User administration rules (`UserAdministrationService`)
- Users are created only by a Super Admin (`POST /api/v1/users`, role defaults to `ADMIN`). Users are never deleted, only deactivated.
- Nobody changes their own role or status, or resets their own password, through user administration. Use `PUT /api/v1/auth/me/password` instead. A self-change answers 422.
- There is always at least one active Super Admin. Each account change first locks every active Super Admin row (`SELECT … FOR UPDATE`, in id order), then checks that the actor is still one of them. So two Super Admins deactivating each other at the same moment can't both succeed (`UserAdministrationConcurrencyIntegrationTest`).
- Usernames are case-insensitive: stored lower case, compared on `lower(username)`. 3 to 50 characters: letters, digits, `.`, `-` or `_`.

### First Super Admin
- No account is created by migrations or code defaults.
- At startup, `SuperAdminProvisioner` creates one from `BOOTSTRAP_SUPER_ADMIN_USERNAME` and `BOOTSTRAP_SUPER_ADMIN_PASSWORD` (optional `BOOTSTRAP_SUPER_ADMIN_DISPLAY_NAME`), only while no `SUPER_ADMIN` row exists at all.
- After that the settings are ignored: nothing is overwritten and no password is reset on restart.
- A PostgreSQL advisory lock stops two instances that start together from both creating one.
- Half-set or invalid settings stop the start with a message that never includes the values. The password is never logged.
- See DEVELOPMENT_WORKFLOW.md, "Authentication setup".

### Audit
- `security_events` records sign-ins (succeeded, failed, throttled), sign-outs, and every account change: created, updated, role changed, activated or deactivated, password changed or reset, and the bootstrap.
- Each row has the actor, the target user, the typed username and the client address. It never holds a password, hash or session id.
- It is append-only: a V5 trigger refuses updates and deletes.
- Account changes write their event in the same transaction. Sign-in events commit on their own.
- No API reads it yet.
- `created_by` on business tables is now filled with the signed-in user's id (`common.audit.CurrentUser`): through `AuditableEntity`, `StockMovement`, `PayrollLine` and `ActivityEntry`.

## Security and Documents
- Use Spring Security for authentication and authorization (see "Authentication and Authorization").
- Never store credentials in source code.
- Keep uploaded agreements private.
- Store document metadata and a storage reference in PostgreSQL, not the PDF binary itself by default.
- Validate uploaded file type, size, access permissions, and download authorization.
- Agreement PDFs (implemented):
  - `agreements.storage.AgreementFileStore` is the only code that touches the disk. Controllers and the database code never build paths.
  - Files go to `agreements.storage-dir` (env `AGREEMENTS_STORAGE_DIR`): `backend/data/agreements` (git-ignored) when run locally, and the `agreement_files` named volume at `/app/data/agreements` in Docker. The directory is not on the classpath or under any static folder, so nothing serves it except the authenticated download endpoint.
  - Uploads are streamed to a temp file while they are hashed, counted and checked for the `%PDF-` signature, then renamed to a server-generated `<uuid>.pdf`. Keys are validated against that pattern and must resolve directly inside the storage directory.
  - Limits: `agreements.max-size` (env `AGREEMENTS_MAX_SIZE`, default 20MB, matching the UI) is enforced while streaming. `spring.servlet.multipart.max-file-size` and `max-request-size` (default 21MB) reject larger requests earlier. Raise them together.
  - Order of writes: the file is stored first, then the metadata in its own transaction. If the database step fails, the new file is deleted, and the previous agreement row and file are untouched. A crash between the two steps can leave an orphaned file but never metadata without a file.
  - Downloads are always `Content-Disposition: attachment`, with the cleaned original name encoded per RFC 6266/5987.
  - Deployment: the storage directory must be persistent and backed up together with the database.

## Frontend Integration
Preserve the existing React UI and its shadcn theme. Use typed API contracts and centralized API configuration. Keep frontend secrets out of browser-accessible environment variables.
