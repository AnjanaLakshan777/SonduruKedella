# Database Guidelines

## Database
PostgreSQL is the system of record for business and financial data. Local development uses Docker Compose with a named persistent volume.

## Schema Management
Use Flyway versioned SQL migrations. Do not use Hibernate schema auto-update as the production schema management strategy. Do not edit an already-applied migration in shared or persistent environments; create a new migration instead.

## Monetary Data
Use Java BigDecimal and suitable PostgreSQL NUMERIC types. Define scale and rounding policies deliberately. Never use floating-point types for money calculations.

## Core Business Areas
- Clients and contact details.
- Projects, contract values, deadlines, and payment schedules.
- Revenue and client receipts.
- Project expenses and company-level expenses.
- Commission recipients, confirmed commission terms, and payments.
- Suppliers, invoices, and partial payments.
- Materials, stock movements, and project issues.
- Employees, attendance, overtime, advances, and weekly payroll.
- Document metadata and audit history.

## Financial Integrity Rules
1. Revenue records and receipts are separate concepts.
2. A client receipt must not be counted as new revenue repeatedly.
3. Supplier purchases and supplier payments are separate records.
4. Commission is calculated from the agreed contract value using the confirmed percentage and recorded calculation basis.
5. Multiple commission recipients must be supported without silently exceeding the configured allocation policy.
6. Delay deductions must not overwrite the original contract value.
7. Written-off receivables are not payments received.
8. Payroll advances must not be counted twice when deducted from weekly payroll.
9. Inventory acquisition and project stock issue must not double-count expense.
10. Project expenses must remain distinguishable from company-level expenses.

## Data Integrity
Use foreign keys, NOT NULL constraints, unique constraints, CHECK constraints, and indexes where appropriate. Use transactions for multi-record financial operations. Use optimistic locking or other suitable concurrency protection where simultaneous updates could cause incorrect balances.

## Deletion and Audit
Avoid hard deletion of finalized financial records. Use controlled correction, reversal, cancellation, or write-off workflows with a reason and audit history where appropriate.

## Development Safety
Never run destructive database resets against a database containing data that must be preserved. Back up data before risky schema operations. Verify migrations against the actual PostgreSQL version and schema.

## Current Schema Status
Record which migration files exist, which have been applied, and which tables have been verified. Do not assume an earlier draft schema is present in the active database; inspect the repository and database first.