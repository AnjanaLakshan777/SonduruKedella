import { create } from 'zustand'
import { useMemo } from 'react'
import { createSeed } from '@/data/seed'
import {
  categoryId, code, emptyState, loadActivity, loadAll, loadClients, loadExpenses, loadInventory, loadOtherIncome, loadProject, loadSupplierInvoices,
  loadLabour, loadSuppliers, mapClient, methodId, unitId, versionOf, adjustmentKey, loadCommissions, loadSettings, findCategoryId, findUnitId,
  findMethodId,
} from '@/data/api-load'
import type { PaymentInput } from '@/components/app/payment-dialog'
import { api, apiPost, apiPut } from '@/lib/api'
import type {
  Attendance, Client, DataState, DelayTerms, Employee,
  ExpenseGroup, ExpenseScope, Material, OtherIncome, Overtime, PayrollAdjustment, ProjectStatus, ProjectType,
  Settings, StockMovement, Supplier, SupplierInvoice, WriteOff,
} from '@/data/types'
import { derive } from '@/lib/finance'

type New<T extends { id: string }> = Omit<T, 'id'>

interface Actions {
  // saved on the backend; they throw ApiError (with the server's message) when it says no
  addClient: (c: ClientInput) => Promise<string>
  updateClient: (id: string, c: ClientInput) => Promise<void>
  addProject: (p: ProjectInput) => Promise<string>
  setProjectStatus: (id: string, status: ProjectStatus) => Promise<void>
  uploadAgreement: (projectId: string, file: File) => Promise<void>
  saveDelayTerms: (projectId: string, terms: DelayTerms, enabled: boolean) => Promise<void>
  applyDelayDeduction: (projectId: string, d: { completionDate: string; installmentId: string; amount: number; notes?: string }) => Promise<void>
  reverseDelayDeduction: (projectId: string) => Promise<void>
  reviseSchedule: (projectId: string, rows: { id?: string; description: string; dueDate: string; amount: number }[]) => Promise<void>
  addVariation: (projectId: string, v: { description: string; amount: number; dueDate: string; billedOn: string }) => Promise<void>
  billInstallment: (id: string, date: string) => Promise<void>
  recordClientPayment: (p: PaymentInput & { installmentId: string }) => Promise<void>
  writeOff: (w: New<WriteOff>) => Promise<void>
  addOtherIncome: (i: New<OtherIncome>, received?: PaymentInput) => Promise<void>
  recordOtherIncomeReceipt: (r: PaymentInput & { incomeId: string }) => Promise<void>
  // the result carries the server's duplicate warning (same supplier and invoice number as a supplier invoice)
  addExpense: (e: ExpenseInput, payment?: PaymentInput) => Promise<{ id: string; warning?: string }>
  updateExpense: (id: string, e: ExpenseInput) => Promise<{ warning?: string }>
  recordExpensePayment: (p: PaymentInput & { expenseId: string }) => Promise<void>
  addSupplier: (x: New<Supplier>) => Promise<string>
  // the attachment is uploaded after the invoice is saved; if only the upload fails, the invoice stays
  addSupplierInvoice: (inv: New<SupplierInvoice>, payment?: PaymentInput, file?: File, onSaved?: () => void) => Promise<string>
  recordSupplierPayment: (p: PaymentInput & { invoiceId: string }) => Promise<void>
  addMaterial: (m: New<Material>) => Promise<void>
  // The server costs it FIFO and refuses what isn't allowed (more than in stock, a return beyond the issue...).
  // requestId: one per dialog opening, so a retry doesn't post twice. qty is signed, as in the ledger.
  addStockMovement: (m: New<StockMovement> & { requestId: string; notes?: string }) => Promise<{ totalCost: number; projectId?: string }>
  addEmployee: (e: New<Employee>) => Promise<void>
  updateEmployee: (id: string, e: New<Employee>) => Promise<void>
  // the whole register for the day; a worker left out has no attendance that day
  saveAttendance: (date: string, rows: Attendance[]) => Promise<void>
  addOvertime: (o: Omit<New<Overtime>, 'approved'>) => Promise<void>
  approveOvertime: (id: string, approved: boolean) => Promise<void>
  addAdvance: (a: PaymentInput & { employeeId: string }) => Promise<void>
  setPayrollAdjustment: (a: PayrollAdjustment) => Promise<void>
  // one payment per worker-week; the first payment freezes that week on the server
  recordPayrollPayments: (rows: (PaymentInput & { weekStart: string; employeeId: string })[]) => Promise<void>
  // a paid week changes only through corrections (+/-, with a reason); a wrong one is voided, not edited
  recordPayrollCorrection: (weekStart: string, employeeId: string, c: { requestId: string; correctionDate: string; wageDelta: number; projectId?: string; reason: string }) => Promise<void>
  voidPayrollCorrection: (weekStart: string, employeeId: string, correctionId: string, version: number, reason: string) => Promise<void>
  voidPayrollPayment: (weekStart: string, employeeId: string, paymentId: string, version: number, reason: string) => Promise<void>
  // a week the advance recovery swallows (nothing to pay) is closed instead of paid
  closePayrollWeek: (weekStart: string, employeeId: string) => Promise<void>
  // the project's whole set; drafts left out are removed, confirmed ones can't be changed (void them instead)
  saveCommissions: (projectId: string, rows: { id?: string; recipient: string; role: string; rate: number }[], confirm: boolean, acceptPolicyException?: boolean) => Promise<void>
  recordCommissionPayment: (p: PaymentInput & { arrangementId: string }) => Promise<void>
  updateSettings: (patch: Partial<Settings>) => Promise<void>
  // fills everything from the backend; loaded stays false until the first load finishes
  load: () => Promise<void>
  // the demo data, for the unit tests
  reset: () => void
}

