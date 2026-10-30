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
import { formatLKR } from '@/lib/format'

/* eslint-disable @typescript-eslint/no-explicit-any */
type Row = any

const id = (n: number | null | undefined) => (n == null ? undefined : String(n))
const day = (ts: string) => ts.slice(0, 10)
const opt = <T,>(v: T | null | undefined) => (v == null || v === '' ? undefined : v)
// SUPER_ADMIN -> Super Admin, IN_PROGRESS -> In Progress, HALF_DAY -> Half Day
const label = (code: string) => code.toLowerCase().split('_').map((w) => w[0].toUpperCase() + w.slice(1)).join(' ')

export const PROJECT_STATUSES: ProjectStatus[] = ['Planning', 'In Progress', 'On Hold', 'Completed', 'Cancelled']

// The backend wants the version you read on every edit (optimistic locking). The UI types have no
// room for it, so it lives here, keyed like "project:12", and gets refreshed whenever a row is mapped.
const versions = new Map<string, number>()
export const versionOf = (kind: string, id: string) => versions.get(`${kind}:${id}`)
function keep<T extends { id: number; version?: number }>(kind: string, row: T): T {
  if (row.version != null) versions.set(`${kind}:${row.id}`, row.version)
  return row
}

// API enum codes for the UI's labels: 'In Progress' -> IN_PROGRESS
export const code = (s: string) => s.toUpperCase().replace(/ /g, '_')

export function mapClient(c: Row): Client {
  keep('client', c)
  return {
    id: String(c.id), name: c.name, kind: label(c.kind) as Client['kind'], phone: c.phone, email: opt(c.email), address: c.address ?? '',
    city: c.city ?? '', notes: opt(c.notes), createdAt: day(c.createdAt),
  }
}

export function mapProject(p: Row, delay?: Row, agreement?: Row): Project {
  keep('project', p)
  if (delay) versions.set(`delay:${p.id}`, delay.version)
  else versions.delete(`delay:${p.id}`)
  return {
    id: String(p.id), code: p.code, name: p.name, type: label(p.type) as ProjectType, clientId: String(p.clientId),
    location: p.location ?? '', description: p.description ?? '', contractValue: p.contractValue, startDate: p.startDate,
    expectedCompletion: p.expectedCompletion, actualCompletion: opt(p.actualCompletion), status: label(p.status) as ProjectStatus,
    notes: opt(p.notes), createdAt: day(p.createdAt),
    agreement: agreement && { fileName: agreement.fileName, size: agreement.size, uploadedAt: day(agreement.uploadedAt) },
    delay: delay ? delayFrom(delay) : undefined,
  }
}

export function mapInstallment(i: Row): Installment {
  keep('installment', i)
  return {
    id: String(i.id), projectId: String(i.projectId), description: i.description, dueDate: i.dueDate, amount: i.amount,
    billed: i.billed, billedOn: opt(i.billedOn), variation: i.kind === 'VARIATION' || undefined,
  }
}

const mapReceipt = (r: Row): ClientPayment => ({
  id: String(r.id), installmentId: String(r.installmentId), date: r.paymentDate, amount: r.amount, method: r.method,
  reference: opt(r.reference), notes: opt(r.notes),
})
const mapWriteOff = (w: Row): WriteOff => ({ id: String(w.id), installmentId: String(w.installmentId), date: w.date, amount: w.amount, reason: w.reason })
const mapActivity = (a: Row): ActivityEntry => ({ id: String(a.id), date: a.date, projectId: id(a.projectId), kind: a.kind.toLowerCase(), text: a.message })

