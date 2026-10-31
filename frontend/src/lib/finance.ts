/**
 * All financial figures are derived here from the raw records, so every screen agrees.
 *
 * Definitions (see the design system README):
 *  - Contract value: agreed with the client. Never changed by deductions.
 *  - Revenue recorded: installments billed, net of any applied delay deduction, plus other income.
 *  - Payments received: client payments actually recorded. A write-off is NOT a payment.
 *  - Receivable: revenue recorded − payments received − amounts written off.
 *  - Project costs: direct expenses + materials (direct-to-site invoices and stock issued to the project
 *    at issue cost — stock purchases themselves are never counted) + labour (attendance and approved
 *    overtime) + confirmed commission commitments (contract value × rate).
 *  - Profit to date: revenue recorded − written off − project costs. "Estimated" until the project is completed.
 *  - Salary advances are money already paid: deducted from the weekly net, never added as a second expense.
 */
import type {
  Advance, Client, ClientPayment, CommissionArrangement, CommissionPayment, DataState, Employee, Expense, ExpenseGroup,
  ExpenseScope, Installment, Material, OtherIncome, Project, StockMovement, Supplier, SupplierInvoice, SupplierPayment, WriteOff,
} from '@/data/types'
import { addDays, diffDays, formatRange, TODAY, weekStart as weekStartOf, monthKey } from './dates'
import { formatLKR } from './format'
import { replayFifo, stockOf, type CostLayer } from './fifo'

const sum = <T,>(xs: T[], f: (x: T) => number) => xs.reduce((t, x) => t + f(x), 0)
const r2 = (n: number) => Math.round(n * 100) / 100

function groupBy<T>(xs: T[], key: (x: T) => string | undefined) {
  const m = new Map<string, T[]>()
  for (const x of xs) {
    const k = key(x)
    if (k == null) continue
    const arr = m.get(k)
    if (arr) arr.push(x)
    else m.set(k, [x])
  }
  return m
}

// ---------------------------------------------------------------- receivables
export type ReceivableStatus = 'Upcoming' | 'Due' | 'Partially Paid' | 'Paid' | 'Overdue' | 'Written Off'

export interface InstallmentView {
  inst: Installment
  project: Project
  client: Client
  payments: ClientPayment[]
  writeOffs: WriteOff[]
  paid: number
  writtenOff: number
  deduction: number
  /** amount payable after deduction */
  net: number
  remaining: number
  status: ReceivableStatus
  daysOverdue: number
  daysToDue: number
}

// No separate due dates: anything still owed after the day it was billed is overdue.
function installmentStatus(billed: boolean, remaining: number, paid: number, writtenOff: number, billedOn: string): ReceivableStatus {
  if (!billed) return 'Upcoming'
  if (remaining <= 0.005) return writtenOff > 0 ? 'Written Off' : 'Paid'
  if (billedOn < TODAY) return 'Overdue'
  if (paid > 0) return 'Partially Paid'
  return 'Due'
}

// ---------------------------------------------------------------- expenses
export type ExpenseSource = 'Manual' | 'Supplier invoice' | 'Stock issue' | 'Payroll' | 'Commission'
export type PayStatus = 'Paid' | 'Partially Paid' | 'Unpaid' | 'Overdue' | 'From Stock'

export interface ExpenseLine {
  id: string
  source: ExpenseSource
  date: string
  scope: ExpenseScope
  projectId?: string
  group: ExpenseGroup
  category: string
  description: string
  payee: string
  amount: number
  paid: number
  remaining: number
  status: PayStatus
  dueDate?: string
  expense?: Expense
  refId: string
  method?: string
}

// Cash basis: one row per payment that actually went out, dated when it was paid. A stock issue is the one
// exception (fromStock): no new cash moves, but the project still carries the stock's cost, so it counts
// for the project and is left out of company totals, where the stock invoice payments already are.
export interface CashLine {
  id: string
  source: ExpenseSource | 'Advance'
  date: string
  scope: ExpenseScope
  projectId?: string
  group: ExpenseGroup
  category: string
  description: string
  payee: string
  amount: number
  method?: string
  fromStock?: boolean
}

export type ProfitBasis = 'accrual' | 'cash'

// ---------------------------------------------------------------- payroll
export interface PayrollLine {
  employee: Employee
  presentDays: number
  halfDays: number
  absentDays: number
  /** applicable attendance days: present + 0.5 × half days */
  days: number
  baseWages: number
  otHours: number
  otPendingHours: number
  otPay: number
  gross: number
  advanceDeduction: number
  advancesOutstanding: number
  otherDeduction: number
  otherNote?: string
  net: number
  paid: number
  remaining: number
  status: 'Draft' | 'Ready to Pay' | 'Partially Paid' | 'Paid'
  byProject: Record<string, number>
  adjusted: boolean
}
export interface PayrollWeek {
  weekStart: string
  weekEnd: string
  lines: PayrollLine[]
  gross: number
  advances: number
  other: number
  net: number
  paid: number
  remaining: number
  status: 'Draft' | 'Ready to Pay' | 'Partially Paid' | 'Paid'
  closed: boolean
}

