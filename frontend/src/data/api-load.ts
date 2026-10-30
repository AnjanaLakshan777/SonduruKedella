// Reads everything the pages need from the backend and maps it onto the DataState the UI was built
// around, so lib/finance.ts can keep deriving the figures the same way. Voided and cancelled rows are
// left out - the local model has no such thing, and they never count anyway.
import type {
  ActivityEntry, Advance, Attendance, Client, ClientPayment, CommissionArrangement, CommissionPayment, DataState, DelayTerms, Employee,
  Expense, ExpenseGroup, ExpensePayment, Installment, Material, OtherIncome, OtherIncomeReceipt, Overtime, PayrollAdjustment,
  PayrollPayment, Project, ProjectStatus, ProjectType, Settings, StockMovement, Supplier, SupplierInvoice, SupplierPayment, WriteOff,
} from './types'
import { apiGet, apiGetAll, apiGetOptional } from '@/lib/api'
import { addDays, TODAY } from '@/lib/dates'

/* eslint-disable @typescript-eslint/no-explicit-any */
type Row = any

const id = (n: number | null | undefined) => (n == null ? undefined : String(n))
const day = (ts: string) => ts.slice(0, 10)
const opt = <T,>(v: T | null | undefined) => (v == null || v === '' ? undefined : v)
// SUPER_ADMIN -> Super Admin, IN_PROGRESS -> In Progress, HALF_DAY -> Half Day
const label = (code: string) => code.toLowerCase().split('_').map((w) => w[0].toUpperCase() + w.slice(1)).join(' ')

export const PROJECT_STATUSES: ProjectStatus[] = ['Planning', 'In Progress', 'On Hold', 'Completed', 'Cancelled']

function settingsFrom(o: Row): Settings {
  const groups: Record<ExpenseGroup, string[]> = { Design: [], Construction: [], Consultation: [], Company: [] }
  for (const c of o.expenseCategories) if (c.active) groups[label(c.group) as ExpenseGroup].push(c.name)
  // the backend keeps only the month; the year is whichever puts the start on or before today
  const month = String(o.company.financialYearStartMonth).padStart(2, '0')
  const thisYear = Number(TODAY.slice(0, 4))
  const fyStart = `${thisYear}-${month}-01` <= TODAY ? `${thisYear}-${month}-01` : `${thisYear - 1}-${month}-01`
  return {
    company: {
      name: o.company.name, tagline: o.company.tagline ?? '', address: o.company.address ?? '', phone: o.company.phone ?? '',
      email: o.company.email ?? '', registration: o.company.registration ?? '', owner: o.company.owner ?? '',
    },
    financialYearStart: fyStart,
    revenueCategories: o.revenueCategories.map((r: Row) => r.name),
    expenseCategories: groups,
    commission: {
      designCompanyShare: o.commission.designCompanyShare, designCommissionPool: o.commission.designCommissionPool,
      constructionDefaultRate: opt(o.commission.constructionDefaultRate),
    },
    wages: {
      hoursPerDay: o.wages.hoursPerDay, overtimeMultiplier: o.wages.overtimeMultiplier, defaultDailyRate: o.wages.defaultDailyRate,
      payday: label(o.wages.payday),
    },
    units: o.units.filter((u: Row) => u.active).map((u: Row) => u.name),
    projectStatuses: PROJECT_STATUSES,
    paymentMethods: o.paymentMethods.filter((m: Row) => m.active).map((m: Row) => m.name),
    supplierCreditDays: o.company.supplierCreditDays,
  }
}

function delayFrom(t: Row): DelayTerms {
  return {
    enabled: t.enabled, contractDeadline: t.contractDeadline, rule: t.rule.toLowerCase(), ratePerDay: opt(t.ratePerDay),
    percentPerWeek: opt(t.percentPerWeek), fixedAmount: opt(t.fixedAmount), graceDays: t.graceDays, maxDeduction: opt(t.maxDeduction),
    actualCompletion: opt(t.actualCompletion), appliedAmount: opt(t.appliedAmount), appliedOn: opt(t.appliedOn),
    appliedToInstallmentId: id(t.appliedToInstallmentId), notes: opt(t.notes),
  }
}

// attendance and overtime are only served 93 days at a time
async function inWindows(path: string, from: string): Promise<Row[]> {
  const out: Row[] = []
  for (let start = from; start <= TODAY; start = addDays(start, 90)) {
    const end = addDays(start, 89) < TODAY ? addDays(start, 89) : TODAY
    out.push(...(await apiGet<Row[]>(`${path}?from=${start}&to=${end}`)))
  }
  return out
}

