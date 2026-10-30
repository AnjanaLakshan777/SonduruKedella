import { create } from 'zustand'
import { useMemo } from 'react'
import { createSeed } from '@/data/seed'
import { emptyState, loadAll } from '@/data/api-load'
import type {
  Advance, AgreementDoc, Attendance, Client, ClientPayment, CommissionArrangement, CommissionPayment, DataState, DelayTerms, Employee,
  Expense, ExpensePayment, Installment, Material, OtherIncome, OtherIncomeReceipt, Overtime, PayrollAdjustment, PayrollPayment, Project,
  Settings, StockMovement, Supplier, SupplierInvoice, SupplierPayment, WriteOff, ActivityEntry,
} from '@/data/types'
import { derive } from '@/lib/finance'
import { movementError, replayFifo, type CostedMovement } from '@/lib/fifo'
import { TODAY } from '@/lib/dates'

let n = 1000
export const uid = (p: string) => `${p}${++n}`

type New<T extends { id: string }> = Omit<T, 'id'>

interface Actions {
  addClient: (c: New<Client>) => string
  updateClient: (id: string, patch: Partial<Client>) => void
  addProject: (p: New<Project>, schedule: New<Installment>[]) => string
  updateProject: (id: string, patch: Partial<Project>, note?: string) => void
  setAgreement: (projectId: string, doc: AgreementDoc | undefined) => void
  setDelayTerms: (projectId: string, terms: DelayTerms, note?: string) => void
  replaceSchedule: (projectId: string, rows: (New<Installment> & { id?: string })[]) => void
  billInstallment: (id: string, date: string) => void
  recordClientPayment: (p: New<ClientPayment>) => void
  writeOff: (w: New<WriteOff>) => void
  addOtherIncome: (i: New<OtherIncome>, received?: { amount: number; method: string; date: string }) => void
  recordOtherIncomeReceipt: (r: New<OtherIncomeReceipt>) => void
  addExpense: (e: New<Expense>, payment?: New<ExpensePayment> & { expenseId?: string }) => string
  updateExpense: (id: string, patch: Partial<Expense>) => void
  recordExpensePayment: (p: New<ExpensePayment>) => void
  addSupplier: (x: New<Supplier>) => string
  addSupplierInvoice: (inv: New<SupplierInvoice>, payment?: { amount: number; method: string; date: string; reference?: string }) => string
  recordSupplierPayment: (p: New<SupplierPayment>) => void
  addMaterial: (m: New<Material>) => void
  /** Posts to the stock ledger and returns its FIFO cost; throws, saving nothing, if the movement isn't allowed. */
  addStockMovement: (m: New<StockMovement>) => CostedMovement
  addEmployee: (e: New<Employee>) => void
  updateEmployee: (id: string, patch: Partial<Employee>) => void
  saveAttendance: (date: string, rows: Attendance[]) => void
  addOvertime: (o: New<Overtime>) => void
  approveOvertime: (id: string, approved: boolean) => void
  addAdvance: (a: New<Advance>) => void
  setPayrollAdjustment: (a: PayrollAdjustment) => void
  recordPayrollPayments: (rows: New<PayrollPayment>[]) => void
  saveCommissions: (projectId: string, rows: (New<CommissionArrangement> & { id?: string })[], confirm: boolean) => void
  recordCommissionPayment: (p: New<CommissionPayment>) => void
  updateSettings: (patch: Partial<Settings>) => void
  log: (projectId: string | undefined, kind: ActivityEntry['kind'], text: string) => void
  // fills everything from the backend; loaded stays false until the first load finishes
  load: () => Promise<void>
  // the demo data, for the unit tests
  reset: () => void
}

export type Store = DataState & Actions & { loaded: boolean }