export function computePayroll(s: Pick<DataState, 'employees' | 'attendance' | 'overtime' | 'advances' | 'payrollAdjustments' | 'payrollPayments'>): PayrollWeek[] {
  const first = s.attendance.reduce((m, a) => (a.date < m ? a.date : m), TODAY)
  const weeks: PayrollWeek[] = []
  const attByWeek = groupBy(s.attendance, (a) => weekStartOf(a.date))
  const otByWeek = groupBy(s.overtime, (o) => weekStartOf(o.date))
  const deductedSoFar = new Map<string, number>()
  const adj = new Map(s.payrollAdjustments.map((a) => [`${a.weekStart}:${a.employeeId}`, a]))
  const payBy = groupBy(s.payrollPayments, (p) => `${p.weekStart}:${p.employeeId}`)
  for (let ws = weekStartOf(first); ws <= weekStartOf(TODAY); ws = addDays(ws, 7)) {
    const we = addDays(ws, 5)
    const closed = we < TODAY
    const att = attByWeek.get(ws) ?? []
    const ots = otByWeek.get(ws) ?? []
    const lines: PayrollLine[] = []
    for (const e of s.employees) {
      const mine = att.filter((a) => a.employeeId === e.id)
      const myOt = ots.filter((o) => o.employeeId === e.id)
      if (!mine.length && !myOt.length) continue
      const presentDays = mine.filter((a) => a.status === 'Present').length
      const halfDays = mine.filter((a) => a.status === 'Half Day').length
      const absentDays = mine.filter((a) => a.status === 'Absent').length
      const days = presentDays + halfDays * 0.5
      const baseWages = days * e.dailyRate
      const approved = myOt.filter((o) => o.approved)
      const otHours = sum(approved, (o) => o.hours)
      const otPay = sum(approved, (o) => o.hours * o.rate)
      const byProject: Record<string, number> = {}
      for (const a of mine) {
        const v = a.status === 'Present' ? e.dailyRate : a.status === 'Half Day' ? e.dailyRate / 2 : 0
        if (v && a.projectId) byProject[a.projectId] = (byProject[a.projectId] ?? 0) + v
      }
      for (const o of approved) if (o.projectId) byProject[o.projectId] = (byProject[o.projectId] ?? 0) + o.hours * o.rate
      const gross = baseWages + otPay
      const advTotal = sum(s.advances.filter((a) => a.employeeId === e.id && a.date <= addDays(we, 1)), (a) => a.amount)
      const outstanding = Math.max(0, advTotal - (deductedSoFar.get(e.id) ?? 0))
      const a = adj.get(`${ws}:${e.id}`)
      const advanceDeduction = Math.max(0, Math.min(a?.advanceDeduction ?? outstanding, outstanding, gross))
      const otherDeduction = Math.max(0, a?.otherDeduction ?? 0)
      deductedSoFar.set(e.id, (deductedSoFar.get(e.id) ?? 0) + advanceDeduction)
      const net = r2(gross - advanceDeduction - otherDeduction)
      const paid = sum(payBy.get(`${ws}:${e.id}`) ?? [], (p) => p.amount)
      const remaining = r2(Math.max(0, net - paid))
      const status: PayrollLine['status'] = net > 0 && remaining <= 0 ? 'Paid' : paid > 0 ? 'Partially Paid' : closed ? 'Ready to Pay' : 'Draft'
      lines.push({
        employee: e, presentDays, halfDays, absentDays, days, baseWages, otHours, otPendingHours: sum(myOt.filter((o) => !o.approved), (o) => o.hours), otPay, gross,
        advanceDeduction, advancesOutstanding: outstanding, otherDeduction, otherNote: a?.otherNote, net, paid, remaining, status, byProject, adjusted: !!a,
      })
    }
    const gross = sum(lines, (l) => l.gross), net = sum(lines, (l) => l.net), paid = sum(lines, (l) => l.paid)
    const remaining = sum(lines, (l) => l.remaining)
    const status: PayrollWeek['status'] = lines.length && remaining <= 0 ? 'Paid' : paid > 0 ? 'Partially Paid' : closed ? 'Ready to Pay' : 'Draft'
    weeks.push({ weekStart: ws, weekEnd: we, lines, gross, advances: sum(lines, (l) => l.advanceDeduction), other: sum(lines, (l) => l.otherDeduction), net, paid, remaining, status, closed })
  }
  return weeks
}

export interface AdvanceView {
  advance: Advance
  employee: Employee
  deducted: number
  scheduled: number
  remaining: number
  allocations: { weekStart: string; amount: number; settled: boolean }[]
  status: 'Deducted' | 'Partly Deducted' | 'Scheduled' | 'Outstanding'
}

function computeAdvances(s: DataState, weeks: PayrollWeek[], empById: Map<string, Employee>): AdvanceView[] {
  const views: AdvanceView[] = s.advances
    .slice()
    .sort((a, b) => a.date.localeCompare(b.date))
    .map((a) => ({ advance: a, employee: empById.get(a.employeeId)!, deducted: 0, scheduled: 0, remaining: a.amount, allocations: [], status: 'Outstanding' }))
  for (const w of weeks) {
    for (const l of w.lines) {
      let left = l.advanceDeduction
      for (const v of views) {
        if (left <= 0) break
        if (v.advance.employeeId !== l.employee.id || v.remaining <= 0) continue
        const take = Math.min(left, v.remaining)
        const settled = l.paid > 0
        v.allocations.push({ weekStart: w.weekStart, amount: take, settled })
        if (settled) v.deducted += take
        else v.scheduled += take
        v.remaining -= take
        left -= take
      }
    }
  }
  for (const v of views) v.status = v.remaining <= 0 && v.scheduled === 0 ? 'Deducted' : v.deducted > 0 ? 'Partly Deducted' : v.scheduled > 0 ? 'Scheduled' : 'Outstanding'
  return views.reverse()
}