// one project with everything hanging off it, for the initial load and for refreshing after a save
async function projectBundle(p: Row) {
  const [schedule, receipts, writeOffs, delay, agreement] = await Promise.all([
    apiGet<Row[]>(`/projects/${p.id}/schedule`),
    apiGet<Row[]>(`/projects/${p.id}/receipts`),
    apiGet<Row[]>(`/projects/${p.id}/write-offs`),
    apiGet<Row | undefined>(`/projects/${p.id}/delay-terms`),
    apiGetOptional<Row>(`/projects/${p.id}/agreement`),
  ])
  return {
    project: mapProject(p, delay, agreement),
    installments: schedule.map(mapInstallment),
    clientPayments: receipts.filter((r: Row) => !r.voided).map(mapReceipt),
    writeOffs: writeOffs.filter((w: Row) => !w.voided).map(mapWriteOff),
  }
}
export type ProjectBundle = Awaited<ReturnType<typeof projectBundle>>

export async function loadProject(projectId: string): Promise<ProjectBundle> {
  return projectBundle(await apiGet<Row>(`/projects/${projectId}`))
}
export async function loadClients(): Promise<Client[]> {
  return (await apiGetAll<Row>('/clients')).map(mapClient)
}
export async function loadActivity(): Promise<ActivityEntry[]> {
  return (await apiGetAll<Row>('/activity')).map(mapActivity)
}

const mapOtherIncome = (o: Row): OtherIncome => ({
  id: String(o.id), date: o.date, description: o.description, payer: opt(o.payer), amount: o.amount, dueDate: opt(o.dueDate),
  reference: opt(o.reference), notes: opt(o.notes),
})
const mapIncomeReceipt = (r: Row): OtherIncomeReceipt => ({
  id: String(r.id), incomeId: String(r.incomeId), date: r.paymentDate, amount: r.amount, method: r.method, reference: opt(r.reference),
})
export async function loadOtherIncome(): Promise<{ otherIncome: OtherIncome[]; otherIncomeReceipts: OtherIncomeReceipt[] }> {
  const rows = await apiGetAll<Row>('/other-income')
  const receipts = await Promise.all(rows.map((o: Row) => apiGet<Row[]>(`/other-income/${o.id}/receipts`)))
  return { otherIncome: rows.map(mapOtherIncome), otherIncomeReceipts: receipts.flat().filter((r: Row) => !r.voided).map(mapIncomeReceipt) }
}

function mapExpense(e: Row): Expense {
  keep('expense', e)
  // same keys the expense dialog writes, so the table shows them the same way
  const details: Record<string, string> = {}
  if (e.quantity != null) Object.assign(details, { Item: e.description, Quantity: `${e.quantity} ${e.unit}`, 'Unit price': formatLKR(e.unitPrice) })
  if (e.machineType) details.Machine = e.machineType
  if (e.usageFrom) details['Usage period'] = e.usageTo ? `${e.usageFrom} to ${e.usageTo}` : e.usageFrom
  if (e.billingPeriod) details.Period = e.billingPeriod
  return {
    id: String(e.id), date: e.date, scope: label(e.scope) as Expense['scope'], projectId: id(e.projectId), group: label(e.group) as ExpenseGroup,
    category: e.category, description: e.description, payee: e.payee, supplierId: id(e.supplierId), amount: e.amount, dueDate: opt(e.dueDate),
    invoiceNo: opt(e.invoiceNo), notes: opt(e.notes), details: Object.keys(details).length ? details : undefined,
    quantity: opt(e.quantity), unit: opt(e.unit), unitPrice: opt(e.unitPrice), machineType: opt(e.machineType), usageFrom: opt(e.usageFrom),
    usageTo: opt(e.usageTo), billingPeriod: opt(e.billingPeriod),
  }
}
const mapExpensePayment = (p: Row): ExpensePayment => ({
  id: String(p.id), expenseId: String(p.expenseId), date: p.paymentDate, amount: p.amount, method: p.method, reference: opt(p.reference),
})
export async function loadExpenses(): Promise<{ expenses: Expense[]; expensePayments: ExpensePayment[] }> {
  const [rows, payments] = await Promise.all([apiGetAll<Row>('/expenses'), apiGet<Row[]>('/expenses/payments')])
  return { expenses: rows.map(mapExpense), expensePayments: payments.filter((p: Row) => !p.voided).map(mapExpensePayment) }
}

