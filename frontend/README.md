# Sonduru Kedella — Financial Management (UI prototype)

Project-based financial management for Sonduru Kedella, a home design and construction company in Anuradhapura. This is the **UI design / prototype stage**: every screen works against in-browser mock data. There is no backend, database, authentication or real file storage yet.

## Run it

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # type-check + production build into dist/
npm run preview    # serve the production build
```

Node 18.18+ (Node 20 or 22 recommended).

## Stack

- React 18 + TypeScript + Vite 5
- Tailwind CSS v4 with the supplied shadcn preset theme (`src/index.css` — values copied exactly; additions are marked)
- shadcn/ui components (new-york style, Radix primitives) in `src/components/ui`
- Lucide icons, Recharts, Sonner toasts, react-day-picker, Zustand for the in-memory store
- Geist / Geist Mono via Fontsource (bundled, no external font requests)

> The preset code `b3kI323Ky` could not be resolved offline, so the theme tokens from the brief were applied by hand and Geist was assumed as the font. If you regenerate with `npx shadcn@latest init --preset b3kI323Ky`, keep the "additions" block in `src/index.css` (status colours, `primary-text`) — the app uses those tokens.

## Structure

```
src/
  data/types.ts        Data model (projects, installments, payments, write-offs, expenses, suppliers,
                       invoices, materials, stock movements, employees, attendance, overtime, advances,
                       payroll, commissions, settings)
  data/seed.ts         Fictional Sri Lankan sample data (today is fixed at 22 Oct 2026 for the demo)
  lib/finance.ts       ALL derived figures — receivables, project profitability, expense ledger,
                       payroll, advances, commissions, inventory. Every screen reads from here,
                       so dashboard, tables and reports always agree.
  lib/dates.ts         DD MMM YYYY formatting and UTC date maths
  lib/format.ts        LKR formatting
  store/store.ts       Zustand store + actions (record payment, write off, bill installment, …)
  components/ui/       shadcn/ui primitives
  components/app/      App components: shell, KPI strip, Money, StatusBadge, FigureList,
                       dialogs (payment, write-off, expense), FileDropzone, tables
  pages/               Dashboard, projects (list, create, detail + tabs), clients, revenue,
                       expenses, suppliers (+ invoice form), inventory, labour (employees,
                       attendance, overtime, advances, payroll), commissions, reports, settings
```

## Financial rules implemented

| Rule | Where |
| --- | --- |
| Contract value ≠ revenue ≠ cash | Revenue = billed installments (net of applied delay deduction) + other income; payments are separate records |
| An installment is revenue once, however many payments | `derive()` sums billed installments, never payments |
| Partial payments (clients, suppliers, expenses, commissions, payroll) | `PaymentDialog` + remaining recalculated from records |
| Overdue vs outstanding vs written off | `installmentStatus()`; write-off has its own dialog, reason, date and is never shown as received |
| Delay deduction kept apart from contract value | `calcDelay()` + Payments tab; configurable per-day / %-per-week / fixed, grace days, cap |
| Commission = contract value × recipient % | `ProjectCommissions`; design 80/20 policy is a setting; conflicts warn, never auto-adjust |
| Project vs company-level expenses | `Expense.scope`; company costs never assigned to a project |
| No double counting of stock | Stock purchases are not project costs; issues to a project are, at issue cost |
| Advances not counted twice | Advance deducted from weekly net; wages (gross) are the only labour cost |
| No cash/bank balances or cash-flow | Payment method is metadata only |

## Prototype behaviour

- Changes live in memory for the session. **Account menu → Reset sample data** restores the seed.
- Agreement PDF upload simulates progress and keeps the file in the browser session only (Preview/Download work for a file you uploaded; sample agreements have no file behind them).
- CSV export works in the browser; Excel export shows a "not connected yet" message.
- Search (`/`), notifications, theme toggle, attendance, payroll payments, commission confirmation, stock issues and every dialog are functional against the mock data.

## Next stage

Replace `store/store.ts` actions with API calls to the Java backend and move `lib/finance.ts` calculations server-side (keep this file as the reference for the formulas). Data types in `data/types.ts` map one-to-one to tables.
