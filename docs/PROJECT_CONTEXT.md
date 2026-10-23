# Sonduru Kedella — Project Context

## Product
Sonduru Kedella is a project-based financial management web application for a medium-sized, owner-operated home design and construction company in Anuradhapura, Sri Lanka.

## Objective
Replace Excel-based financial management with a centralized application that tracks project revenue, expenses, commissions, receivables, supplier payables, inventory, attendance, weekly payroll, and profitability.

## Revenue Categories
- Design
- Construction
- Consultation
- Other Income

## Confirmed Business Rules
- Design and construction projects can have multiple commission recipients with configurable percentages.
- Commission is based on the full agreed contract value, not merely receipts collected.
- Design has an initial 80% company allocation and 20% commission allocation policy; recipient arrangements must be configurable and validated.
- Consultation has no commission.
- Other income does not necessarily belong to a project.
- Construction projects support installment schedules, completion deadlines, agreement PDF uploads, and contract-specific delay deductions.
- Delay deductions must remain separate from original contract value and retention money.
- Supplier invoices support credit terms and partial payments.
- Client balances distinguish outstanding, overdue, and written-off debt.
- Labour uses attendance, daily wages, weekly payroll, configurable overtime rates, and advances.
- Inventory movements must not double-count costs in project profitability.
- Project expenses and company-level expenses remain separate.
- Legacy Excel import is not required initially.

## Initial Reports
- Income
- Expenses
- Project profitability
- Client receivables
- Supplier payables
- Labour payments

## Excluded Initially
- Live cash and bank balances
- Cash-flow reporting
- Bank reconciliation
- Agreement generation

## Confirmed Technical Decisions
- Frontend: React + TypeScript, existing shadcn-based UI.
- Backend: Java 25 LTS + Spring Boot 4.1.x, subject to stable version availability.
- Database: PostgreSQL.
- Persistence: Spring Data JPA.
- Database migrations: Flyway.
- Security: Spring Security.
- Build: Maven.
- Local infrastructure: Docker Compose.
- Architecture: Modular monolith.

## Working Rules
Inspect the actual source code before claiming a feature exists. Keep confirmed requirements separate from assumptions. Update this document when business decisions change.