const mapSupplier = (s: Row): Supplier => ({
  id: String(s.id), name: s.name, contactPerson: s.contactPerson ?? '', phone: s.phone, email: opt(s.email), address: s.address ?? '',
  supplies: s.supplies ?? '', creditDays: s.creditDays,
})
const mapInvoice = ({ invoice: i, lines }: Row): SupplierInvoice => ({
  id: String(i.id), supplierId: String(i.supplierId), invoiceNo: i.invoiceNo, date: i.invoiceDate, dueDate: i.dueDate,
  projectId: id(i.projectId), notes: opt(i.notes),
  attachment: i.attachmentFilename ? { fileName: i.attachmentFilename, size: i.attachmentSize } : undefined,
  lines: lines.map((l: Row) => ({ id: String(l.id), description: l.description, materialId: id(l.materialId), qty: l.quantity, unit: l.unit, unitPrice: l.unitPrice })),
})
const mapInvoicePayments = ({ payments }: Row): SupplierPayment[] => payments.filter((p: Row) => !p.voided).map((p: Row) => ({
  id: String(p.id), invoiceId: String(p.invoiceId), date: p.paymentDate, amount: p.amount, method: p.method, reference: opt(p.reference),
}))
const mapMaterial = (m: Row): Material => ({
  id: String(m.id), sku: m.sku, name: m.name, category: m.category, unit: m.unit, unitCost: m.defaultUnitCost,
  // the owner dropped reorder levels; 0 keeps the old low-stock maths quiet until that UI is removed
  reorderLevel: 0,
})
// FIFO runs in posting order (id), not by date - a backdated issue still takes the oldest layers left
const mapMovements = (rows: Row[]): StockMovement[] => rows
  .sort((a: Row, b: Row) => a.id - b.id)
  .map((m: Row) => {
    const incoming = m.type === 'RECEIVED' || (m.type === 'ADJUSTMENT' && m.quantityChange > 0)
    return {
      id: String(m.id), materialId: String(m.materialId), date: m.date, type: label(m.type) as StockMovement['type'], qty: m.quantityChange,
      unitCost: incoming ? m.unitCost : undefined, projectId: id(m.projectId), returnedFromId: id(m.returnedFromMovementId),
      reason: m.type === 'ADJUSTMENT' ? opt(m.notes) : undefined, reference: opt(m.reference),
    }
  })

export async function loadSuppliers(): Promise<Supplier[]> {
  return (await apiGetAll<Row>('/suppliers')).map(mapSupplier)
}
export async function loadSupplierInvoices(): Promise<{ supplierInvoices: SupplierInvoice[]; supplierPayments: SupplierPayment[] }> {
  const live = (await apiGetAll<Row>('/supplier-invoices')).filter((i: Row) => i.status !== 'CANCELLED')
  const details = await Promise.all(live.map((i: Row) => apiGet<Row>(`/supplier-invoices/${i.id}`)))
  return { supplierInvoices: details.map(mapInvoice), supplierPayments: details.flatMap(mapInvoicePayments) }
}
export async function loadInventory(): Promise<{ materials: Material[]; stockMovements: StockMovement[] }> {
  const [materials, movements] = await Promise.all([apiGetAll<Row>('/inventory/materials'), apiGetAll<Row>('/inventory/movements')])
  return { materials: materials.map(mapMaterial), stockMovements: mapMovements(movements) }
}

// adjustments are keyed by week and worker, not by their own id
export const adjustmentKey = (weekStart: string, employeeId: string) => `${weekStart}:${employeeId}`