// ---------------------------------------------------------------- commissions
export interface CommissionView {
  arr: CommissionArrangement
  project: Project
  amount: number
  paid: number
  remaining: number
  payments: CommissionPayment[]
  status: 'Pending' | 'Confirmed' | 'Partially Paid' | 'Paid'
}
export interface CommissionPolicyCheck {
  totalRate: number
  limit?: number
  conflict: boolean
  message?: string
}

// ---------------------------------------------------------------- inventory
export interface MaterialView {
  material: Material
  qty: number
  value: number
  received: number
  issued: number
  returned: number
  adjusted: number
  status: 'In Stock' | 'Out of Stock'
  movements: StockMovement[]
  /** open FIFO cost layers, next to be used first */
  layers: CostLayer[]
  lastReceived?: string
}

// ---------------------------------------------------------------- suppliers
export interface InvoiceView {
  invoice: SupplierInvoice
  supplier: Supplier
  project?: Project
  total: number
  paid: number
  remaining: number
  payments: SupplierPayment[]
  status: 'Paid' | 'Partially Paid' | 'Unpaid' | 'Overdue'
  daysToDue: number
}

// ---------------------------------------------------------------- projects
export interface ProjectFinance {
  project: Project
  client: Client
  installments: InstallmentView[]
  contractValue: number
  deduction: number
  adjustedValue: number
  scheduledTotal: number
  revenueRecorded: number
  received: number
  writtenOff: number
  receivable: number
  overdue: number
  unbilled: number
  costs: {
    materialsDirect: number
    materialsStock: number
    labour: number
    expensesByCategory: Record<string, number>
    expenses: number
    commission: number
  }
  directCosts: number
  totalCosts: number
  commissionCommitted: number
  commissionPaid: number
  commissionPendingRate: number
  payables: { expenses: number; suppliers: number; commission: number; total: number }
  /** the same project on the cash basis: money received from the client less money paid out for it */
  cash: { received: number; materials: number; labour: number; expenses: number; commission: number; costs: number; profit: number }
  profit: number
  profitBasis: 'Recorded' | 'Estimated'
  margin: number
  collectedPct: number
  attention: string[]
}

export interface DelayCalc {
  daysLate: number
  chargeableDays: number
  calculated: number
  cap?: number
  capped: boolean
  final: number
  explanation: string
}

/** Delay deduction under the configured agreement terms. */
export function calcDelay(p: Project, completionDate?: string): DelayCalc | null {
  const d = p.delay
  if (!d || !d.enabled) return null
  const end = completionDate ?? d.actualCompletion ?? p.actualCompletion ?? TODAY
  const daysLate = Math.max(0, diffDays(end, d.contractDeadline))
  const chargeableDays = Math.max(0, daysLate - (d.graceDays ?? 0))
  let calculated = 0
  let explanation = ''
  if (d.rule === 'per_day') {
    calculated = chargeableDays * (d.ratePerDay ?? 0)
    explanation = `${chargeableDays} chargeable day${chargeableDays === 1 ? '' : 's'} × ${formatLKR(d.ratePerDay ?? 0, { decimals: 0 })} per day`
  } else if (d.rule === 'percent_per_week') {
    const weeks = Math.floor(chargeableDays / 7)
    calculated = (p.contractValue * (d.percentPerWeek ?? 0) * weeks) / 100
    explanation = `${weeks} full week${weeks === 1 ? '' : 's'} × ${d.percentPerWeek}% of the contract value`
  } else {
    calculated = chargeableDays > 0 ? (d.fixedAmount ?? 0) : 0
    explanation = chargeableDays > 0 ? `Fixed deduction of ${formatLKR(d.fixedAmount ?? 0, { decimals: 0 })} once late` : 'Not late — no fixed deduction'
  }
  if (d.graceDays) explanation += ` (after ${d.graceDays} grace days)`
  const cap = d.maxDeduction
  const capped = cap != null && calculated > cap
  return { daysLate, chargeableDays, calculated: r2(calculated), cap, capped, final: r2(capped ? cap! : calculated), explanation }
}

