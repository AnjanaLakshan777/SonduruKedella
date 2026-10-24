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
├── config        persistence web (plus ClockConfig at the package root)
└── security      config user auth
```

Tests in `src/test/java` mirror these packages: `controller`, `service` and `repository` for each business module and `reports`; `service` and `repository` for `activity`; plus `validation` where a module has its own validators (currently `clients`, `projects`, `revenue` and `agreements`), `agreements/storage`, `common`, `security`, `integration/database`, `integration/api` and `support` (shared fixtures).

## Module Responsibilities
Tables are listed in `docs/DATABASE.md`.

| Module | Responsibility | Owns |
| --- | --- | --- |
| `settings` | Company profile, commission policy, wage settings, and the editable lists in Settings. Revenue categories are fixed and read-only. | `company_profile`, `commission_policy`, `wage_settings`, `expense_categories`, `units`, `payment_methods` |
| `clients` | Client records. | `clients` |
| `projects` | Projects, contract value, dates and status. The contract value and project type are fixed after the project is created. | `projects` |
| `agreements` | Uploading agreement PDFs, storing their metadata and controlling download access. The PDFs are kept in private storage. | `project_agreements` |
| `revenue` | Installment schedules, billing and variations, client payments, bad-debt write-offs, delay deductions (applied and reversed), and other income with its receipts. | `installments`, `client_payments`, `receivable_write_offs`, `delay_deduction_terms`, `other_income`, `other_income_receipts` |
| `commissions` | Commission arrangements based on the contract value, policy warnings, confirmation (with the stored amount) and payments. | `commission_arrangements`, `commission_payments` |
| `expenses` | Manual project and company expenses, including machinery rental as an expense category, and their payments. | `expenses`, `expense_payments` |
| `suppliers` | Suppliers, supplier invoices with their lines, and supplier payments (payables). | `suppliers`, `supplier_invoices`, `supplier_invoice_lines`, `supplier_payments` |
| `inventory` | Materials and the stock movement ledger: receipts, issues, returns to stock and adjustments, costed FIFO from persistent cost layers. | `materials`, `stock_movements`, `stock_cost_layers`, `stock_cost_allocations` |
| `employees` | Workers and their daily inputs: attendance and overtime. | `employees`, `attendance`, `overtime_entries` |
| `payroll` | Weekly payroll: salary advances and their recovery, adjustments, the per-employee weekly freeze, corrections and payroll payments. | `salary_advances`, `payroll_adjustments`, `payroll_lines`, `payroll_line_costs`, `advance_recoveries`, `payroll_corrections`, `payroll_payments` |
| `reports` | Read-only reports: income, expenses, project profitability, client receivables, supplier payables and labour payments. | Nothing |
| `activity` | The project activity feed, written by the other modules. | `activity_log` |
| `common` | Shared code that contains no business rules. | — |
| `config` | Application-wide configuration. | — |
| `security` | Authentication and authorization. | `app_users` |

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
- `config.persistence`: JPA and auditing configuration.
- `config.web`: Web configuration such as CORS for the frontend origin and JSON settings.
- `config.ClockConfig`: the application `Clock`, in the Asia/Colombo zone. Business dates such as completion and activity dates come from it, so tests can fix the time.
- `security.config`: Spring Security configuration.
- `security.user`: Application users (`app_users`) and the user-details lookup.
- `security.auth`: Login and session or token handling.

## Module Boundaries
- A module reads and writes only its own tables through its own repositories. It never uses another module's repositories or entities.
- Modules call each other through service-level interfaces or clearly defined contracts that pass DTOs or IDs. Examples:
  - Posting a `STOCK` supplier invoice: `suppliers` asks `inventory` to record the receipts.
  - Issuing stock or entering overtime: `inventory` and `employees` check with `projects` that the project is a construction project.
  - Editing attendance or overtime (implemented): the V1 freeze trigger refuses a change to a paid employee-week, and `employees` turns that refusal into a 409. `employees` does not ask `payroll`: `payroll` reads attendance and overtime from `employees`, so asking back would make the two modules depend on each other. Both modules take the same `employees` row lock before writing, so a day saved while that week is being paid can't slip past the freeze.
  - Calculating commission: `commissions` reads the contract value from `projects`.
  - Delay deductions and schedules: `projects` passes the project type (as the plain code `revenue` stores in `project_type`) and the contract value into the `revenue` service operations. `revenue` never imports `projects` or queries its repositories, because `projects` already depends on `revenue`.
  - Recording an event: any module calls the `activity` service.
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
    - Receipts create no supplier payable; credit purchases are meant for supplier invoices, which are not built yet.
  - Suppliers (implemented): the supplier directory (`/api/v1/suppliers`). It reads only the default credit period from `settings.CompanyProfileService`, and nothing it does reaches into other modules. `expenses` depends on it, never the reverse: a supplier's expenses are listed with `GET /api/v1/expenses?supplierId=`. Suppliers are deactivated, never deleted. Supplier invoices, supplier payments and payables are not implemented yet.
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

## Financial Integrity
- Use BigDecimal for Java monetary calculations.
- Use appropriate PostgreSQL NUMERIC columns for monetary amounts.
- Use database transactions for operations that update related financial records.
- Prevent duplicate payment recording and double counting.
- Keep original contract values and applied deductions separately.
- Preserve finalized payroll and confirmed commission history.
- Do not count stock purchases and stock issues twice as expenses.

## Security and Documents
- Use Spring Security for authentication and authorization.
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