function mapLabour(employeeRows: Row[], attendanceRows: Row[], overtimeRows: Row[], advanceRows: Row[], adjustmentRows: Row[], paymentRows: Row[]) {
  const employees: Employee[] = employeeRows.map((e: Row) => {
    keep('employee', e)
    return {
      id: String(e.id), name: e.name, role: e.role, phone: e.phone ?? '', dailyRate: e.dailyWage, overtimeRate: e.overtimeRate,
      status: label(e.status) as Employee['status'], joinedOn: e.joinedOn, address: opt(e.address),
    }
  })
  const attendance: Attendance[] = attendanceRows.map((a: Row) => ({
    date: a.date, employeeId: String(a.employeeId), status: label(a.status) as Attendance['status'], projectId: id(a.projectId), notes: opt(a.notes),
  }))
  const overtime: Overtime[] = overtimeRows.map((o: Row) => {
    keep('overtime', o)
    return {
      id: String(o.id), date: o.date, employeeId: String(o.employeeId), projectId: id(o.projectId), hours: o.hours, rate: o.hourlyRate,
      approved: o.approved, recordedBy: o.recordedBy, notes: opt(o.notes),
    }
  })
  const advances: Advance[] = advanceRows.filter((a: Row) => !a.voided).map((a: Row) => ({
    id: String(a.id), date: a.paymentDate, employeeId: String(a.employeeId), amount: a.amount, method: a.paymentMethod, notes: opt(a.notes),
  }))
  const payrollAdjustments: PayrollAdjustment[] = adjustmentRows.map((a: Row) => {
    versions.set(`adjustment:${adjustmentKey(a.weekStart, String(a.employeeId))}`, a.version)
    return {
      weekStart: a.weekStart, employeeId: String(a.employeeId), advanceDeduction: opt(a.advanceDeduction), otherDeduction: opt(a.otherDeduction),
      otherNote: opt(a.otherNote),
    }
  })
  const payrollPayments: PayrollPayment[] = paymentRows.filter((p: Row) => !p.voided).map((p: Row) => ({
    id: String(p.id), weekStart: p.weekStart, employeeId: String(p.employeeId), date: p.paymentDate, amount: p.amount, method: p.paymentMethod,
  }))
  return { employees, attendance, overtime, advances, payrollAdjustments, payrollPayments }
}

// everything labour and payroll: one save can change attendance, recoveries and what each week owes
export async function loadLabour() {
  const [employeeRows, advanceRows, adjustmentRows, paymentRows] = await Promise.all([
    apiGetAll<Row>('/employees'), apiGetAll<Row>('/payroll/advances'), apiGet<Row[]>('/payroll/adjustments'), apiGet<Row[]>('/payroll/payments'),
  ])
  const firstDay = employeeRows.reduce((m: string, e: Row) => (e.joinedOn < m ? e.joinedOn : m), TODAY)
  const [attendanceRows, overtimeRows] = await Promise.all([inWindows('/attendance', firstDay), inWindows('/overtime', firstDay)])
  return mapLabour(employeeRows, attendanceRows, overtimeRows, advanceRows, adjustmentRows, paymentRows)
}

function mapCommission(c: Row): CommissionArrangement {
  keep('commission', c)
  return {
    id: String(c.id), projectId: String(c.projectId), recipient: c.recipient, role: c.role ?? '', rate: c.ratePct,
    status: c.status === 'PENDING' ? 'Pending' : 'Confirmed', confirmedOn: opt(c.confirmedOn),
  }
}
const mapCommissionPayment = (p: Row): CommissionPayment => ({
  id: String(p.id), arrangementId: String(p.commissionId), date: p.paymentDate, amount: p.amount, method: p.method, reference: opt(p.reference),
})
export async function loadCommissions(): Promise<{ commissions: CommissionArrangement[]; commissionPayments: CommissionPayment[] }> {
  const [rows, payments] = await Promise.all([apiGetAll<Row>('/commissions'), apiGetAll<Row>('/commissions/payments')])
  return {
    commissions: rows.filter((c: Row) => c.status !== 'VOIDED').map(mapCommission),
    commissionPayments: payments.filter((p: Row) => !p.voided).map(mapCommissionPayment),
  }
}
export async function loadSettings(): Promise<Settings> {
  return settingsFrom(await apiGet<Row>('/settings'))
}
// id of a list entry the user removed, or undefined when it isn't on the server
export const findCategoryId = (group: ExpenseGroup, name: string) => categoryIds.get(`${code(group)}/${name}`)
export const findUnitId = (name: string) => unitIds.get(name)
export const findMethodId = (name: string) => methodIds.get(name)

