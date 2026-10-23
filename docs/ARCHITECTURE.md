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

## Backend Layering
- Controller: HTTP request handling and response mapping.
- Service: business rules and transaction boundaries.
- Repository: database access.
- Entity: persisted domain model.
- DTO: API input and output contracts.
- Configuration: security, persistence, and infrastructure setup.

## Suggested Package Organization
com.sondurukedella
  common
  config
  security
  client
  project
  revenue
  expense
  commission
  supplier
  inventory
  payroll
  reporting

Organize each business module around its own controller, service, repository, entity, and DTOs where appropriate. Avoid creating empty packages for features that are not yet implemented.

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

## Frontend Integration
Preserve the existing React UI and its shadcn theme. Use typed API contracts and centralized API configuration. Keep frontend secrets out of browser-accessible environment variables.