export const useStore = create<Store>()((set, get) => {
  const log = (projectId: string | undefined, kind: ActivityEntry['kind'], text: string) =>
    set((s) => ({ activity: [...s.activity, { id: uid('a'), date: TODAY, projectId, kind, text }] }))
  return {
    ...emptyState(),
    loaded: false,
    log,
    load: async () => set({ ...(await loadAll()), loaded: true }),
    addClient: (c) => {
      const id = uid('c')
      set((s) => ({ clients: [...s.clients, { ...c, id }] }))
      return id
    },
    updateClient: (id, patch) => set((s) => ({ clients: s.clients.map((c) => (c.id === id ? { ...c, ...patch } : c)) })),
    addProject: (p, schedule) => {
      const id = uid('p')
      set((s) => ({
        projects: [...s.projects, { ...p, id }],
        installments: [...s.installments, ...schedule.map((r) => ({ ...r, id: uid('i'), projectId: id }))],
      }))
      log(id, 'status', `Project created in ${p.status}.`)
      return id
    },
    updateProject: (id, patch, note) => {
      const before = get().projects.find((p) => p.id === id)
      set((s) => ({ projects: s.projects.map((p) => (p.id === id ? { ...p, ...patch } : p)) }))
      if (before && patch.status && patch.status !== before.status) log(id, 'status', `Status changed from ${before.status} to ${patch.status}.${note ? ` ${note}` : ''}`)
      else if (note) log(id, 'note', note)
    },
    setAgreement: (projectId, doc) => {
      set((s) => ({ projects: s.projects.map((p) => (p.id === projectId ? { ...p, agreement: doc } : p)) }))
      log(projectId, 'document', doc ? `Agreement uploaded: ${doc.fileName}` : 'Agreement removed.')
    },
    setDelayTerms: (projectId, terms, note) => {
      set((s) => ({ projects: s.projects.map((p) => (p.id === projectId ? { ...p, delay: terms } : p)) }))
      if (note) log(projectId, 'deduction', note)
    },
    replaceSchedule: (projectId, rows) =>
      set((s) => {
        const keep = new Set(rows.filter((r) => r.id).map((r) => r.id))
        const others = s.installments.filter((i) => i.projectId !== projectId || keep.has(i.id))
        const updated = others.map((i) => {
          const r = rows.find((x) => x.id === i.id)
          return r ? { ...i, ...r, id: i.id } : i
        })
        const added = rows.filter((r) => !r.id).map((r) => ({ ...r, id: uid('i'), projectId }) as Installment)
        return { installments: [...updated, ...added] }
      }),
    billInstallment: (id, date) => set((s) => ({ installments: s.installments.map((i) => (i.id === id ? { ...i, billed: true, billedOn: date } : i)) })),
    recordClientPayment: (p) => set((s) => ({ clientPayments: [...s.clientPayments, { ...p, id: uid('cp') }] })),
    writeOff: (w) => {
      set((s) => ({ writeOffs: [...s.writeOffs, { ...w, id: uid('wo') }] }))
      const inst = get().installments.find((i) => i.id === w.installmentId)
      if (inst) log(inst.projectId, 'writeoff', `Bad debt written off: ${w.reason}`)
    },
    addOtherIncome: (i, received) => {
      const id = uid('oi')
      set((s) => ({
        otherIncome: [...s.otherIncome, { ...i, id }],
        otherIncomeReceipts: received && received.amount > 0 ? [...s.otherIncomeReceipts, { id: uid('oir'), incomeId: id, ...received }] : s.otherIncomeReceipts,
      }))
    },
    recordOtherIncomeReceipt: (r) => set((s) => ({ otherIncomeReceipts: [...s.otherIncomeReceipts, { ...r, id: uid('oir') }] })),
    addExpense: (e, payment) => {
      const id = uid('e')
      set((s) => ({
        expenses: [...s.expenses, { ...e, id }],
        expensePayments: payment && payment.amount > 0 ? [...s.expensePayments, { ...payment, expenseId: id, id: uid('ep') }] : s.expensePayments,
      }))
      return id
    },
    updateExpense: (id, patch) => set((s) => ({ expenses: s.expenses.map((e) => (e.id === id ? { ...e, ...patch } : e)) })),
    recordExpensePayment: (p) => set((s) => ({ expensePayments: [...s.expensePayments, { ...p, id: uid('ep') }] })),
    addSupplier: (x) => {
      const id = uid('s')
      set((s) => ({ suppliers: [...s.suppliers, { ...x, id }] }))
      return id
    },
    addSupplierInvoice: (inv, payment) => {
      const id = uid('si')
      set((s) => {
        // each stock line is a receipt, so a new FIFO layer at its price; the material's default cost is left alone
        const moves: StockMovement[] = inv.projectId
          ? []
          : inv.lines.filter((l) => l.materialId).map((l) => ({ id: uid('sm'), materialId: l.materialId!, date: inv.date, type: 'Received', qty: l.qty, unitCost: l.unitPrice, invoiceId: id, reference: inv.invoiceNo }))
        return {
          supplierInvoices: [...s.supplierInvoices, { ...inv, id }],
          stockMovements: [...s.stockMovements, ...moves],
          supplierPayments: payment && payment.amount > 0 ? [...s.supplierPayments, { id: uid('sp'), invoiceId: id, ...payment }] : s.supplierPayments,
        }
      })
      return id
    },
    recordSupplierPayment: (p) => set((s) => ({ supplierPayments: [...s.supplierPayments, { ...p, id: uid('sp') }] })),
    addMaterial: (m) => set((s) => ({ materials: [...s.materials, { ...m, id: uid('m') }] })),
    addStockMovement: (m) => {
      const s = get()
      const mat = s.materials.find((x) => x.id === m.materialId)
      const problem = movementError(s.stockMovements, m, mat?.unit, mat?.name)
      if (problem) throw new Error(problem)
      const posted = { ...m, id: uid('sm') }
      const stockMovements = [...s.stockMovements, posted]
      set({ stockMovements })
      return replayFifo(stockMovements).costed.get(posted.id)!
    },
    addEmployee: (e) => set((s) => ({ employees: [...s.employees, { ...e, id: uid('w') }] })),
    updateEmployee: (id, patch) => set((s) => ({ employees: s.employees.map((e) => (e.id === id ? { ...e, ...patch } : e)) })),
    saveAttendance: (date, rows) => set((s) => ({ attendance: [...s.attendance.filter((a) => a.date !== date), ...rows] })),
    addOvertime: (o) => set((s) => ({ overtime: [...s.overtime, { ...o, id: uid('ot') }] })),
    approveOvertime: (id, approved) => set((s) => ({ overtime: s.overtime.map((o) => (o.id === id ? { ...o, approved } : o)) })),
    addAdvance: (a) => set((s) => ({ advances: [...s.advances, { ...a, id: uid('ad') }] })),
    setPayrollAdjustment: (a) =>
      set((s) => ({ payrollAdjustments: [...s.payrollAdjustments.filter((x) => !(x.weekStart === a.weekStart && x.employeeId === a.employeeId)), a] })),
    recordPayrollPayments: (rows) => set((s) => ({ payrollPayments: [...s.payrollPayments, ...rows.map((r) => ({ ...r, id: uid('pp') }))] })),
    saveCommissions: (projectId, rows, confirm) => {
      set((s) => {
        const others = s.commissions.filter((c) => c.projectId !== projectId)
        const next = rows.map((r) => ({
          ...r,
          id: r.id ?? uid('cm'),
          projectId,
          status: confirm ? ('Confirmed' as const) : r.status,
          confirmedOn: confirm ? (r.confirmedOn ?? TODAY) : r.confirmedOn,
        }))
        return { commissions: [...others, ...next] }
      })
      log(projectId, 'commission', confirm ? `Commission arrangement confirmed (${rows.map((r) => `${r.recipient} ${r.rate}%`).join(', ')}).` : 'Commission arrangement saved as draft.')
    },
    recordCommissionPayment: (p) => set((s) => ({ commissionPayments: [...s.commissionPayments, { ...p, id: uid('cmp') }] })),
    updateSettings: (patch) => set((s) => ({ settings: { ...s.settings, ...patch } })),
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