export async function loadAll(): Promise<DataState> {
  const [overview, clientRows, projectRows, otherIncomeRows, expenseRows, expensePaymentRows, supplierRows, invoiceRows, materialRows,
    movementRows, employeeRows, advanceRows, adjustmentRows, payrollPaymentRows, commissionRows, commissionPaymentRows, activityRows] =
    await Promise.all([
      apiGet<Row>('/settings'),
      apiGetAll<Row>('/clients'),
      apiGetAll<Row>('/projects'),
      apiGetAll<Row>('/other-income'),
      apiGetAll<Row>('/expenses'),
      apiGet<Row[]>('/expenses/payments'),
      apiGetAll<Row>('/suppliers'),
      apiGetAll<Row>('/supplier-invoices'),
      apiGetAll<Row>('/inventory/materials'),
      apiGetAll<Row>('/inventory/movements'),
      apiGetAll<Row>('/employees'),
      apiGetAll<Row>('/payroll/advances'),
      apiGet<Row[]>('/payroll/adjustments'),
      apiGet<Row[]>('/payroll/payments'),
      apiGetAll<Row>('/commissions'),
      apiGetAll<Row>('/commissions/payments'),
      apiGetAll<Row>('/activity'),
    ])

  // per-project pieces: schedule, receipts, write-offs, delay terms, agreement
  const perProject = await Promise.all(projectRows.map(async (p: Row) => {
    const [schedule, receipts, writeOffs, delay, agreement] = await Promise.all([
      apiGet<Row[]>(`/projects/${p.id}/schedule`),
      apiGet<Row[]>(`/projects/${p.id}/receipts`),
      apiGet<Row[]>(`/projects/${p.id}/write-offs`),
      apiGet<Row | undefined>(`/projects/${p.id}/delay-terms`),
      apiGetOptional<Row>(`/projects/${p.id}/agreement`),
    ])
    return { p, schedule, receipts, writeOffs, delay, agreement }
  }))
  const liveInvoices = invoiceRows.filter((i: Row) => i.status !== 'CANCELLED')
  const [incomeReceipts, invoiceDetails] = await Promise.all([
    Promise.all(otherIncomeRows.map((o: Row) => apiGet<Row[]>(`/other-income/${o.id}/receipts`))),
    Promise.all(liveInvoices.map((i: Row) => apiGet<Row>(`/supplier-invoices/${i.id}`))),
  ])
  const firstDay = employeeRows.reduce((m: string, e: Row) => (e.joinedOn < m ? e.joinedOn : m), TODAY)
  const [attendanceRows, overtimeRows] = await Promise.all([inWindows('/attendance', firstDay), inWindows('/overtime', firstDay)])

  const projects: Project[] = perProject.map(({ p, delay, agreement }) => ({
    id: String(p.id), code: p.code, name: p.name, type: label(p.type) as ProjectType, clientId: String(p.clientId),
    location: p.location ?? '', description: p.description ?? '', contractValue: p.contractValue, startDate: p.startDate,
    expectedCompletion: p.expectedCompletion, actualCompletion: opt(p.actualCompletion), status: label(p.status) as ProjectStatus,
    notes: opt(p.notes), createdAt: day(p.createdAt),
    agreement: agreement && { fileName: agreement.fileName, size: agreement.size, uploadedAt: day(agreement.uploadedAt) },
    delay: delay ? delayFrom(delay) : undefined,
  }))
  const installments: Installment[] = perProject.flatMap(({ schedule }) => schedule.map((i: Row) => ({
    id: String(i.id), projectId: String(i.projectId), description: i.description, dueDate: i.dueDate, amount: i.amount,
    billed: i.billed, billedOn: opt(i.billedOn),
  })))
  const clientPayments: ClientPayment[] = perProject.flatMap(({ receipts }) => receipts.filter((r: Row) => !r.voided).map((r: Row) => ({
    id: String(r.id), installmentId: String(r.installmentId), date: r.paymentDate, amount: r.amount, method: r.method,
    reference: opt(r.reference), notes: opt(r.notes),
  })))
  const writeOffs: WriteOff[] = perProject.flatMap(({ writeOffs }) => writeOffs.filter((w: Row) => !w.voided).map((w: Row) => ({
    id: String(w.id), installmentId: String(w.installmentId), date: w.date, amount: w.amount, reason: w.reason,
  })))

  const clients: Client[] = clientRows.map((c: Row) => ({
    id: String(c.id), name: c.name, kind: label(c.kind) as Client['kind'], phone: c.phone, email: opt(c.email), address: c.address ?? '',
    city: c.city ?? '', notes: opt(c.notes), createdAt: day(c.createdAt),
  }))
  const otherIncome: OtherIncome[] = otherIncomeRows.map((o: Row) => ({
    id: String(o.id), date: o.date, description: o.description, payer: opt(o.payer), amount: o.amount, dueDate: opt(o.dueDate),
    reference: opt(o.reference), notes: opt(o.notes),
  }))
  const otherIncomeReceipts: OtherIncomeReceipt[] = incomeReceipts.flat().filter((r: Row) => !r.voided).map((r: Row) => ({
    id: String(r.id), incomeId: String(r.incomeId), date: r.paymentDate, amount: r.amount, method: r.method, reference: opt(r.reference),
  }))
  const expenses: Expense[] = expenseRows.map((e: Row) => {
    const details: Record<string, string> = {}
    if (e.machineType) details.Machine = e.machineType
    if (e.usageFrom) details['Usage period'] = e.usageTo && e.usageTo !== e.usageFrom ? `${e.usageFrom} – ${e.usageTo}` : e.usageFrom
    if (e.billingPeriod) details['Billing period'] = e.billingPeriod
    return {
      id: String(e.id), date: e.date, scope: label(e.scope) as Expense['scope'], projectId: id(e.projectId), group: label(e.group) as ExpenseGroup,
      category: e.category, description: e.description, payee: e.payee, supplierId: id(e.supplierId), amount: e.amount, dueDate: opt(e.dueDate),
      invoiceNo: opt(e.invoiceNo), notes: opt(e.notes), details: Object.keys(details).length ? details : undefined,
    }
  })
  const expensePayments: ExpensePayment[] = expensePaymentRows.filter((p: Row) => !p.voided).map((p: Row) => ({
    id: String(p.id), expenseId: String(p.expenseId), date: p.paymentDate, amount: p.amount, method: p.method, reference: opt(p.reference),
  }))
  const suppliers: Supplier[] = supplierRows.map((s: Row) => ({
    id: String(s.id), name: s.name, contactPerson: s.contactPerson ?? '', phone: s.phone, email: opt(s.email), address: s.address ?? '',
    supplies: s.supplies ?? '', creditDays: s.creditDays,
  }))
  const supplierInvoices: SupplierInvoice[] = invoiceDetails.map(({ invoice: i, lines }: Row) => ({
    id: String(i.id), supplierId: String(i.supplierId), invoiceNo: i.invoiceNo, date: i.invoiceDate, dueDate: i.dueDate,
    projectId: id(i.projectId), notes: opt(i.notes),
    attachment: i.attachmentFilename ? { fileName: i.attachmentFilename, size: i.attachmentSize } : undefined,
    lines: lines.map((l: Row) => ({ id: String(l.id), description: l.description, materialId: id(l.materialId), qty: l.quantity, unit: l.unit, unitPrice: l.unitPrice })),
  }))
  const supplierPayments: SupplierPayment[] = invoiceDetails.flatMap(({ payments }: Row) => payments.filter((p: Row) => !p.voided).map((p: Row) => ({
    id: String(p.id), invoiceId: String(p.invoiceId), date: p.paymentDate, amount: p.amount, method: p.method, reference: opt(p.reference),
  })))
  const materials: Material[] = materialRows.map((m: Row) => ({
    id: String(m.id), sku: m.sku, name: m.name, category: m.category, unit: m.unit, unitCost: m.defaultUnitCost,
    // the owner dropped reorder levels; 0 keeps the old low-stock maths quiet until that UI is removed
    reorderLevel: 0,
  }))
  // FIFO runs in posting order (id), not by date - a backdated issue still takes the oldest layers left
  const stockMovements: StockMovement[] = movementRows
    .sort((a: Row, b: Row) => a.id - b.id)
    .map((m: Row) => {
      const incoming = m.type === 'RECEIVED' || (m.type === 'ADJUSTMENT' && m.quantityChange > 0)
      return {
        id: String(m.id), materialId: String(m.materialId), date: m.date, type: label(m.type) as StockMovement['type'], qty: m.quantityChange,
        unitCost: incoming ? m.unitCost : undefined, projectId: id(m.projectId), returnedFromId: id(m.returnedFromMovementId),
        reason: m.type === 'ADJUSTMENT' ? opt(m.notes) : undefined, reference: opt(m.reference),
      }
    })
  const employees: Employee[] = employeeRows.map((e: Row) => ({
    id: String(e.id), name: e.name, role: e.role, phone: e.phone ?? '', dailyRate: e.dailyWage, overtimeRate: e.overtimeRate,
    status: label(e.status) as Employee['status'], joinedOn: e.joinedOn, address: opt(e.address),
  }))
  const attendance: Attendance[] = attendanceRows.map((a: Row) => ({
    date: a.date, employeeId: String(a.employeeId), status: label(a.status) as Attendance['status'], projectId: id(a.projectId), notes: opt(a.notes),
  }))
  const overtime: Overtime[] = overtimeRows.map((o: Row) => ({
    id: String(o.id), date: o.date, employeeId: String(o.employeeId), projectId: id(o.projectId), hours: o.hours, rate: o.hourlyRate,
    approved: o.approved, recordedBy: o.recordedBy, notes: opt(o.notes),
  }))
  const advances: Advance[] = advanceRows.filter((a: Row) => !a.voided).map((a: Row) => ({
    id: String(a.id), date: a.paymentDate, employeeId: String(a.employeeId), amount: a.amount, method: a.paymentMethod, notes: opt(a.notes),
  }))
  const payrollAdjustments: PayrollAdjustment[] = adjustmentRows.map((a: Row) => ({
    weekStart: a.weekStart, employeeId: String(a.employeeId), advanceDeduction: opt(a.advanceDeduction), otherDeduction: opt(a.otherDeduction),
    otherNote: opt(a.otherNote),
  }))
  const payrollPayments: PayrollPayment[] = payrollPaymentRows.filter((p: Row) => !p.voided).map((p: Row) => ({
    id: String(p.id), weekStart: p.weekStart, employeeId: String(p.employeeId), date: p.paymentDate, amount: p.amount, method: p.paymentMethod,
  }))
  const commissions: CommissionArrangement[] = commissionRows.filter((c: Row) => c.status !== 'VOIDED').map((c: Row) => ({
    id: String(c.id), projectId: String(c.projectId), recipient: c.recipient, role: c.role ?? '', rate: c.ratePct,
    status: c.status === 'PENDING' ? 'Pending' : 'Confirmed', confirmedOn: opt(c.confirmedOn),
  }))
  const commissionPayments: CommissionPayment[] = commissionPaymentRows.filter((p: Row) => !p.voided).map((p: Row) => ({
    id: String(p.id), arrangementId: String(p.commissionId), date: p.paymentDate, amount: p.amount, method: p.method, reference: opt(p.reference),
  }))
  const activity: ActivityEntry[] = activityRows.map((a: Row) => ({
    id: String(a.id), date: a.date, projectId: id(a.projectId), kind: a.kind.toLowerCase(), text: a.message,
  }))

  return {
    clients, projects, installments, clientPayments, writeOffs, otherIncome, otherIncomeReceipts, expenses, expensePayments, suppliers,
    supplierInvoices, supplierPayments, materials, stockMovements, employees, attendance, overtime, advances, payrollAdjustments,
    payrollPayments, commissions, commissionPayments, activity, settings: settingsFrom(overview),
  }
}

export function emptyState(): DataState {
  return {
    clients: [], projects: [], installments: [], clientPayments: [], writeOffs: [], otherIncome: [], otherIncomeReceipts: [], expenses: [],
    expensePayments: [], suppliers: [], supplierInvoices: [], supplierPayments: [], materials: [], stockMovements: [], employees: [],
    attendance: [], overtime: [], advances: [], payrollAdjustments: [], payrollPayments: [], commissions: [], commissionPayments: [],
    activity: [],
    settings: {
      company: { name: '', tagline: '', address: '', phone: '', email: '', registration: '', owner: '' }, financialYearStart: TODAY,
      revenueCategories: [], expenseCategories: { Design: [], Construction: [], Consultation: [], Company: [] },
      commission: { designCompanyShare: 80, designCommissionPool: 20 }, wages: { hoursPerDay: 8, overtimeMultiplier: 1.5, defaultDailyRate: 0, payday: 'Saturday' },
      units: [], projectStatuses: PROJECT_STATUSES, paymentMethods: [], supplierCreditDays: 14,
    },
  }
}
