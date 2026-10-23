# Development Workflow

## Repository Layout
- backend/: Spring Boot Maven application.
- frontend/: existing React application.
- docs/: persistent project documentation.
- compose.yaml: local Docker Compose configuration.
- .env: local, untracked environment configuration.
- .env.example: tracked template without secrets.

## Environment Variables
Read .env.example for required variable names. Keep actual development credentials in the root .env file and never commit it. Claude Code is authorized to edit .env when required for implementation, but must preserve valid existing credentials unless a change is necessary. Do not print secrets, include them in logs, or copy them into documentation. Ask before rotating credentials that may already be in use.

## Docker Commands
Validate configuration:
  docker compose config --quiet

Start services:
  docker compose up -d --build

Check services:
  docker compose ps

View service logs:
  docker compose logs --tail=100

Follow backend logs:
  docker compose logs -f backend

Stop services without deleting persistent volumes:
  docker compose down

Do not use docker compose down -v as routine cleanup. Never delete persistent data without explicit approval and a confirmed backup.

## Backend Commands (Windows PowerShell)
From the backend directory:
  .\mvnw.cmd test
  .\mvnw.cmd clean package

Use commands appropriate to the actual project and verify the available Maven goals and configuration before assuming they work.

## Frontend Commands
Inspect frontend/package.json and use the package manager matching the existing lockfile. Do not replace the lockfile or install a second package manager without approval.

## Before Every Change
1. Inspect the relevant source and Git status.
2. Understand existing behavior.
3. Implement the smallest complete change.
4. Add or update tests.
5. Run relevant tests and builds.
6. Report what was verified and what was not.
7. Update implementation status and relevant documentation.

## Secrets and Destructive Operations
Do not commit .env or print its values. Do not run destructive database operations, remove volumes, reset migrations, or delete user files without explicit authorization.

## API and Database Changes
Keep API contracts compatible with the existing frontend or explicitly document required frontend changes. Add new Flyway migrations for schema changes. Never claim a migration or endpoint works unless it has been tested.