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

Optional, with defaults, so not in .env.example: `AGREEMENTS_STORAGE_DIR` (default `data/agreements`, relative to the working directory; compose sets `/app/data/agreements`), `AGREEMENTS_MAX_SIZE` (default `20MB`) and `AGREEMENTS_MAX_REQUEST_SIZE` (default `21MB`). Raise the last two together.

Optional security settings, with defaults. `.env.example` was not changed when they were added (owner instruction for that task), so they are listed here:

| Variable | Default | Meaning |
| --- | --- | --- |
| `SESSION_COOKIE_SECURE` | `true` (compose: `false`) | Session and XSRF cookies only over HTTPS. Must be `true` in any deployment. |
| `SESSION_TIMEOUT` | `30m` | Idle time before a session ends. |
| `CORS_ALLOWED_ORIGINS` | empty (compose: `http://localhost:${FRONTEND_PORT}`) | Exact frontend origins, comma separated, that may call the API with cookies. Never `*`. |
| `LOGIN_MAX_FAILURES_PER_USERNAME` | `5` | Failed sign-ins per username per window. |
| `LOGIN_MAX_FAILURES_PER_ADDRESS` | `20` | Failed sign-ins per client address per window. |
| `LOGIN_FAILURE_WINDOW` | `15m` | The throttle window. |
| `BOOTSTRAP_SUPER_ADMIN_USERNAME` | none | One-time first Super Admin (see below). |
| `BOOTSTRAP_SUPER_ADMIN_PASSWORD` | none | One-time first Super Admin. Remove it after the first start. |
| `BOOTSTRAP_SUPER_ADMIN_DISPLAY_NAME` | `Super Admin` | Optional. |

## Authentication setup
The API needs a signed-in user for everything except `POST /api/v1/auth/login`, `GET /api/v1/auth/csrf` and `GET /actuator/health`. The roles, sign-in flow, sessions, CSRF and CORS are described in ARCHITECTURE.md, "Authentication and Authorization".

### First Super Admin (one time)
No account exists after the migrations, and nothing creates one by default. To create the first Super Admin:
1. Choose a username (3 to 50 characters: letters, digits, `.`, `-`, `_`) and a password of at least 12 characters. Don't reuse a password from elsewhere, and never write it into code, docs or commits.
2. Add `BOOTSTRAP_SUPER_ADMIN_USERNAME` and `BOOTSTRAP_SUPER_ADMIN_PASSWORD` (optionally `BOOTSTRAP_SUPER_ADMIN_DISPLAY_NAME`) to the root `.env`, or set them as environment variables of the backend in a deployment.
3. Start (or restart) the backend: `docker compose up -d --build backend`, or run it locally. The log says `Created the first Super Admin account "<username>"`. The password is never logged.
4. Remove `BOOTSTRAP_SUPER_ADMIN_PASSWORD` from `.env` or the environment, and restart if you like.

How it behaves:
- It only runs while no `SUPER_ADMIN` row exists at all, disabled ones included. Once one exists, the settings are ignored on every start: no second account, no password reset, nothing overwritten.
- If only one of the two variables is set, or the username or password breaks the rules, or the username already belongs to another account, the backend refuses to start and says which. The message never contains the values.
- With neither set and no Super Admin, the backend starts and logs a warning that nobody can manage users.
- Lost the only Super Admin password? Another Super Admin can reset it (`PUT /api/v1/users/{id}/password`). If there is none, an operator with database access must decide deliberately. The bootstrap will not run again, by design, and there is no reset endpoint.

### Managing accounts (Super Admin)
Every write needs the session cookie and the `X-XSRF-TOKEN` header.
- Create an Admin: `POST /api/v1/users` `{"username":"…","displayName":"…","password":"…"}`. `role` defaults to `ADMIN`; `"role":"SUPER_ADMIN"` creates another Super Admin. Give the person their password directly; they should change it with `PUT /api/v1/auth/me/password`.
- Deactivate or reactivate: `PUT /api/v1/users/{id}/status` `{"enabled":false,"version":N}`. Deactivation ends that user's sessions at once and blocks sign-in.
- Change a role: `PUT /api/v1/users/{id}/role` `{"role":"ADMIN","version":N}`. That user's sessions end.
- Reset a forgotten password: `PUT /api/v1/users/{id}/password` `{"newPassword":"…"}`. Returns 204; the password is never echoed. That user's sessions end.
- Edit the display name: `PUT /api/v1/users/{id}` `{"displayName":"…","version":N}`.
- List and view: `GET /api/v1/users?q=&role=&enabled=&page=&size=&sort=`, and `GET /api/v1/users/{id}`.
- Nobody can change their own role or status, or reset their own password, here. The last active Super Admin can't be demoted or deactivated.

