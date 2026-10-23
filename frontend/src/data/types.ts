export type ProjectType = 'Design' | 'Construction' | 'Consultation'
export type ProjectStatus = 'Planning' | 'In Progress' | 'On Hold' | 'Completed' | 'Cancelled'
export type ExpenseGroup = 'Design' | 'Construction' | 'Consultation' | 'Company'
export type ExpenseScope = 'Project' | 'Company'

export interface Client {
  id: string
  name: string
  kind: 'Individual' | 'Business'
  phone: string
  email?: string
  address: string
  city: string
  notes?: string
  createdAt: string
}

export interface AgreementDoc {
  fileName: string
  size: number
  uploadedAt: string
}

export type DelayRule = 'per_day' | 'percent_per_week' | 'fixed_amount'

/** Delay deduction (liquidated damages) — configured per agreement, never hard-coded. */
export interface DelayTerms {
  enabled: boolean
  contractDeadline: string
  rule: DelayRule
  ratePerDay?: number
  percentPerWeek?: number
  fixedAmount?: number
  graceDays?: number
  maxDeduction?: number
  actualCompletion?: string
  /** Final deduction applied; undefined until the owner applies it. */
  appliedAmount?: number
  appliedOn?: string
  /** Installment the applied deduction reduces (usually the final one). */
  appliedToInstallmentId?: string
  notes?: string
}

export interface Project {
  id: string
  code: string
  name: string
  type: ProjectType
  clientId: string
  location: string
  description: string
  /** Agreed contract value. Never changed by deductions. */
  contractValue: number
  startDate: string
  expectedCompletion: string
  actualCompletion?: string
  status: ProjectStatus
  notes?: string
  agreement?: AgreementDoc
  delay?: DelayTerms
  createdAt: string
}

/** One line of a client's payment schedule. Revenue is recorded when it is billed. */
export interface Installment {
  id: string
  projectId: string
  description: string
  dueDate: string
  amount: number
  billed: boolean
  billedOn?: string
}

export interface ClientPayment {
  id: string
  installmentId: string
  date: string
  amount: number
  method: string
  reference?: string
  notes?: string
}

export interface WriteOff {
  id: string
  installmentId: string
  date: string
  amount: number
  reason: string
}

/** Income not tied to a project (scrap sales, equipment hire to others…). */
export interface OtherIncome {
  id: string
  date: string
  description: string
  payer?: string
  amount: number
  dueDate?: string
  reference?: string
  notes?: string
}
export interface OtherIncomeReceipt {
  id: string
  incomeId: string
  date: string
  amount: number
  method: string
  reference?: string
}

export interface Expense {
  id: string
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
  attachment?: { fileName: string; size: number }
  details?: Record<string, string | number>
  notes?: string
}

export interface ExpensePayment {
  id: string
  expenseId: string
  date: string
  amount: number
  method: string
  reference?: string
}

export interface Supplier {
  id: string
  name: string
  contactPerson: string
  phone: string
  email?: string
  address: string
  supplies: string
  creditDays: number
}

export interface InvoiceLine {
  id: string
  description: string
  materialId?: string
  qty: number
  unit: string
  unitPrice: number
}

export interface SupplierInvoice {
  id: string
  supplierId: string
  invoiceNo: string
  date: string
  dueDate: string
  /** Project the materials were delivered to; undefined = received into stock. */
  projectId?: string
  lines: InvoiceLine[]
  notes?: string
  attachment?: { fileName: string; size: number }
}

export interface SupplierPayment {
  id: string
  invoiceId: string
  date: string
  amount: number
  method: string
  reference?: string
}

export interface Material {
  id: string
  sku: string
  name: string
  category: string
  unit: string
  reorderLevel: number
  unitCost: number
}

export type MovementType = 'Received' | 'Issued' | 'Adjustment'
export interface StockMovement {
  id: string
  materialId: string
  date: string
  type: MovementType
  /** Signed: + into stock, − out of stock. */
  qty: number
  unitCost: number
  projectId?: string
  invoiceId?: string
  reason?: string
  reference?: string
}

export interface Employee {
  id: string
  name: string
  role: string
  phone: string
  dailyRate: number
  overtimeRate: number
  status: 'Active' | 'Inactive'
  joinedOn: string
  address?: string
}

export type AttendanceStatus = 'Present' | 'Half Day' | 'Absent'
export interface Attendance {
  date: string
  employeeId: string
  status: AttendanceStatus
  projectId?: string
  notes?: string
}

export interface Overtime {
  id: string
  date: string
  employeeId: string
  projectId?: string
  hours: number
  rate: number
  approved: boolean
  recordedBy: string
  notes?: string
}

export interface Advance {
  id: string
  date: string
  employeeId: string
  amount: number
  method: string
  notes?: string
}

/** Owner's overrides for a payroll week (default deductions are calculated). */
export interface PayrollAdjustment {
  weekStart: string
  employeeId: string
  advanceDeduction?: number
  otherDeduction?: number
  otherNote?: string
}

export interface PayrollPayment {
  id: string
  weekStart: string
  employeeId: string
  date: string
  amount: number
  method: string
}

export interface CommissionArrangement {
  id: string
  projectId: string
  recipient: string
  role: string
  rate: number
  status: 'Pending' | 'Confirmed'
  confirmedOn?: string
  notes?: string
}

export interface CommissionPayment {
  id: string
  arrangementId: string
  date: string
  amount: number
  method: string
  reference?: string
}

export interface ActivityEntry {
  id: string
  date: string
  projectId?: string
  text: string
  kind: 'payment' | 'expense' | 'document' | 'status' | 'commission' | 'note' | 'writeoff' | 'deduction'
}

export interface Settings {
  company: { name: string; tagline: string; address: string; phone: string; email: string; registration: string; owner: string }
  financialYearStart: string
  revenueCategories: string[]
  expenseCategories: Record<ExpenseGroup, string[]>
  commission: { designCompanyShare: number; designCommissionPool: number; constructionDefaultRate?: number }
  wages: { hoursPerDay: number; overtimeMultiplier: number; defaultDailyRate: number; payday: string }
  units: string[]
  projectStatuses: ProjectStatus[]
  paymentMethods: string[]
  supplierCreditDays: number
}

export interface DataState {
  clients: Client[]
  projects: Project[]
  installments: Installment[]
  clientPayments: ClientPayment[]
  writeOffs: WriteOff[]
  otherIncome: OtherIncome[]
  otherIncomeReceipts: OtherIncomeReceipt[]
  expenses: Expense[]
  expensePayments: ExpensePayment[]
  suppliers: Supplier[]
  supplierInvoices: SupplierInvoice[]
  supplierPayments: SupplierPayment[]
  materials: Material[]
  stockMovements: StockMovement[]
  employees: Employee[]
  attendance: Attendance[]
  overtime: Overtime[]
  advances: Advance[]
  payrollAdjustments: PayrollAdjustment[]
  payrollPayments: PayrollPayment[]
  commissions: CommissionArrangement[]
  commissionPayments: CommissionPayment[]
  activity: ActivityEntry[]
  settings: Settings
}