// ---------------------------------------------------------------- derive everything
export function derive(s: DataState) {
  const clientById = new Map(s.clients.map((c) => [c.id, c]))
  const projectById = new Map(s.projects.map((p) => [p.id, p]))
  const supplierById = new Map(s.suppliers.map((x) => [x.id, x]))
  const empById = new Map(s.employees.map((e) => [e.id, e]))
  const materialById = new Map(s.materials.map((m) => [m.id, m]))

  // installments
  const payByInst = groupBy(s.clientPayments, (p) => p.installmentId)
  const woByInst = groupBy(s.writeOffs, (w) => w.installmentId)
  const installments: InstallmentView[] = s.installments.map((inst) => {
    const project = projectById.get(inst.projectId)!
    const client = clientById.get(project.clientId)!
    const payments = (payByInst.get(inst.id) ?? []).slice().sort((a, b) => a.date.localeCompare(b.date))
    const writeOffs = woByInst.get(inst.id) ?? []
    const paid = sum(payments, (p) => p.amount)
    const writtenOff = sum(writeOffs, (w) => w.amount)
    const deduction = project.delay?.appliedAmount && project.delay.appliedToInstallmentId === inst.id ? project.delay.appliedAmount : 0
    const net = inst.amount - deduction
    const remaining = r2(Math.max(0, net - paid - writtenOff))
    const status = installmentStatus(inst.billed, remaining, paid, writtenOff, inst.billedOn ?? inst.dueDate)
    return {
      inst, project, client, payments, writeOffs, paid, writtenOff, deduction, net, remaining, status,
      daysOverdue: status === 'Overdue' ? diffDays(TODAY, inst.billedOn ?? inst.dueDate) : 0,
      daysToDue: diffDays(inst.dueDate, TODAY),
    }
  })
  const instByProject = groupBy(installments, (i) => i.inst.projectId)

  // supplier invoices
  const spByInv = groupBy(s.supplierPayments, (p) => p.invoiceId)
  const invoices: InvoiceView[] = s.supplierInvoices.map((invoice) => {
    const total = sum(invoice.lines, (l) => l.qty * l.unitPrice)
    const payments = spByInv.get(invoice.id) ?? []
    const paid = sum(payments, (p) => p.amount)
    const remaining = r2(Math.max(0, total - paid))
    const status: InvoiceView['status'] = remaining <= 0 ? 'Paid' : invoice.dueDate < TODAY ? 'Overdue' : paid > 0 ? 'Partially Paid' : 'Unpaid'
    return { invoice, supplier: supplierById.get(invoice.supplierId)!, project: invoice.projectId ? projectById.get(invoice.projectId) : undefined, total, paid, remaining, payments, status, daysToDue: diffDays(invoice.dueDate, TODAY) }
  })

  // inventory
  // stock is valued FIFO: quantity and value are what is left in the material's cost layers
  const fifo = replayFifo(s.stockMovements)
  const movByMat = groupBy(s.stockMovements, (m) => m.materialId)
  const layersByMat = groupBy(fifo.layers, (l) => l.materialId)
  const materials: MaterialView[] = s.materials.map((material) => {
    const movements = (movByMat.get(material.id) ?? []).slice().sort((a, b) => b.date.localeCompare(a.date))
    const { qty, value } = stockOf(fifo, material.id)
    const received = sum(movements.filter((m) => m.type === 'Received'), (m) => m.qty)
    const issued = -sum(movements.filter((m) => m.type === 'Issued'), (m) => m.qty)
    const returned = sum(movements.filter((m) => m.type === 'Returned'), (m) => m.qty)
    const adjusted = sum(movements.filter((m) => m.type === 'Adjustment'), (m) => m.qty)
    // no reorder levels (owner decision): a material either has stock or it doesn't
    const status: MaterialView['status'] = qty <= 0 ? 'Out of Stock' : 'In Stock'
    const layers = (layersByMat.get(material.id) ?? []).filter((l) => l.remainingQty > 0)
    return { material, qty, value, received, issued, returned, adjusted, status, movements, layers, lastReceived: movements.find((m) => m.type === 'Received')?.date }
  })

  // payroll
  const payroll = computePayroll(s)
  const advances = computeAdvances(s, payroll, empById)

  // commissions
  const cpByArr = groupBy(s.commissionPayments, (p) => p.arrangementId)
  const commissions: CommissionView[] = s.commissions.map((arr) => {
    const project = projectById.get(arr.projectId)!
    const amount = r2((project.contractValue * arr.rate) / 100)
    const payments = cpByArr.get(arr.id) ?? []
    const paid = sum(payments, (p) => p.amount)
    const remaining = r2(Math.max(0, amount - paid))
    const status: CommissionView['status'] = arr.status === 'Pending' ? 'Pending' : remaining <= 0 ? 'Paid' : paid > 0 ? 'Partially Paid' : 'Confirmed'
    return { arr, project, amount, paid, remaining, payments, status }
  })
  const commByProject = groupBy(commissions, (c) => c.arr.projectId)
  function policyCheck(projectId: string, rates?: number[]): CommissionPolicyCheck {
    const p = projectById.get(projectId)
    const list = rates ?? (commByProject.get(projectId) ?? []).map((c) => c.arr.rate)
    const totalRate = r2(list.reduce((t, r) => t + r, 0))
    if (!p) return { totalRate, conflict: false }
    if (p.type === 'Design') {
      const limit = s.settings.commission.designCommissionPool
      const conflict = totalRate > limit
      return { totalRate, limit, conflict, message: conflict ? `Shares total ${totalRate}% — above the ${limit}% design commission policy (company share ${s.settings.commission.designCompanyShare}%).` : undefined }
    }
    const limit = s.settings.commission.constructionDefaultRate
    if (limit != null && totalRate > limit) return { totalRate, limit, conflict: true, message: `Shares total ${totalRate}% — above the configured ${limit}% construction default.` }
    return { totalRate, limit, conflict: false }
  }

  // expense ledger
  const epByExp = groupBy(s.expensePayments, (p) => p.expenseId)
  const expenseLines: ExpenseLine[] = []
  for (const e of s.expenses) {
    const pays = epByExp.get(e.id) ?? []
    const paid = sum(pays, (p) => p.amount)
    const remaining = r2(Math.max(0, e.amount - paid))
    const status: PayStatus = remaining <= 0 ? 'Paid' : e.dueDate && e.dueDate < TODAY ? 'Overdue' : paid > 0 ? 'Partially Paid' : 'Unpaid'
    expenseLines.push({ id: e.id, source: 'Manual', date: e.date, scope: e.scope, projectId: e.projectId, group: e.group, category: e.category, description: e.description, payee: e.payee, amount: e.amount, paid, remaining, status, dueDate: e.dueDate, expense: e, refId: e.id, method: pays[0]?.method })
  }
  for (const v of invoices) {
    if (!v.invoice.projectId) continue
    expenseLines.push({
      id: `inv:${v.invoice.id}`, source: 'Supplier invoice', date: v.invoice.date, scope: 'Project', projectId: v.invoice.projectId, group: 'Construction', category: 'Materials',
      description: `${v.invoice.lines.map((l) => l.description).join(', ')} — ${v.invoice.invoiceNo}`, payee: v.supplier.name, amount: v.total, paid: v.paid, remaining: v.remaining,
      status: v.status === 'Overdue' ? 'Overdue' : v.status, dueDate: v.invoice.dueDate, refId: v.invoice.id,
    })
  }
  // an issue is charged at its FIFO cost; a return takes that same cost back off the project
  for (const m of s.stockMovements) {
    if ((m.type !== 'Issued' && m.type !== 'Returned') || !m.projectId) continue
    const mat = materialById.get(m.materialId)!
    const amount = -fifo.costed.get(m.id)!.totalCost
    const what = m.type === 'Issued' ? `${-m.qty} ${mat.unit} issued from stock` : `${m.qty} ${mat.unit} returned to stock`
    expenseLines.push({
      id: `iss:${m.id}`, source: 'Stock issue', date: m.date, scope: 'Project', projectId: m.projectId, group: 'Construction', category: 'Materials',
      description: `${mat.name} — ${what}${m.reference ? ` (${m.reference})` : ''}`, payee: 'Company stock', amount, paid: amount, remaining: 0, status: 'From Stock', refId: m.id,
    })
  }
  for (const w of payroll) {
    const perProject: Record<string, number> = {}
    const workers: Record<string, number> = {}
    for (const l of w.lines) for (const [pid, v] of Object.entries(l.byProject)) { perProject[pid] = (perProject[pid] ?? 0) + v; workers[pid] = (workers[pid] ?? 0) + 1 }
    const ratio = w.net > 0 ? Math.min(1, w.paid / w.net) : 0
    for (const [pid, amount] of Object.entries(perProject)) {
      const paid = Math.round(amount * ratio)
      const remaining = r2(amount - paid)
      expenseLines.push({
        id: `pay:${w.weekStart}:${pid}`, source: 'Payroll', date: w.weekEnd < TODAY ? w.weekEnd : TODAY, scope: 'Project', projectId: pid, group: 'Construction', category: 'Labour',
        description: `Wages & overtime, ${formatRange(w.weekStart, w.weekEnd)} (${workers[pid]} worker${workers[pid] === 1 ? '' : 's'})${w.closed ? '' : ' — week in progress'}`, payee: 'Site workers',
        amount: r2(amount), paid, remaining, status: remaining <= 0.5 ? 'Paid' : paid > 0 ? 'Partially Paid' : 'Unpaid', refId: w.weekStart,
      })
    }
  }
  for (const c of commissions) {
    if (c.arr.status !== 'Confirmed') continue
    expenseLines.push({
      id: `com:${c.arr.id}`, source: 'Commission', date: c.arr.confirmedOn ?? c.project.startDate, scope: 'Project', projectId: c.project.id,
      group: c.project.type === 'Design' ? 'Design' : 'Construction', category: 'Commission', description: `Commission ${c.arr.rate}% — ${c.arr.recipient} (${c.arr.role})`,
      payee: c.arr.recipient, amount: c.amount, paid: c.paid, remaining: c.remaining, status: c.remaining <= 0 ? 'Paid' : c.paid > 0 ? 'Partially Paid' : 'Unpaid', refId: c.arr.id,
    })
  }
  expenseLines.sort((a, b) => b.date.localeCompare(a.date))
  const linesByProject = groupBy(expenseLines, (l) => l.projectId)

  // cash ledger: what was paid, when it was paid
  const expenseById = new Map(s.expenses.map((e) => [e.id, e]))
  const invoiceById = new Map(invoices.map((v) => [v.invoice.id, v]))
  const payrollLineOf = new Map(payroll.flatMap((w) => w.lines.map((l) => [`${w.weekStart}:${l.employee.id}`, { w, l }] as const)))
  const cashLines: CashLine[] = []
  for (const p of s.expensePayments) {
    const e = expenseById.get(p.expenseId)
    if (!e) continue
    cashLines.push({ id: `ep:${p.id}`, source: 'Manual', date: p.date, scope: e.scope, projectId: e.projectId, group: e.group, category: e.category, description: e.description, payee: e.payee, amount: p.amount, method: p.method })
  }
  for (const p of s.supplierPayments) {
    const v = invoiceById.get(p.invoiceId)
    if (!v) continue
    // paying for stock is company money; it reaches a project later, when the stock is issued
    const toStock = !v.invoice.projectId
    cashLines.push({
      id: `sp:${p.id}`, source: 'Supplier invoice', date: p.date, scope: toStock ? 'Company' : 'Project', projectId: v.invoice.projectId, group: toStock ? 'Company' : 'Construction',
      category: toStock ? 'Stock purchases' : 'Materials', description: `${v.invoice.lines.map((l) => l.description).join(', ')} — ${v.invoice.invoiceNo}`, payee: v.supplier.name, amount: p.amount, method: p.method,
    })
  }
  for (const l of expenseLines) {
    if (l.source !== 'Stock issue') continue
    cashLines.push({ id: l.id, source: l.source, date: l.date, scope: l.scope, projectId: l.projectId, group: l.group, category: l.category, description: l.description, payee: l.payee, amount: l.amount, fromStock: true })
  }
  // a wage payment is split over the projects the worker was on that week, in proportion to the wages earned there
  for (const p of s.payrollPayments) {
    const hit = payrollLineOf.get(`${p.weekStart}:${p.employeeId}`)
    const who = empById.get(p.employeeId)?.name ?? 'Worker'
    const description = `Wages, ${hit ? formatRange(hit.w.weekStart, hit.w.weekEnd) : p.weekStart} — ${who}`
    const parts = Object.entries(hit?.l.byProject ?? {}).filter(([, v]) => v > 0)
    const base = sum(parts, ([, v]) => v)
    if (!base) {
      cashLines.push({ id: `pp:${p.id}`, source: 'Payroll', date: p.date, scope: 'Company', group: 'Company', category: 'Labour', description, payee: who, amount: p.amount, method: p.method })
      continue
    }
    let left = p.amount
    parts.forEach(([pid, v], i) => {
      // the last share takes the rounding remainder so the split adds back to the payment
      const amount = i === parts.length - 1 ? r2(left) : r2((p.amount * v) / base)
      left -= amount
      cashLines.push({ id: `pp:${p.id}:${pid}`, source: 'Payroll', date: p.date, scope: 'Project', projectId: pid, group: 'Construction', category: 'Labour', description, payee: who, amount, method: p.method })
    })
  }
  // an advance is cash out the day it's given; it isn't tied to a project, and the later deduction just pays less wages
  for (const a of s.advances) {
    const who = empById.get(a.employeeId)?.name ?? 'Worker'
    cashLines.push({ id: `adv:${a.id}`, source: 'Advance', date: a.date, scope: 'Company', group: 'Company', category: 'Salary advances', description: `Salary advance — ${who}`, payee: who, amount: a.amount, method: a.method })
  }
  for (const c of commissions) {
    for (const p of c.payments) {
      cashLines.push({
        id: `cp:${p.id}`, source: 'Commission', date: p.date, scope: 'Project', projectId: c.project.id, group: c.project.type === 'Design' ? 'Design' : 'Construction',
        category: 'Commission', description: `Commission ${c.arr.rate}% — ${c.arr.recipient} (${c.arr.role})`, payee: c.arr.recipient, amount: p.amount, method: p.method,
      })
    }
  }
  cashLines.sort((a, b) => b.date.localeCompare(a.date))
  const cashByProject = groupBy(cashLines, (l) => l.projectId)

  // projects
  const projects: ProjectFinance[] = s.projects.map((project) => {
    const insts = (instByProject.get(project.id) ?? []).slice().sort((a, b) => a.inst.dueDate.localeCompare(b.inst.dueDate))
    const lines = linesByProject.get(project.id) ?? []
    const deduction = project.delay?.appliedAmount ?? 0
    const revenueRecorded = sum(insts.filter((i) => i.inst.billed), (i) => i.net)
    const received = sum(insts, (i) => i.paid)
    const writtenOff = sum(insts, (i) => i.writtenOff)
    const receivable = sum(insts.filter((i) => i.inst.billed), (i) => i.remaining)
    const overdue = sum(insts.filter((i) => i.status === 'Overdue'), (i) => i.remaining)
    const scheduledTotal = sum(insts, (i) => i.inst.amount)
    const unbilled = sum(insts.filter((i) => !i.inst.billed), (i) => i.net)
    const expensesByCategory: Record<string, number> = {}
    for (const l of lines.filter((l) => l.source === 'Manual')) expensesByCategory[l.category] = (expensesByCategory[l.category] ?? 0) + l.amount
    const materialsDirect = sum(lines.filter((l) => l.source === 'Supplier invoice'), (l) => l.amount)
    const materialsStock = sum(lines.filter((l) => l.source === 'Stock issue'), (l) => l.amount)
    const labour = sum(lines.filter((l) => l.source === 'Payroll'), (l) => l.amount)
    const expenses = sum(lines.filter((l) => l.source === 'Manual'), (l) => l.amount)
    const pc = commByProject.get(project.id) ?? []
    const commissionCommitted = sum(pc.filter((c) => c.arr.status === 'Confirmed'), (c) => c.amount)
    const commissionPaid = sum(pc, (c) => c.paid)
    const directCosts = materialsDirect + materialsStock + labour + expenses
    const totalCosts = directCosts + commissionCommitted
    const payables = {
      expenses: sum(lines.filter((l) => l.source === 'Manual'), (l) => l.remaining),
      suppliers: sum(lines.filter((l) => l.source === 'Supplier invoice'), (l) => l.remaining),
      commission: sum(pc.filter((c) => c.arr.status === 'Confirmed'), (c) => c.remaining),
      total: 0,
    }
    payables.total = payables.expenses + payables.suppliers + payables.commission
    const profit = revenueRecorded - writtenOff - totalCosts
    const cl = cashByProject.get(project.id) ?? []
    const cashOf = (...src: CashLine['source'][]) => r2(sum(cl.filter((l) => src.includes(l.source)), (l) => l.amount))
    const cashCosts = r2(sum(cl, (l) => l.amount))
    const cash = {
      received, materials: cashOf('Supplier invoice', 'Stock issue'), labour: cashOf('Payroll'), expenses: cashOf('Manual'), commission: cashOf('Commission'),
      costs: cashCosts, profit: r2(received - cashCosts),
    }
    const adjustedValue = project.contractValue - deduction
    const attention: string[] = []
    const ovCount = insts.filter((i) => i.status === 'Overdue').length
    if (ovCount) attention.push(`${formatLKR(overdue, { decimals: 0 })} overdue`)
    if (project.status !== 'Completed' && project.status !== 'Cancelled' && project.expectedCompletion < TODAY) attention.push('Past expected completion')
    else if (project.status === 'In Progress' && diffDays(project.expectedCompletion, TODAY) <= 14) attention.push(`Due for completion ${diffDays(project.expectedCompletion, TODAY)} days from now`)
    if (profit < 0) attention.push('Costs exceed revenue recorded')
    if (policyCheck(project.id).conflict) attention.push('Commission shares above policy')
    if (project.status === 'On Hold') attention.push('On hold')
    if (!project.agreement && project.type !== 'Consultation') attention.push('No agreement uploaded')
    return {
      project, client: clientById.get(project.clientId)!, installments: insts, contractValue: project.contractValue, deduction, adjustedValue, scheduledTotal,
      revenueRecorded, received, writtenOff, receivable, overdue, unbilled,
      costs: { materialsDirect, materialsStock, labour, expensesByCategory, expenses, commission: commissionCommitted },
      directCosts, totalCosts, commissionCommitted, commissionPaid,
      commissionPendingRate: sum(pc.filter((c) => c.arr.status === 'Pending'), (c) => c.arr.rate),
      payables, cash, profit, profitBasis: project.status === 'Completed' ? 'Recorded' : 'Estimated',
      margin: revenueRecorded > 0 ? (profit / revenueRecorded) * 100 : 0,
      collectedPct: adjustedValue > 0 ? (received / adjustedValue) * 100 : 0,
      attention,
    }
  })
  const projectFinById = new Map(projects.map((p) => [p.project.id, p]))

  // other income
  const oirByInc = groupBy(s.otherIncomeReceipts, (r) => r.incomeId)
  const otherIncome = s.otherIncome.map((inc: OtherIncome) => {
    const receipts = oirByInc.get(inc.id) ?? []
    const received = sum(receipts, (r) => r.amount)
    const remaining = Math.max(0, inc.amount - received)
    const status: ReceivableStatus = remaining <= 0 ? 'Paid' : inc.date < TODAY ? 'Overdue' : received > 0 ? 'Partially Paid' : 'Due'
    return { income: inc, receipts, received, remaining, status }
  })

  // totals
  // "active" means work is going on: on-hold projects are left out (owner decision)
  const active = projects.filter((p) => p.project.status === 'In Progress')
  const billed = installments.filter((i) => i.inst.billed)
  const receivables = billed.filter((i) => i.remaining > 0)
  const totals = {
    activeProjects: active.length,
    activeByType: {
      Design: active.filter((p) => p.project.type === 'Design').length,
      Construction: active.filter((p) => p.project.type === 'Construction').length,
      Consultation: active.filter((p) => p.project.type === 'Consultation').length,
    },
    activeContractValue: sum(active, (p) => p.contractValue),
    revenueProjects: sum(billed, (i) => i.net),
    revenueOther: sum(otherIncome, (o) => o.income.amount),
    received: sum(s.clientPayments, (p) => p.amount),
    receivedOther: sum(otherIncome, (o) => o.received),
    receivable: sum(receivables, (i) => i.remaining) + sum(otherIncome, (o) => o.remaining),
    overdue: sum(receivables.filter((i) => i.status === 'Overdue'), (i) => i.remaining) + sum(otherIncome.filter((o) => o.status === 'Overdue'), (o) => o.remaining),
    writtenOff: sum(s.writeOffs, (w) => w.amount),
    projectCosts: sum(projects, (p) => p.totalCosts),
    companyExpenses: sum(expenseLines.filter((l) => l.scope === 'Company'), (l) => l.amount),
    projectProfit: sum(projects, (p) => p.profit),
    supplierPayables: sum(invoices, (v) => v.remaining),
    expensePayables: sum(expenseLines.filter((l) => l.source === 'Manual'), (l) => l.remaining),
    commissionPayables: sum(commissions.filter((c) => c.arr.status === 'Confirmed'), (c) => c.remaining),
    payrollPayables: sum(payroll.filter((w) => w.closed), (w) => w.remaining),
    stockValue: r2(sum(materials, (m) => m.value)),
    // cash basis. Stock issues are left out of the company total: the stock invoice payments are the cash
    projectCashProfit: r2(sum(projects, (p) => p.cash.profit)),
    cashPaidOut: r2(sum(cashLines.filter((l) => !l.fromStock), (l) => l.amount)),
  }
  const revenueRecorded = totals.revenueProjects + totals.revenueOther
  const paymentsReceived = totals.received + totals.receivedOther

  // monthly series (financial year to date)
  const monthly = new Map<string, { revenue: number; expenses: number; received: number }>()
  const bump = (k: string, f: 'revenue' | 'expenses' | 'received', v: number) => {
    const m = monthly.get(k) ?? { revenue: 0, expenses: 0, received: 0 }
    m[f] += v
    monthly.set(k, m)
  }
  for (const i of billed) bump(monthKey(i.inst.billedOn ?? i.inst.dueDate), 'revenue', i.net)
  for (const o of otherIncome) bump(monthKey(o.income.date), 'revenue', o.income.amount)
  for (const p of s.clientPayments) bump(monthKey(p.date), 'received', p.amount)
  for (const l of expenseLines) bump(monthKey(l.date), 'expenses', l.amount)

  // upcoming payables (next 14 days and overdue)
  const payablesDue = [
    ...invoices.filter((v) => v.remaining > 0).map((v) => ({ id: v.invoice.id, kind: 'Supplier invoice' as const, payee: v.supplier.name, ref: v.invoice.invoiceNo, projectId: v.invoice.projectId, dueDate: v.invoice.dueDate, amount: v.remaining, status: v.status })),
    ...expenseLines.filter((l) => l.source === 'Manual' && l.remaining > 0 && l.dueDate).map((l) => ({ id: l.id, kind: 'Expense' as const, payee: l.payee, ref: l.category, projectId: l.projectId, dueDate: l.dueDate!, amount: l.remaining, status: l.status })),
  ].sort((a, b) => a.dueDate.localeCompare(b.dueDate))

  return {
    s, clientById, projectById, supplierById, empById, materialById,
    // FIFO cost of every stock movement, keyed by movement id
    stockCost: fifo.costed,
    installments, invoices, materials, payroll, advances, commissions, policyCheck, expenseLines, cashLines, projects, projectFinById, otherIncome,
    totals: { ...totals, revenueRecorded, paymentsReceived },
    monthly, payablesDue,
  }
}