// frozenWeeks: "weekStart:employeeId" for worker-weeks already paid or closed - their inputs are locked
export type Store = DataState & Actions & { loaded: boolean; frozenWeeks: Set<string> }

export interface ClientInput {
  name: string
  kind: Client['kind']
  phone: string
  email?: string
  address: string
  city: string
  notes?: string
}

export interface ProjectInput {
  name: string
  type: ProjectType
  // an existing client, or newClient to create one in the same transaction
  clientId?: string
  newClient?: ClientInput
  location: string
  description: string
  contractValue: number
  startDate: string
  expectedCompletion: string
  status: ProjectStatus
  notes?: string
  schedule: { description: string; dueDate: string; amount: number }[]
  delay?: DelayTerms
}

export interface ExpenseInput {
  date: string
  scope: ExpenseScope
  projectId?: string
  group: ExpenseGroup
  category: string
  description: string
  payee: string
  supplierId?: string
  amount: number
  dueDate?: string
  invoiceNo?: string
  notes?: string
  // a materials line: the server works the amount out from these
  quantity?: number
  unit?: string
  unitPrice?: number
  machineType?: string
  usageFrom?: string
  usageTo?: string
  billingPeriod?: string
}

const expenseBody = (e: ExpenseInput) => {
  const line = e.quantity != null && e.unit && e.unitPrice != null
  return {
    date: e.date, scope: code(e.scope), projectId: e.projectId, categoryId: categoryId(e.group, e.category), description: e.description,
    payee: e.payee, supplierId: e.supplierId, amount: line ? undefined : e.amount, dueDate: e.dueDate, invoiceNo: e.invoiceNo, notes: e.notes,
    quantity: line ? e.quantity : undefined, unitId: line ? unitId(e.unit!) : undefined, unitPrice: line ? e.unitPrice : undefined,
    machineType: e.machineType, usageFrom: e.usageFrom, usageTo: e.usageTo, billingPeriod: e.billingPeriod,
  }
}

const clientBody = (c: ClientInput) => ({ ...c, kind: code(c.kind) })
// units and payment methods: add the new names, remove the dropped ones (the server refuses removing one still in use)
async function saveList(path: string, before: string[], after: string[], idOf: (name: string) => number | undefined) {
  for (const name of after.filter((x) => !before.includes(x))) await apiPost(path, { name })
  for (const name of before.filter((x) => !after.includes(x))) {
    const id = idOf(name)
    if (id != null) await api('DELETE', `${path}/${id}`)
  }
}
const employeeBody = (e: New<Employee>) => ({
  name: e.name, role: e.role, phone: e.phone, address: e.address, dailyWage: e.dailyRate, overtimeRate: e.overtimeRate, joinedOn: e.joinedOn,
})
// same shape for every kind of payment and receipt on the backend
const paymentBody = (p: PaymentInput) => ({
  requestId: p.requestId, paymentDate: p.date, amount: p.amount, paymentMethodId: methodId(p.method), reference: p.reference, notes: p.notes,
})
const delayBody = (d: DelayTerms) => ({
  contractDeadline: d.contractDeadline, rule: code(d.rule), ratePerDay: d.ratePerDay, percentPerWeek: d.percentPerWeek,
  fixedAmount: d.fixedAmount, graceDays: d.graceDays, maxDeduction: d.maxDeduction, notes: d.notes,
})