### Trying it with curl (local, plain HTTP)
```
curl -c jar -b jar http://localhost:8080/api/v1/auth/csrf
TOKEN=$(awk '$6=="XSRF-TOKEN"{print $7}' jar)
curl -c jar -b jar -H "X-XSRF-TOKEN: $TOKEN" -H "Content-Type: application/json" \
     -d '{"username":"<your-username>","password":"<your-password>"}' http://localhost:8080/api/v1/auth/login
curl -b jar http://localhost:8080/api/v1/auth/me
```
The login response sets a new `XSRF-TOKEN` cookie. Read it from the jar again before the next POST or PUT.

### Deployment checklist
- HTTPS everywhere, `SESSION_COOKIE_SECURE=true` (the default).
- `CORS_ALLOWED_ORIGINS` set to the real frontend origin, or empty if the frontend is served from the same origin as the API.
- Bootstrap password removed from the environment once the first Super Admin exists.
- One backend instance, or sticky sessions plus a shared session store (not built). Sessions and the sign-in throttle are in memory.
- If a reverse proxy sits in front, decide deliberately whether to trust its forwarded headers (`server.forward-headers-strategy`). Until then, the per-address throttle sees only the proxy's address.

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

Do not use docker compose down -v as routine cleanup. It deletes both persistent volumes: `postgres_data` (the database) and `agreement_files` (uploaded agreement PDFs). Never delete persistent data without explicit approval and a confirmed backup. Back the two volumes up together, because agreement rows point at files in `agreement_files`.

The backend image must be rebuilt (`docker compose up -d --build backend`) before the agreement endpoints work in Docker. The image creates the writable `/app/data/agreements` directory that the volume mounts over.

## Backend Commands (Windows PowerShell)
From the backend directory:
  .\mvnw.cmd test
  .\mvnw.cmd clean package

Use commands appropriate to the actual project and verify the available Maven goals and configuration before assuming they work.

## Frontend Commands
Inspect frontend/package.json and use the package manager matching the existing lockfile. Do not replace the lockfile or install a second package manager without approval.

From `frontend/`: `npm run build` (typecheck + production build), `npm run typecheck`, and `npm test` (Vitest, `vitest run`; unit tests next to the code as `*.test.ts`). The tests need no backend or database.

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

Pending migrations are applied by Flyway when the backend starts. `V2__suppliers_active.sql`, `V3__inventory_ledger_additions.sql`, `V4__inventory_fifo_cost_layers.sql`, `V5__users_roles_and_security_events.sql` and `V6__commission_voids_and_guards.sql` have not been applied to the development database yet; they run, in order, on the next backend start or rebuild. V5 adds the user roles and `security_events`. Existing users become `ADMIN`, and it stops (applying nothing) if two usernames differ only in case. After V5, nobody can use the API until a user exists: provision the first Super Admin (see "Authentication setup"). V6 adds commission voids and freezes confirmed arrangements; it stops (applying nothing) if a project has two live arrangements for the same recipient and role. V2 to V4 are additive (new columns with defaults or NULLs, new constraints, indexes, tables and triggers), so existing data is kept. V4 also inserts one FIFO layer per material that holds stock, and refuses to run, applying nothing, if any material's stock ledger does not reconcile (see DATABASE.md, "FIFO migration of existing data"). Before applying any migration to the development database, test it on a throwaway PostgreSQL 17 database, both fresh and with representative data.
## Backend Tests
Run the tests with JDK 25 against a throwaway PostgreSQL 17 container, never the development database. Point `DB_HOST`, `DB_PORT`, `POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD` at the container; environment variables override `.env`. Some integration tests commit for real (`integration/database/*`) and delete only the rows they created. `RevenueConcurrencyIntegrationTest`, `ExpenseConcurrencyIntegrationTest`, `InventoryConcurrencyIntegrationTest` and `PayrollConcurrencyIntegrationTest` briefly disable `trg_client_payments_guard`, `trg_expense_payments_guard`, `trg_stock_movements_immutable`, `trg_stock_cost_allocations_immutable` and the payroll guards (`trg_payroll_payments_guard`, `trg_payroll_lines_immutable`, `trg_payroll_line_costs_immutable`, `trg_advance_recoveries_immutable`, `trg_salary_advances_guard`) during their own cleanup, because those rows cannot otherwise be deleted. `UserAdministrationConcurrencyIntegrationTest` does the same with `trg_security_events_immutable`, deleting only the `sectest-*` users it created and their events. `CommissionConcurrencyIntegrationTest` lifts `trg_commission_payments_guard` and `trg_commission_arrangements_guard` to delete its own `COMTEST-*` project's commission rows. That is another reason these tests must never run against a database whose data matters.

Security tests sign in for real (`support.TestUsers`): they create users with a test-only password in the throwaway database, and call `POST /api/v1/auth/login`. Tests that only need "some Admin" use `@WithMockUser(roles = "ADMIN")`. Tests that fail sign-ins on purpose use a random client address, because the sign-in throttle is shared by the whole test context. Spring's `csrf()` test helper replaces the CSRF filter's token store for the rest of the context; `AuthApiIntegrationTest` puts the real cookie store back before testing the real cookie-to-header flow.