export type Derived = ReturnType<typeof derive>

/** A project's figures on either basis, so a screen can switch between them without caring which. */
export function projectFigures(p: ProjectFinance, basis: ProfitBasis) {
  if (basis === 'cash') {
    const c = p.cash
    return { revenue: c.received, materials: c.materials, labour: c.labour, expenses: c.expenses, commission: c.commission, costs: c.costs, profit: c.profit, margin: c.received > 0 ? (c.profit / c.received) * 100 : 0 }
  }
  return {
    revenue: p.revenueRecorded - p.writtenOff, materials: p.costs.materialsDirect + p.costs.materialsStock, labour: p.costs.labour, expenses: p.costs.expenses,
    commission: p.commissionCommitted, costs: p.totalCosts, profit: p.profit, margin: p.margin,
  }
}

export function clientSummary(d: Derived, clientId: string) {
  const projects = d.projects.filter((p) => p.client.id === clientId)
  const insts = d.installments.filter((i) => i.client.id === clientId)
  return {
    projects,
    installments: insts,
    contractValue: sum(projects, (p) => p.contractValue),
    revenueRecorded: sum(projects, (p) => p.revenueRecorded),
    received: sum(projects, (p) => p.received),
    writtenOff: sum(projects, (p) => p.writtenOff),
    receivable: sum(projects, (p) => p.receivable),
    overdue: sum(projects, (p) => p.overdue),
  }
}

/** Project activity, derived from records plus logged notes. */
// The project's timeline, newest first. The backend writes an entry for every change (billing, payments,
// write-offs, expenses, stock issues, commissions, documents, status), so the log is the only source -
// building entries from the records as well would show each event twice.
export function projectActivity(d: Derived, projectId: string) {
  const out: { id: string; date: string; kind: string; text: string; amount?: number }[] = []
  if (!d.projectFinById.get(projectId)) return out
  const entries = d.s.activity.filter((a) => a.projectId === projectId)
  // same date: the later recording first, then the higher id (numbers, not text: 100 comes after 99)
  entries.sort((a, b) => b.date.localeCompare(a.date) || (b.createdAt ?? '').localeCompare(a.createdAt ?? '') || Number(b.id) - Number(a.id))
  for (const a of entries) out.push({ id: a.id, date: a.date, kind: a.kind, text: a.text })
  return out
}

export { sum }