export const useStore = create<Store>()((set, get) => {
  // after a save, read back what the server now has instead of guessing it locally
  const refreshProject = async (projectId: string) => {
    const b = await loadProject(projectId)
    set((s) => {
      const instIds = new Set(s.installments.filter((i) => i.projectId === projectId).map((i) => i.id))
      return {
        projects: s.projects.some((p) => p.id === projectId) ? s.projects.map((p) => (p.id === projectId ? b.project : p)) : [...s.projects, b.project],
        installments: [...s.installments.filter((i) => i.projectId !== projectId), ...b.installments],
        clientPayments: [...s.clientPayments.filter((p) => !instIds.has(p.installmentId)), ...b.clientPayments],
        writeOffs: [...s.writeOffs.filter((w) => !instIds.has(w.installmentId)), ...b.writeOffs],
      }
    })
  }
  const refreshClients = async () => set({ clients: await loadClients() })
  const refreshOtherIncome = async () => set(await loadOtherIncome())
  const refreshExpenses = async () => set(await loadExpenses())
  const refreshInvoices = async () => set(await loadSupplierInvoices())
  const refreshInventory = async () => set(await loadInventory())
  const refreshLabour = async () => set(await loadLabour())
  const refreshCommissions = async () => set(await loadCommissions())
  const projectOf = (installmentId: string) => {
    const inst = get().installments.find((i) => i.id === installmentId)
    if (!inst) throw new Error('That installment is no longer there. Reload the data.')
    return inst.projectId
  }
  const refreshActivity = async () => set({ activity: await loadActivity() })
  return {
    ...emptyState(),
    loaded: false,
    frozenWeeks: new Set<string>(),
    load: async () => set({ ...(await loadAll()), loaded: true }),
    addClient: async (c) => {
      const saved = mapClient(await apiPost('/clients', clientBody(c)))
      set((s) => ({ clients: [...s.clients, saved] }))
      return saved.id
    },
    updateClient: async (id, c) => {
      const saved = mapClient(await apiPut(`/clients/${id}`, { ...clientBody(c), version: versionOf('client', id) }))
      set((s) => ({ clients: s.clients.map((x) => (x.id === id ? saved : x)) }))
    },
    addProject: async (p) => {
      const created = await apiPost<{ id: number }>('/projects', {
        name: p.name, type: code(p.type), clientId: p.clientId, newClient: p.newClient && clientBody(p.newClient), location: p.location,
        description: p.description, contractValue: p.contractValue, startDate: p.startDate, expectedCompletion: p.expectedCompletion,
        status: code(p.status), notes: p.notes, schedule: p.schedule, delayTerms: p.delay && delayBody(p.delay),
      })
      const id = String(created.id)
      await Promise.all([refreshProject(id), p.newClient && refreshClients(), refreshActivity()])
      return id
    },
    setProjectStatus: async (id, status) => {
      await apiPut(`/projects/${id}/status`, { status: code(status), version: versionOf('project', id) })
      await Promise.all([refreshProject(id), refreshActivity()])
    },
    uploadAgreement: async (projectId, file) => {
      const form = new FormData()
      form.append('file', file)
      await api('PUT', `/projects/${projectId}/agreement`, form)
      await Promise.all([refreshProject(projectId), refreshActivity()])
    },
    saveDelayTerms: async (projectId, terms, enabled) => {
      await apiPut(`/projects/${projectId}/delay-terms`, { enabled, terms: delayBody(terms), version: versionOf('delay', projectId) })
      await Promise.all([refreshProject(projectId), refreshActivity()])
    },
    applyDelayDeduction: async (projectId, d) => {
      await apiPost(`/projects/${projectId}/delay-terms/deduction`, { ...d, version: versionOf('delay', projectId) })
      await Promise.all([refreshProject(projectId), refreshActivity()])
    },
    reverseDelayDeduction: async (projectId) => {
      await apiPost(`/projects/${projectId}/delay-terms/deduction/reverse`, { version: versionOf('delay', projectId) })
      await Promise.all([refreshProject(projectId), refreshActivity()])
    },
    reviseSchedule: async (projectId, rows) => {
      await apiPut(`/projects/${projectId}/schedule`, {
        installments: rows.map((r) => ({
          id: r.id, version: r.id ? versionOf('installment', r.id) : undefined,
          installment: { description: r.description, dueDate: r.dueDate, amount: r.amount },
        })),
      })
      await Promise.all([refreshProject(projectId), refreshActivity()])
    },
    addVariation: async (projectId, v) => {
      await apiPost(`/projects/${projectId}/variations`, v)
      await Promise.all([refreshProject(projectId), refreshActivity()])
    },
    billInstallment: async (id, date) => {
      const projectId = projectOf(id)
      await apiPost(`/projects/${projectId}/installments/${id}/bill`, { billedOn: date, version: versionOf('installment', id) })
      await Promise.all([refreshProject(projectId), refreshActivity()])
    },
    recordClientPayment: async (p) => {
      const projectId = projectOf(p.installmentId)
      await apiPost(`/projects/${projectId}/installments/${p.installmentId}/receipts`, paymentBody(p))
      await Promise.all([refreshProject(projectId), refreshActivity()])
    },
    writeOff: async (w) => {
      const projectId = projectOf(w.installmentId)
      await apiPost(`/projects/${projectId}/installments/${w.installmentId}/write-offs`, { date: w.date, amount: w.amount, reason: w.reason })
      await Promise.all([refreshProject(projectId), refreshActivity()])
    },
    addOtherIncome: async (i, received) => {
      const saved = await apiPost<{ id: number }>('/other-income', i)
      // "received now" is a separate receipt; if it fails the income itself is still recorded
      if (received && received.amount > 0) await apiPost(`/other-income/${saved.id}/receipts`, paymentBody(received))
      await refreshOtherIncome()
    },
    recordOtherIncomeReceipt: async (r) => {
      await apiPost(`/other-income/${r.incomeId}/receipts`, paymentBody(r))
      await refreshOtherIncome()
    },
    addExpense: async (e, payment) => {
      const saved = await apiPost<{ id: number; warning?: string }>('/expenses', {
        ...expenseBody(e), initialPayment: payment && payment.amount > 0 ? paymentBody(payment) : undefined,
      })
      await Promise.all([refreshExpenses(), refreshActivity()])
      return { id: String(saved.id), warning: saved.warning ?? undefined }
    },
    updateExpense: async (id, e) => {
      const saved = await apiPut<{ warning?: string }>(`/expenses/${id}`, { ...expenseBody(e), version: versionOf('expense', id) })
      await Promise.all([refreshExpenses(), refreshActivity()])
      return { warning: saved.warning ?? undefined }
    },
    recordExpensePayment: async (p) => {
      await apiPost(`/expenses/${p.expenseId}/payments`, paymentBody(p))
      await Promise.all([refreshExpenses(), refreshActivity()])
    },
    addSupplier: async (x) => {
      const saved = await apiPost<{ id: number }>('/suppliers', x)
      set({ suppliers: await loadSuppliers() })
      return String(saved.id)
    },
    addSupplierInvoice: async (inv, payment, file, onSaved) => {
      const saved = await apiPost<{ invoice: { id: number } }>('/supplier-invoices', {
        supplierId: inv.supplierId, invoiceNo: inv.invoiceNo, invoiceDate: inv.date, dueDate: inv.dueDate,
        allocation: inv.projectId ? 'PROJECT' : 'STOCK', projectId: inv.projectId, notes: inv.notes,
        lines: inv.lines.map((l) => ({
          materialId: inv.projectId ? undefined : l.materialId, description: l.description, quantity: l.qty, unitId: unitId(l.unit), unitPrice: l.unitPrice,
        })),
        initialPayment: payment && payment.amount > 0 ? paymentBody(payment) : undefined,
      })
      const id = String(saved.invoice.id)
      onSaved?.()
      try {
        if (file) {
          const form = new FormData()
          form.append('file', file)
          await api('PUT', `/supplier-invoices/${id}/attachment`, form)
        }
      } finally {
        // a stock invoice also brings stock in, so the ledger changes too
        await Promise.all([refreshInvoices(), refreshInventory(), refreshActivity()])
      }
      return id
    },
    recordSupplierPayment: async (p) => {
      await apiPost(`/supplier-invoices/${p.invoiceId}/payments`, paymentBody(p))
      await Promise.all([refreshInvoices(), refreshActivity()])
    },
    addMaterial: async (m) => {
      await apiPost('/inventory/materials', { sku: m.sku, name: m.name, category: m.category, unitId: unitId(m.unit), defaultUnitCost: m.unitCost })
      await refreshInventory()
    },
    addStockMovement: async (m) => {
      const base = { requestId: m.requestId, date: m.date, reference: m.reference, notes: m.notes }
      const posted = await apiPost<{ totalCost: number; projectId?: number }>(...(
        m.type === 'Received' ? ['/inventory/receipts', { ...base, materialId: m.materialId, quantity: m.qty, unitCost: m.unitCost }]
        : m.type === 'Issued' ? ['/inventory/issues', { ...base, materialId: m.materialId, projectId: m.projectId, quantity: -m.qty }]
        : m.type === 'Returned' ? ['/inventory/returns', { ...base, issueId: m.returnedFromId, quantity: m.qty }]
        : ['/inventory/adjustments', { ...base, materialId: m.materialId, quantityChange: m.qty, reason: m.reason, unitCost: m.qty > 0 ? m.unitCost : undefined }]
      ) as [string, unknown])
      await Promise.all([refreshInventory(), refreshActivity()])
      return { totalCost: posted.totalCost, projectId: posted.projectId == null ? undefined : String(posted.projectId) }
    },
    addEmployee: async (e) => {
      await apiPost('/employees', { ...employeeBody(e), status: code(e.status) })
      await refreshLabour()
    },
    updateEmployee: async (id, e) => {
      const saved = await apiPut<{ version: number; status: string }>(`/employees/${id}`, { ...employeeBody(e), version: versionOf('employee', id) })
      // active/inactive has its own endpoint, so an ordinary edit can't flip it by accident
      if (saved.status !== code(e.status)) await apiPut(`/employees/${id}/status`, { status: code(e.status), version: saved.version })
      await refreshLabour()
    },
    saveAttendance: async (date, rows) => {
      await apiPut(`/attendance/${date}`, {
        entries: rows.map((r) => ({ employeeId: r.employeeId, status: code(r.status), projectId: r.projectId, notes: r.notes })),
      })
      await refreshLabour()
    },
    addOvertime: async (o) => {
      await apiPost('/overtime', { employeeId: o.employeeId, date: o.date, projectId: o.projectId, hours: o.hours, hourlyRate: o.rate, recordedBy: o.recordedBy, notes: o.notes })
      await refreshLabour()
    },
    approveOvertime: async (id, approved) => {
      await apiPut(`/overtime/${id}/approval`, { approved, version: versionOf('overtime', id) })
      await refreshLabour()
    },
    addAdvance: async (a) => {
      await apiPost('/payroll/advances', { ...paymentBody(a), employeeId: a.employeeId })
      await refreshLabour()
    },
    setPayrollAdjustment: async (a) => {
      await apiPut(`/payroll/weeks/${a.weekStart}/employees/${a.employeeId}/adjustment`, {
        advanceDeduction: a.advanceDeduction, otherDeduction: a.otherDeduction, otherNote: a.otherNote,
        version: versionOf('adjustment', adjustmentKey(a.weekStart, a.employeeId)) ?? null,
      })
      await refreshLabour()
    },
    recordPayrollCorrection: async (ws, emp, c) => {
      await apiPost(`/payroll/weeks/${ws}/employees/${emp}/corrections`, c)
      await refreshLabour()
    },
    voidPayrollCorrection: async (ws, emp, id, version, reason) => {
      await apiPost(`/payroll/weeks/${ws}/employees/${emp}/corrections/${id}/void`, { reason, version })
      await refreshLabour()
    },
    voidPayrollPayment: async (ws, emp, id, version, reason) => {
      await apiPost(`/payroll/weeks/${ws}/employees/${emp}/payments/${id}/void`, { reason, version })
      await refreshLabour()
    },
    closePayrollWeek: async (ws, emp) => {
      await apiPost(`/payroll/weeks/${ws}/employees/${emp}/close`)
      await refreshLabour()
    },
    recordPayrollPayments: async (rows) => {
      try {
        // one at a time: each locks that worker on the server, and a failure stops the rest
        for (const r of rows) await apiPost(`/payroll/weeks/${r.weekStart}/employees/${r.employeeId}/payments`, paymentBody(r))
      } finally {
        await refreshLabour()
      }
    },
    saveCommissions: async (projectId, rows, confirm, acceptPolicyException) => {
      await apiPut(`/projects/${projectId}/commissions`, {
        confirm, acceptPolicyException,
        arrangements: rows.map((r) => ({
          id: r.id, version: r.id ? versionOf('commission', r.id) : undefined, recipient: r.recipient, role: r.role, ratePct: r.rate,
        })),
      })
      await Promise.all([refreshCommissions(), refreshActivity()])
    },
    recordCommissionPayment: async (p) => {
      await apiPost(`/commissions/${p.arrangementId}/payments`, paymentBody(p))
      await Promise.all([refreshCommissions(), refreshActivity()])
    },
    updateSettings: async (patch) => {
      const now = get().settings
      if (patch.company || patch.supplierCreditDays != null) {
        const c = patch.company ?? now.company
        await apiPut('/settings/company', {
          ...c, supplierCreditDays: patch.supplierCreditDays ?? now.supplierCreditDays, version: versionOf('settings', 'company'),
        })
      }
      if (patch.commission) {
        // Super Admin only on the server; an Admin gets the 403 message
        await apiPut('/settings/commission-policy', {
          designCommissionPool: patch.commission.designCommissionPool, constructionDefaultRate: patch.commission.constructionDefaultRate,
          version: versionOf('settings', 'commission'),
        })
      }
      if (patch.wages) {
        await apiPut('/settings/wages', {
          defaultDailyRate: patch.wages.defaultDailyRate, hoursPerDay: patch.wages.hoursPerDay, overtimeMultiplier: patch.wages.overtimeMultiplier,
          payday: code(patch.wages.payday), version: versionOf('settings', 'wages'),
        })
      }
      if (patch.revenueCategories && patch.revenueCategories.join('|') !== now.revenueCategories.join('|')) {
        throw new Error('Revenue categories are fixed (Design, Construction, Consultation, Other Income) and can’t be changed here.')
      }
      try {
        // the lists are saved as what was added and what was removed
        if (patch.expenseCategories) {
          for (const group of Object.keys(patch.expenseCategories) as ExpenseGroup[]) {
            const before = now.expenseCategories[group], after = patch.expenseCategories[group]
            for (const name of after.filter((x) => !before.includes(x))) await apiPost('/settings/expense-categories', { group: code(group), name })
            for (const name of before.filter((x) => !after.includes(x))) {
              const id = findCategoryId(group, name)
              if (id != null) await api('DELETE', `/settings/expense-categories/${id}`)
            }
          }
        }
        if (patch.units) await saveList('/settings/units', now.units, patch.units, findUnitId)
        if (patch.paymentMethods) await saveList('/settings/payment-methods', now.paymentMethods, patch.paymentMethods, findMethodId)
      } finally {
        set({ settings: await loadSettings() })
      }
    },
    reset: () => set(() => ({ ...createSeed() })),
  }
})

/** Derived financial model, recalculated whenever a record changes. */
export function useData() {
  const s = useStore()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  return useMemo(() => derive(s), [
    s.clients, s.projects, s.installments, s.clientPayments, s.writeOffs, s.otherIncome, s.otherIncomeReceipts, s.expenses, s.expensePayments,
    s.suppliers, s.supplierInvoices, s.supplierPayments, s.materials, s.stockMovements, s.employees, s.attendance, s.overtime, s.advances,
    s.payrollAdjustments, s.payrollPayments, s.commissions, s.commissionPayments, s.activity, s.settings,
  ])
}