// the UI picks categories and units by name; the API wants ids
const categoryIds = new Map<string, number>()
const unitIds = new Map<string, number>()
export function categoryId(group: ExpenseGroup, name: string): number {
  const found = categoryIds.get(`${code(group)}/${name}`)
  if (found == null) throw new Error(`Unknown category "${name}". Reload the data and try again.`)
  return found
}
export function unitId(name: string): number {
  const found = unitIds.get(name)
  if (found == null) throw new Error(`Unknown unit "${name}". Reload the data and try again.`)
  return found
}

// the UI picks payment methods by name; the API wants the id
const methodIds = new Map<string, number>()
export function methodId(name: string): number {
  const found = methodIds.get(name)
  if (found == null) throw new Error(`Unknown payment method "${name}". Reload the data and try again.`)
  return found
}

function settingsFrom(o: Row): Settings {
  versions.set('settings:company', o.company.version)
  versions.set('settings:commission', o.commission.version)
  versions.set('settings:wages', o.wages.version)
  methodIds.clear()
  for (const m of o.paymentMethods) methodIds.set(m.name, m.id)
  categoryIds.clear()
  for (const c of o.expenseCategories) categoryIds.set(`${c.group}/${c.name}`, c.id)
  unitIds.clear()
  for (const u of o.units) unitIds.set(u.name, u.id)
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

  const perProject = await Promise.all(projectRows.map(projectBundle))
  const liveInvoices = invoiceRows.filter((i: Row) => i.status !== 'CANCELLED')
  const [incomeReceipts, invoiceDetails] = await Promise.all([
    Promise.all(otherIncomeRows.map((o: Row) => apiGet<Row[]>(`/other-income/${o.id}/receipts`))),
    Promise.all(liveInvoices.map((i: Row) => apiGet<Row>(`/supplier-invoices/${i.id}`))),
  ])
  const firstDay = employeeRows.reduce((m: string, e: Row) => (e.joinedOn < m ? e.joinedOn : m), TODAY)
  const [attendanceRows, overtimeRows] = await Promise.all([inWindows('/attendance', firstDay), inWindows('/overtime', firstDay)])

  const projects: Project[] = perProject.map((b) => b.project)
  const installments: Installment[] = perProject.flatMap((b) => b.installments)
  const clientPayments: ClientPayment[] = perProject.flatMap((b) => b.clientPayments)
  const writeOffs: WriteOff[] = perProject.flatMap((b) => b.writeOffs)
  const clients: Client[] = clientRows.map(mapClient)
  const otherIncome: OtherIncome[] = otherIncomeRows.map(mapOtherIncome)
  const otherIncomeReceipts: OtherIncomeReceipt[] = incomeReceipts.flat().filter((r: Row) => !r.voided).map(mapIncomeReceipt)
  const expenses: Expense[] = expenseRows.map(mapExpense)
  const expensePayments: ExpensePayment[] = expensePaymentRows.filter((p: Row) => !p.voided).map(mapExpensePayment)
  const suppliers: Supplier[] = supplierRows.map(mapSupplier)
  const supplierInvoices: SupplierInvoice[] = invoiceDetails.map(mapInvoice)
  const supplierPayments: SupplierPayment[] = invoiceDetails.flatMap(mapInvoicePayments)
  const materials: Material[] = materialRows.map(mapMaterial)
  const stockMovements: StockMovement[] = mapMovements(movementRows)
  const { employees, attendance, overtime, advances, payrollAdjustments, payrollPayments } =
    mapLabour(employeeRows, attendanceRows, overtimeRows, advanceRows, adjustmentRows, payrollPaymentRows)
  const commissions: CommissionArrangement[] = commissionRows.filter((c: Row) => c.status !== 'VOIDED').map(mapCommission)
  const commissionPayments: CommissionPayment[] = commissionPaymentRows.filter((p: Row) => !p.voided).map(mapCommissionPayment)
  const activity: ActivityEntry[] = activityRows.map(mapActivity)

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
