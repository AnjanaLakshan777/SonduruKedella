/**
 * Fictional demonstration data for Sonduru Kedella (Anuradhapura).
 * Every derived figure in the app (receivables, profit, payroll…) is calculated from
 * these records, so dashboards, reports and tables always agree with each other.
 */
import type {
  ActivityEntry, Advance, Attendance, AttendanceStatus, Client, ClientPayment, CommissionArrangement, CommissionPayment,
  DataState, Employee, Expense, ExpensePayment, Installment, Material, OtherIncome, OtherIncomeReceipt, Overtime, Project,
  Settings, StockMovement, Supplier, SupplierInvoice, SupplierPayment, WriteOff,
} from './types'
import { addDays, eachDay, weekday } from '@/lib/dates'
import { computePayroll } from '@/lib/finance'

// deterministic random so the demo is identical on every load
function rng(seed: number) {
  return () => {
    seed |= 0
    seed = (seed + 0x6d2b79f5) | 0
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

export const settings: Settings = {
  company: {
    name: 'Sonduru Kedella',
    tagline: 'Home Design & Construction',
    address: 'No. 42, Maithripala Senanayake Mawatha, Anuradhapura',
    phone: '025 222 4567',
    email: 'accounts@sondurukedella.lk',
    registration: 'PV 00234567',
    owner: 'Asanka Senanayake',
  },
  financialYearStart: '2026-04-01',
  revenueCategories: ['Design', 'Construction', 'Consultation', 'Other Income'],
  expenseCategories: {
    Design: ['Printing', 'Electricity'],
    Construction: ['Materials', 'Labour', 'Machinery Rental', 'Electricity', 'Water', 'Engineer / Survey Visit', 'Commission', 'Vehicle Hire', 'Other Construction Expense'],
    Consultation: ['Transport'],
    Company: ['Marketing', 'Cutout and Banner', 'General Company Expense', 'Other Expense'],
  },
  commission: { designCompanyShare: 80, designCommissionPool: 20 },
  wages: { hoursPerDay: 8, overtimeMultiplier: 1.5, defaultDailyRate: 3500, payday: 'Saturday' },
  units: ['bag', 'kg', 'cube', 'cft', 'nos', 'length', 'box', 'litre', 'sq ft'],
  projectStatuses: ['Planning', 'In Progress', 'On Hold', 'Completed', 'Cancelled'],
  paymentMethods: ['Cash', 'Bank Transfer', 'Cheque', 'Online Payment'],
  supplierCreditDays: 14,
}

const clients: Client[] = [
  { id: 'c1', name: 'Dilani Jayawardena', kind: 'Individual', phone: '071 438 2291', email: 'dilani.j@gmail.com', address: 'No. 18, Mihintale Road', city: 'Anuradhapura', createdAt: '2026-04-20' },
  { id: 'c2', name: 'K. M. Bandara', kind: 'Individual', phone: '077 512 9043', address: 'Nuwarawewa Lake Road', city: 'Anuradhapura', createdAt: '2026-05-28' },
  { id: 'c3', name: 'Sanjeewa Rathnayake', kind: 'Individual', phone: '070 221 6684', email: 'sanjeewa.r@yahoo.com', address: 'No. 7, Jaya Mawatha', city: 'Anuradhapura', createdAt: '2026-07-06' },
  { id: 'c4', name: 'Rajarata Educational Trust', kind: 'Business', phone: '025 226 4410', email: 'admin@rajaratatrust.lk', address: 'Main Street', city: 'Kekirawa', notes: 'Payments by cheque, approved by the board each month.', createdAt: '2026-07-15' },
  { id: 'c5', name: 'Nirosha & Chaminda Herath', kind: 'Individual', phone: '076 904 1187', address: 'Hospital Road', city: 'Medawachchiya', createdAt: '2026-03-25' },
  { id: 'c6', name: 'Thilina Wickramasinghe', kind: 'Individual', phone: '071 660 3329', email: 'thilina.w@gmail.com', address: 'Hillside Lane', city: 'Mihintale', createdAt: '2026-09-26' },
  { id: 'c7', name: 'Dr. Anoma Senaratne', kind: 'Individual', phone: '077 318 7752', address: 'Kala Wewa Road', city: 'Thambuttegama', createdAt: '2026-08-30' },
  { id: 'c8', name: 'Wasantha Kumara', kind: 'Individual', phone: '072 845 0016', address: 'Dambulla Road', city: 'Kekirawa', createdAt: '2026-08-14' },
]

const projects: Project[] = [
  {
    id: 'p1', code: 'PRJ-2026-001', name: 'Medawachchiya Single-Storey House', type: 'Construction', clientId: 'c5', location: 'Medawachchiya',
    description: 'Three-bedroom single-storey house, 1,450 sq ft, with a car porch and rear verandah.',
    contractValue: 9_800_000, startDate: '2026-04-06', expectedCompletion: '2026-09-30', actualCompletion: '2026-10-12', status: 'Completed', createdAt: '2026-03-28',
    agreement: { fileName: 'Construction Agreement — PRJ-2026-001 (signed).pdf', size: 1_984_220, uploadedAt: '2026-04-02' },
    delay: { enabled: true, contractDeadline: '2026-09-30', rule: 'per_day', ratePerDay: 15_000, graceDays: 0, maxDeduction: 490_000, actualCompletion: '2026-10-12', appliedAmount: 180_000, appliedOn: '2026-10-14', appliedToInstallmentId: 'p1-i4', notes: 'Clause 14.2 — LKR 15,000 per calendar day after 30 Sep 2026, capped at 5% of the contract value. Handover on 12 Oct 2026 (12 days late).' },
  },
  {
    id: 'p2', code: 'PRJ-2026-002', name: 'Mihintale Road Two-Storey House', type: 'Construction', clientId: 'c1', location: 'Mihintale Road, Anuradhapura',
    description: 'Two-storey four-bedroom house, 2,600 sq ft, reinforced concrete slab and terrace.',
    contractValue: 18_500_000, startDate: '2026-05-04', expectedCompletion: '2027-01-30', status: 'In Progress', createdAt: '2026-04-24',
    agreement: { fileName: 'Construction Agreement — PRJ-2026-002 (signed).pdf', size: 2_411_724, uploadedAt: '2026-04-30' },
    delay: { enabled: true, contractDeadline: '2027-01-30', rule: 'per_day', ratePerDay: 25_000, graceDays: 0, maxDeduction: 925_000, notes: 'Clause 12 — LKR 25,000 per day, maximum 5% of contract value.' },
  },
  {
    id: 'p3', code: 'PRJ-2026-003', name: 'Nuwarawewa Lakeside Villa', type: 'Construction', clientId: 'c2', location: 'Nuwarawewa, Anuradhapura',
    description: 'Single-storey villa with lake-facing verandah, 2,950 sq ft, timber roof.',
    contractValue: 24_750_000, startDate: '2026-06-15', expectedCompletion: '2027-03-31', status: 'In Progress', createdAt: '2026-06-01',
    agreement: { fileName: 'Nuwarawewa Villa — Agreement.pdf', size: 3_120_400, uploadedAt: '2026-06-10' },
    delay: { enabled: true, contractDeadline: '2027-03-31', rule: 'percent_per_week', percentPerWeek: 0.5, graceDays: 14, maxDeduction: 2_475_000, notes: '0.5% of contract value per full week after a 14-day grace period, capped at 10%.' },
  },
  {
    id: 'p4', code: 'PRJ-2026-004', name: 'Kekirawa School Library Block', type: 'Construction', clientId: 'c4', location: 'Kekirawa',
    description: 'Two-storey library block for the trust’s school: reading hall, store and toilets.',
    contractValue: 32_400_000, startDate: '2026-08-01', expectedCompletion: '2027-05-31', status: 'In Progress', createdAt: '2026-07-18',
    agreement: { fileName: 'RET Library Block — Contract.pdf', size: 4_802_113, uploadedAt: '2026-07-28' },
    delay: { enabled: true, contractDeadline: '2027-05-31', rule: 'per_day', ratePerDay: 30_000, graceDays: 7, maxDeduction: 1_620_000, notes: 'LKR 30,000 per day after 7 days’ grace, capped at 5%.' },
  },
  {
    id: 'p5', code: 'PRJ-2026-005', name: 'Thambuttegama Clinic Renovation', type: 'Construction', clientId: 'c7', location: 'Thambuttegama',
    description: 'Renovation of a private medical clinic: new consultation rooms, roof repair, rewiring.',
    contractValue: 6_200_000, startDate: '2026-09-10', expectedCompletion: '2026-12-20', status: 'On Hold', createdAt: '2026-09-02',
    notes: 'On hold since 05 Oct 2026 — waiting for the Pradeshiya Sabha approval of the extension.',
    agreement: { fileName: 'Clinic Renovation Agreement.pdf', size: 1_204_000, uploadedAt: '2026-09-05' },
    delay: { enabled: true, contractDeadline: '2026-12-20', rule: 'fixed_amount', fixedAmount: 150_000, notes: 'A single LKR 150,000 deduction if not completed by the deadline (clause 9).' },
  },
  {
    id: 'p6', code: 'PRJ-2026-006', name: 'Jaya Mawatha House Plan & 3D', type: 'Design', clientId: 'c3', location: 'Jaya Mawatha, Anuradhapura',
    description: 'House plan, elevations and 3D exterior renders for a two-storey home.',
    contractValue: 450_000, startDate: '2026-07-10', expectedCompletion: '2026-08-20', actualCompletion: '2026-08-18', status: 'Completed', createdAt: '2026-07-08',
    agreement: { fileName: 'Design Engagement — Rathnayake.pdf', size: 402_330, uploadedAt: '2026-07-09' },
  },
  {
    id: 'p7', code: 'PRJ-2026-007', name: 'Mihintale Hillside Home — 3D & Plans', type: 'Design', clientId: 'c6', location: 'Mihintale',
    description: '3D design and approval drawings for a hillside home on a sloping block.',
    contractValue: 380_000, startDate: '2026-10-01', expectedCompletion: '2026-11-15', status: 'In Progress', createdAt: '2026-09-28',
  },
  {
    id: 'p8', code: 'PRJ-2026-008', name: 'Kekirawa Shop-House Design', type: 'Design', clientId: 'c8', location: 'Kekirawa',
    description: 'Ground-floor shop with residence above: plans, 3D and council submission set.',
    contractValue: 620_000, startDate: '2026-08-20', expectedCompletion: '2026-10-30', status: 'In Progress', createdAt: '2026-08-14',
    agreement: { fileName: 'Shop-House Design Agreement.pdf', size: 512_870, uploadedAt: '2026-08-18' },
  },
  {
    id: 'p9', code: 'PRJ-2026-009', name: 'Structural Review — Rajarata Hostel', type: 'Consultation', clientId: 'c4', location: 'Kekirawa',
    description: 'Site inspection and structural adequacy report for an existing two-storey hostel.',
    contractValue: 150_000, startDate: '2026-10-05', expectedCompletion: '2026-10-31', status: 'In Progress', createdAt: '2026-10-02',
  },
  {
    id: 'p10', code: 'PRJ-2026-010', name: 'Kekirawa Two-Unit Duplex', type: 'Construction', clientId: 'c8', location: 'Kekirawa',
    description: 'Two attached two-bedroom units on one block, built after the shop-house design is approved.',
    contractValue: 14_200_000, startDate: '2026-12-01', expectedCompletion: '2027-09-30', status: 'Planning', createdAt: '2026-10-16',
  },
]

const I = (id: string, projectId: string, description: string, dueDate: string, amount: number, billed: boolean): Installment =>
  ({ id, projectId, description, dueDate, amount, billed, billedOn: billed ? dueDate : undefined })

const installments: Installment[] = [
  I('p1-i1', 'p1', 'Advance (20%)', '2026-04-06', 1_960_000, true),
  I('p1-i2', 'p1', 'Foundation complete (25%)', '2026-05-15', 2_450_000, true),
  I('p1-i3', 'p1', 'Walls & roof (30%)', '2026-07-15', 2_940_000, true),
  I('p1-i4', 'p1', 'Completion & handover (25%)', '2026-10-20', 2_450_000, true),
  I('p2-i1', 'p2', 'Advance (15%)', '2026-05-04', 2_775_000, true),
  I('p2-i2', 'p2', 'Foundation complete (20%)', '2026-06-30', 3_700_000, true),
  I('p2-i3', 'p2', 'Ground-floor roof slab (20%)', '2026-10-02', 3_700_000, true),
  I('p2-i4', 'p2', 'Upper-floor slab (20%)', '2026-12-15', 3_700_000, false),
  I('p2-i5', 'p2', 'Completion & handover (25%)', '2027-01-30', 4_625_000, false),
  I('p3-i1', 'p3', 'Advance (20%)', '2026-06-15', 4_950_000, true),
  I('p3-i2', 'p3', 'Foundation complete (20%)', '2026-10-15', 4_950_000, true),
  I('p3-i3', 'p3', 'Structure & roof (25%)', '2026-12-15', 6_187_500, false),
  I('p3-i4', 'p3', 'Finishing (20%)', '2027-02-28', 4_950_000, false),
  I('p3-i5', 'p3', 'Completion & handover (15%)', '2027-03-31', 3_712_500, false),
  I('p4-i1', 'p4', 'Advance (20%)', '2026-08-01', 6_480_000, true),
  I('p4-i2', 'p4', 'Foundation complete (15%)', '2026-09-30', 4_860_000, true),
  I('p4-i3', 'p4', 'Ground-floor structure (25%)', '2026-11-30', 8_100_000, false),
  I('p4-i4', 'p4', 'Roof complete (20%)', '2027-02-28', 6_480_000, false),
  I('p4-i5', 'p4', 'Completion & handover (20%)', '2027-05-31', 6_480_000, false),
  I('p5-i1', 'p5', 'Advance (30%)', '2026-09-10', 1_860_000, true),
  I('p5-i2', 'p5', 'Rooms and roof complete (40%)', '2026-11-15', 2_480_000, false),
  I('p5-i3', 'p5', 'Completion (30%)', '2026-12-20', 1_860_000, false),
  I('p6-i1', 'p6', 'Design advance (50%)', '2026-07-10', 225_000, true),
  I('p6-i2', 'p6', 'Design fee balance (50%)', '2026-09-28', 225_000, true),
  I('p7-i1', 'p7', 'Design advance (50%)', '2026-10-01', 190_000, true),
  I('p7-i2', 'p7', 'Design fee balance (50%)', '2026-11-15', 190_000, false),
  I('p8-i1', 'p8', 'Design advance (40%)', '2026-08-20', 248_000, true),
  I('p8-i2', 'p8', 'Plans approved by client (30%)', '2026-09-25', 186_000, true),
  I('p8-i3', 'p8', 'Council submission set (30%)', '2026-10-30', 186_000, false),
  I('p9-i1', 'p9', 'Site inspection report', '2026-10-15', 75_000, true),
  { ...I('p9-i2', 'p9', 'Final structural report', '2026-10-31', 75_000, true), billedOn: '2026-10-20' },
  I('p10-i1', 'p10', 'Advance (20%)', '2026-12-01', 2_840_000, false),
  I('p10-i2', 'p10', 'Foundation complete (20%)', '2027-01-31', 2_840_000, false),
  I('p10-i3', 'p10', 'Structure & roof (30%)', '2027-04-30', 4_260_000, false),
  I('p10-i4', 'p10', 'Completion & handover (30%)', '2027-09-30', 4_260_000, false),
]

let cpN = 0
const CP = (installmentId: string, date: string, amount: number, method: string, reference?: string): ClientPayment =>
  ({ id: `cp${++cpN}`, installmentId, date, amount, method, reference })

const clientPayments: ClientPayment[] = [
  CP('p1-i1', '2026-04-06', 1_960_000, 'Bank Transfer', 'BOC TT 448210'),
  CP('p1-i2', '2026-05-18', 2_450_000, 'Bank Transfer', 'BOC TT 451907'),
  CP('p1-i3', '2026-07-15', 1_500_000, 'Cheque', 'Chq 102233'),
  CP('p1-i3', '2026-07-29', 1_440_000, 'Cheque', 'Chq 102241'),
  CP('p1-i4', '2026-10-20', 1_000_000, 'Bank Transfer', 'BOC TT 470115'),
  CP('p2-i1', '2026-05-04', 2_775_000, 'Bank Transfer', 'COM 77812'),
  CP('p2-i2', '2026-07-02', 3_700_000, 'Bank Transfer', 'COM 79140'),
  CP('p2-i3', '2026-10-09', 1_500_000, 'Bank Transfer', 'COM 82205'),
  CP('p3-i1', '2026-06-16', 4_950_000, 'Bank Transfer', 'PB 300918'),
  CP('p4-i1', '2026-08-03', 6_480_000, 'Cheque', 'Chq 540011'),
  CP('p4-i2', '2026-10-05', 4_860_000, 'Cheque', 'Chq 540089'),
  CP('p5-i1', '2026-09-10', 1_860_000, 'Bank Transfer', 'HNB 66120'),
  CP('p6-i1', '2026-07-10', 225_000, 'Cash', 'Receipt 0412'),
  CP('p7-i1', '2026-10-01', 190_000, 'Online Payment', 'LankaPay 8812'),
  CP('p8-i1', '2026-08-21', 248_000, 'Bank Transfer', 'SAMP 1190'),
  CP('p8-i2', '2026-10-02', 100_000, 'Cash', 'Receipt 0468'),
  CP('p9-i1', '2026-10-16', 75_000, 'Cheque', 'Chq 540102'),
]

const writeOffs: WriteOff[] = []

const otherIncome: OtherIncome[] = [
  { id: 'oi1', date: '2026-07-22', description: 'Sale of scrap steel and formwork offcuts', payer: 'Rajarata Scrap Collectors', amount: 45_000, reference: 'Receipt 0430' },
  { id: 'oi2', date: '2026-09-18', description: 'Concrete mixer hire to a neighbouring contractor (6 days)', payer: 'W. P. Construction', amount: 36_000, dueDate: '2026-10-02', reference: 'Inv OI-002' },
]
const otherIncomeReceipts: OtherIncomeReceipt[] = [
  { id: 'oir1', incomeId: 'oi1', date: '2026-07-22', amount: 45_000, method: 'Cash' },
  { id: 'oir2', incomeId: 'oi2', date: '2026-09-25', amount: 18_000, method: 'Cash' },
]

// ---------------- suppliers, invoices and stock ----------------
const suppliers: Supplier[] = [
  { id: 's1', name: 'Rajarata Hardware & Steel', contactPerson: 'Mahinda Rajapaksha', phone: '025 222 8814', email: 'sales@rajaratahardware.lk', address: 'Main Street, Anuradhapura', supplies: 'Cement, steel, binding wire, hardware', creditDays: 14 },
  { id: 's2', name: 'Malwathu Oya Sand Suppliers', contactPerson: 'Priyantha Silva', phone: '077 902 4410', address: 'Malwathu Oya Road, Anuradhapura', supplies: 'River sand (delivered to site)', creditDays: 14 },
  { id: 's3', name: 'Anuradhapura Block Works', contactPerson: 'Gamini Abeykoon', phone: '025 223 1097', address: 'Puttalam Road, Anuradhapura', supplies: 'Cement blocks, bricks', creditDays: 14 },
  { id: 's4', name: 'Lanka Metal & Aggregate', contactPerson: 'Nuwan Ekanayake', phone: '071 330 8812', address: 'Quarry Road, Mihintale', supplies: 'Metal / aggregate (delivered to site)', creditDays: 14 },
  { id: 's5', name: 'Nuwara Timber Depot', contactPerson: 'Rohana Weerasinghe', phone: '025 222 6630', address: 'Harischandra Mawatha, Anuradhapura', supplies: 'Timber, roofing timber', creditDays: 21 },
  { id: 's6', name: 'Kala Wewa Tiles & Sanitary', contactPerson: 'Shiromi Perera', phone: '025 225 7781', email: 'orders@kalawewatiles.lk', address: 'Kurunegala Road, Anuradhapura', supplies: 'Tiles, sanitaryware, PVC pipes', creditDays: 30 },
]

const materials: Material[] = [
  { id: 'm1', sku: 'CEM-50', name: 'Cement (50 kg bag)', category: 'Cement', unit: 'bag', unitCost: 2450 },
  { id: 'm2', sku: 'STL-12', name: 'Steel bar 12 mm (tor)', category: 'Steel', unit: 'kg', unitCost: 310 },
  { id: 'm3', sku: 'STL-10', name: 'Steel bar 10 mm (tor)', category: 'Steel', unit: 'kg', unitCost: 305 },
  { id: 'm4', sku: 'WIR-BD', name: 'Binding wire', category: 'Steel', unit: 'kg', unitCost: 480 },
  { id: 'm5', sku: 'BLK-06', name: 'Cement block 6"', category: 'Blocks & Bricks', unit: 'nos', unitCost: 95 },
  { id: 'm6', sku: 'BRK-ST', name: 'Clay brick (standard)', category: 'Blocks & Bricks', unit: 'nos', unitCost: 33 },
  { id: 'm7', sku: 'PVC-04', name: 'PVC pipe 4" (6 m length)', category: 'Plumbing', unit: 'length', unitCost: 2950 },
]

const L = (id: string, description: string, qty: number, unit: string, unitPrice: number, materialId?: string) => ({ id, description, qty, unit, unitPrice, materialId })
const INV = (id: string, supplierId: string, invoiceNo: string, date: string, credit: number, lines: SupplierInvoice['lines'], projectId?: string, notes?: string): SupplierInvoice =>
  ({ id, supplierId, invoiceNo, date, dueDate: addDays(date, credit), lines, projectId, notes, attachment: { fileName: `${invoiceNo}.pdf`, size: 180_000 + invoiceNo.length * 7000 } })

const supplierInvoices: SupplierInvoice[] = [
  INV('si1', 's1', 'RHS-4102', '2026-04-07', 14, [L('l1', 'Cement (50 kg bag)', 400, 'bag', 2400, 'm1'), L('l2', 'Steel bar 12 mm', 2000, 'kg', 300, 'm2'), L('l3', 'Binding wire', 50, 'kg', 470, 'm4')]),
  INV('si2', 's3', 'ABW-1021', '2026-04-09', 14, [L('l1', 'Cement block 6"', 8000, 'nos', 92, 'm5')]),
  INV('si3', 's2', 'MOS-0412', '2026-04-10', 14, [L('l1', 'River sand', 14, 'cube', 18_500)], 'p1'),
  INV('si4', 's4', 'LMA-2231', '2026-04-12', 14, [L('l1', 'Metal 3/4"', 10, 'cube', 16_000)], 'p1'),
  INV('si5', 's2', 'MOS-0518', '2026-05-08', 14, [L('l1', 'River sand', 18, 'cube', 18_500)], 'p2'),
  INV('si6', 's4', 'LMA-2298', '2026-05-10', 14, [L('l1', 'Metal 3/4"', 14, 'cube', 16_000)], 'p2'),
  INV('si7', 's1', 'RHS-4255', '2026-06-12', 14, [L('l1', 'Cement (50 kg bag)', 600, 'bag', 2450, 'm1'), L('l2', 'Steel bar 12 mm', 3500, 'kg', 310, 'm2'), L('l3', 'Steel bar 10 mm', 600, 'kg', 305, 'm3'), L('l4', 'Binding wire', 80, 'kg', 480, 'm4')]),
  INV('si8', 's3', 'ABW-1107', '2026-06-14', 14, [L('l1', 'Cement block 6"', 12_000, 'nos', 95, 'm5'), L('l2', 'Clay brick', 10_000, 'nos', 32, 'm6')]),
  INV('si9', 's2', 'MOS-0655', '2026-06-18', 14, [L('l1', 'River sand', 16, 'cube', 18_500)], 'p3'),
  INV('si10', 's4', 'LMA-2371', '2026-06-20', 14, [L('l1', 'Metal 3/4"', 12, 'cube', 16_000)], 'p3'),
  INV('si11', 's5', 'NTD-118', '2026-07-02', 21, [L('l1', 'Roofing timber (jak / mahogany)', 280, 'cft', 2400)], 'p1'),
  INV('si12', 's1', 'RHS-4398', '2026-08-03', 14, [L('l1', 'Cement (50 kg bag)', 700, 'bag', 2450, 'm1'), L('l2', 'Steel bar 12 mm', 4000, 'kg', 310, 'm2')]),
  INV('si13', 's2', 'MOS-0761', '2026-08-04', 14, [L('l1', 'River sand', 22, 'cube', 18_500)], 'p4'),
  INV('si14', 's4', 'LMA-2440', '2026-08-05', 14, [L('l1', 'Metal 3/4"', 18, 'cube', 16_000)], 'p4'),
  INV('si15', 's3', 'ABW-1180', '2026-08-06', 14, [L('l1', 'Cement block 6"', 10_000, 'nos', 95, 'm5')]),
  INV('si16', 's6', 'KWT-0093', '2026-08-25', 30, [L('l1', 'Floor tiles 2×2 (box)', 160, 'box', 3850)], 'p1'),
  INV('si17', 's6', 'KWT-0128', '2026-09-30', 30, [L('l1', 'PVC pipe 4" (6 m)', 60, 'length', 2950, 'm7')]),
  INV('si18', 's5', 'NTD-141', '2026-10-06', 21, [L('l1', 'Formwork and roofing timber', 120, 'cft', 2400)], 'p2'),
  INV('si19', 's4', 'LMA-2519', '2026-10-09', 14, [L('l1', 'Metal 3/4"', 8, 'cube', 16_000)], 'p3'),
  INV('si20', 's1', 'RHS-4471', '2026-10-13', 14, [L('l1', 'Cement (50 kg bag)', 300, 'bag', 2450, 'm1'), L('l2', 'Steel bar 12 mm', 1500, 'kg', 310, 'm2')]),
  INV('si21', 's2', 'MOS-0932', '2026-10-15', 14, [L('l1', 'River sand', 6, 'cube', 18_500), L('l2', 'Transport (long distance)', 1, 'nos', 31_500)], 'p2'),
  INV('si22', 's3', 'ABW-1215', '2026-10-20', 14, [L('l1', 'Cement block 6"', 3000, 'nos', 95, 'm5'), L('l2', 'Clay brick', 1000, 'nos', 33, 'm6')]),
]

const invoiceTotal = (inv: SupplierInvoice) => inv.lines.reduce((t, l) => t + l.qty * l.unitPrice, 0)
let spN = 0
const supplierPayments: SupplierPayment[] = []
for (const inv of supplierInvoices) {
  const total = invoiceTotal(inv)
  if (inv.dueDate < '2026-10-14') supplierPayments.push({ id: `sp${++spN}`, invoiceId: inv.id, date: addDays(inv.dueDate, -1), amount: total, method: total > 200_000 ? 'Bank Transfer' : 'Cash', reference: total > 200_000 ? `TT ${70000 + spN * 37}` : undefined })
}
supplierPayments.push({ id: `sp${++spN}`, invoiceId: 'si20', date: '2026-10-16', amount: 515_000, method: 'Bank Transfer', reference: 'TT 71120' })
supplierPayments.push({ id: `sp${++spN}`, invoiceId: 'si18', date: '2026-10-12', amount: 100_000, method: 'Cash' })

// stock received from stock invoices + issues to projects
const stockMovements: StockMovement[] = []
let smN = 0
for (const inv of supplierInvoices.filter((i) => !i.projectId)) {
  for (const l of inv.lines) if (l.materialId) stockMovements.push({ id: `sm${++smN}`, materialId: l.materialId, date: inv.date, type: 'Received', qty: l.qty, unitCost: l.unitPrice, invoiceId: inv.id, reference: inv.invoiceNo })
}
const issue = (materialId: string, date: string, qty: number, unitCost: number, projectId: string, reference?: string) =>
  stockMovements.push({ id: `sm${++smN}`, materialId, date, type: 'Issued', qty: -qty, unitCost, projectId, reference })
// cement
issue('m1', '2026-04-14', 120, 2400, 'p1', 'GIN-001'); issue('m1', '2026-05-12', 150, 2400, 'p1', 'GIN-006'); issue('m1', '2026-05-14', 130, 2400, 'p2', 'GIN-007')
issue('m1', '2026-06-20', 220, 2450, 'p2', 'GIN-012'); issue('m1', '2026-07-08', 100, 2450, 'p1', 'GIN-015'); issue('m1', '2026-07-10', 180, 2450, 'p3', 'GIN-016')
issue('m1', '2026-08-10', 200, 2450, 'p2', 'GIN-021'); issue('m1', '2026-08-12', 220, 2450, 'p4', 'GIN-022'); issue('m1', '2026-09-08', 160, 2450, 'p2', 'GIN-027')
issue('m1', '2026-09-12', 200, 2450, 'p3', 'GIN-028'); issue('m1', '2026-09-15', 40, 2450, 'p5', 'GIN-029'); issue('m1', '2026-10-15', 120, 2450, 'p4', 'GIN-034')
issue('m1', '2026-10-19', 20, 2450, 'p2', 'GIN-036')
// steel 12mm
issue('m2', '2026-04-15', 1200, 300, 'p1', 'GIN-002'); issue('m2', '2026-05-15', 800, 300, 'p2', 'GIN-008'); issue('m2', '2026-06-22', 700, 310, 'p2', 'GIN-013')
issue('m2', '2026-06-25', 600, 310, 'p1', 'GIN-014'); issue('m2', '2026-07-12', 1600, 310, 'p3', 'GIN-017'); issue('m2', '2026-08-14', 2000, 310, 'p4', 'GIN-023')
issue('m2', '2026-08-16', 1800, 310, 'p2', 'GIN-024'); issue('m2', '2026-09-10', 900, 310, 'p2', 'GIN-030'); issue('m2', '2026-09-14', 800, 310, 'p3', 'GIN-031')
issue('m2', '2026-10-16', 300, 310, 'p4', 'GIN-035')
// steel 10mm
issue('m3', '2026-07-03', 200, 305, 'p1', 'GIN-015A'); issue('m3', '2026-07-20', 400, 305, 'p2', 'GIN-018')
// binding wire
issue('m4', '2026-04-15', 15, 470, 'p1', 'GIN-002'); issue('m4', '2026-05-15', 20, 470, 'p2', 'GIN-008'); issue('m4', '2026-07-12', 25, 480, 'p3', 'GIN-017')
issue('m4', '2026-08-14', 30, 480, 'p4', 'GIN-023'); issue('m4', '2026-09-10', 20, 480, 'p2', 'GIN-030')
// blocks
issue('m5', '2026-04-20', 4500, 92, 'p1', 'GIN-003'); issue('m5', '2026-05-20', 3000, 92, 'p2', 'GIN-009'); issue('m5', '2026-06-24', 2000, 95, 'p1', 'GIN-014')
issue('m5', '2026-07-15', 6000, 95, 'p2', 'GIN-019'); issue('m5', '2026-08-18', 5500, 95, 'p3', 'GIN-025'); issue('m5', '2026-09-16', 4000, 95, 'p2', 'GIN-032')
issue('m5', '2026-09-22', 3500, 95, 'p4', 'GIN-033'); issue('m5', '2026-10-14', 2500, 95, 'p3', 'GIN-034A')
// bricks
issue('m6', '2026-06-26', 3000, 32, 'p1', 'GIN-014B'); issue('m6', '2026-07-22', 4000, 32, 'p2', 'GIN-020'); issue('m6', '2026-09-18', 1200, 32, 'p5', 'GIN-029A')
// pvc
issue('m7', '2026-10-05', 12, 2950, 'p2', 'GIN-033A')
stockMovements.push({ id: `sm${++smN}`, materialId: 'm1', date: '2026-09-02', type: 'Adjustment', qty: -8, unitCost: 2450, reason: 'Bags hardened after roof leak in the store' })
stockMovements.push({ id: `sm${++smN}`, materialId: 'm5', date: '2026-08-20', type: 'Adjustment', qty: 0, unitCost: 95, reason: 'Stock count — no difference' })

// ---------------- expenses ----------------
let exN = 0
const expenses: Expense[] = []
const expensePayments: ExpensePayment[] = []
let epN = 0
function EX(e: Omit<Expense, 'id'>, paid: 'full' | 'none' | number, method = 'Cash', payDate?: string) {
  const id = `e${++exN}`
  expenses.push({ id, ...e })
  const amt = paid === 'full' ? e.amount : paid === 'none' ? 0 : paid
  if (amt > 0) expensePayments.push({ id: `ep${++epN}`, expenseId: id, date: payDate ?? e.date, amount: amt, method })
}
const PE = (projectId: string, group: Expense['group'], category: string, date: string, description: string, payee: string, amount: number, extra: Partial<Expense> = {}) =>
  ({ scope: 'Project' as const, projectId, group, category, date, description, payee, amount, ...extra })
const CE = (category: string, date: string, description: string, payee: string, amount: number, extra: Partial<Expense> = {}) =>
  ({ scope: 'Company' as const, group: 'Company' as const, category, date, description, payee, amount, ...extra })

EX(PE('p1', 'Construction', 'Other Construction Expense', '2026-04-03', 'Building plan approval fee — Pradeshiya Sabha', 'Medawachchiya Pradeshiya Sabha', 45_000), 'full')
EX(PE('p1', 'Construction', 'Machinery Rental', '2026-04-08', 'Excavator (JCB) — foundation excavation, 2 days', 'Ruwanpura Machinery Hire', 85_000, { details: { Machine: 'JCB 3CX backhoe', 'Usage period': '08–09 Apr 2026', Rate: 'LKR 42,500 / day' } }), 'full', 'Bank Transfer')
EX(PE('p1', 'Construction', 'Engineer / Survey Visit', '2026-04-10', 'Structural engineer site visit — footing inspection', 'Eng. R. M. Dissanayake', 25_000), 'full')
EX(PE('p1', 'Construction', 'Water', '2026-05-06', 'Water bowser — curing, 6 loads', 'Lanka Water Bowser Services', 24_000), 'full')
EX(PE('p1', 'Construction', 'Electricity', '2026-06-30', 'Temporary site connection — CEB bills Apr–Jun', 'Ceylon Electricity Board', 18_500), 'full')
EX(PE('p1', 'Construction', 'Vehicle Hire', '2026-08-28', 'Lorry hire — tiles and timber transport', 'Senarath Transport', 32_000), 'full')
EX(PE('p2', 'Construction', 'Engineer / Survey Visit', '2026-04-28', 'Land survey and setting-out', 'Sumanadasa & Sons Surveyors', 35_000), 'full')
EX(PE('p2', 'Construction', 'Machinery Rental', '2026-05-06', 'Excavator — foundation and septic pit, 3 days', 'Ruwanpura Machinery Hire', 120_000, { details: { Machine: 'JCB 3CX backhoe', 'Usage period': '06–08 May 2026', Rate: 'LKR 40,000 / day' } }), 'full', 'Bank Transfer')
EX(PE('p2', 'Construction', 'Machinery Rental', '2026-08-01', 'Concrete mixer and vibrator — Aug–Oct', 'Nuwara Equipment Rentals', 96_000, { dueDate: '2026-10-31', details: { Machine: 'Concrete mixer + poker vibrator', 'Usage period': '01 Aug – 31 Oct 2026', Rate: 'LKR 32,000 / month' } }), 60_000, 'Cash', '2026-09-01')
EX(PE('p2', 'Construction', 'Electricity', '2026-09-30', 'Temporary site connection — CEB bills May–Sep', 'Ceylon Electricity Board', 22_400), 'full')
EX(PE('p2', 'Construction', 'Water', '2026-09-12', 'Water bowser — slab curing', 'Lanka Water Bowser Services', 9_800), 'full')
EX(PE('p2', 'Construction', 'Vehicle Hire', '2026-10-16', 'Crane truck — roof slab formwork lift', 'Senarath Transport', 48_000, { dueDate: '2026-10-30' }), 'none')
EX(PE('p3', 'Construction', 'Engineer / Survey Visit', '2026-06-08', 'Survey, soil test and setting-out', 'Sumanadasa & Sons Surveyors', 55_000), 'full')
EX(PE('p3', 'Construction', 'Machinery Rental', '2026-06-17', 'Excavator — 4 days, rock breaking', 'Ruwanpura Machinery Hire', 160_000, { details: { Machine: 'Excavator with breaker', 'Usage period': '17–20 Jun 2026', Rate: 'LKR 40,000 / day' } }), 'full', 'Bank Transfer')
EX(PE('p3', 'Construction', 'Electricity', '2026-09-30', 'Temporary site connection — CEB bills Jun–Sep', 'Ceylon Electricity Board', 12_600), 'full')
EX(PE('p3', 'Construction', 'Vehicle Hire', '2026-10-13', 'Tractor — moving rubble off site', 'Kumara Tractor Service', 28_000), 'full')
EX(PE('p4', 'Construction', 'Engineer / Survey Visit', '2026-07-30', 'Survey and structural engineer visits (2)', 'Eng. R. M. Dissanayake', 65_000), 'full', 'Bank Transfer')
EX(PE('p4', 'Construction', 'Machinery Rental', '2026-08-04', 'Excavator and roller — foundation, 5 days', 'Ruwanpura Machinery Hire', 210_000, { dueDate: '2026-10-15', details: { Machine: 'Excavator + vibratory roller', 'Usage period': '04–08 Aug 2026', Rate: 'LKR 42,000 / day' } }), 150_000, 'Bank Transfer', '2026-08-20')
EX(PE('p4', 'Construction', 'Electricity', '2026-10-05', 'Temporary site connection — CEB bills Aug–Sep', 'Ceylon Electricity Board', 15_800, { dueDate: '2026-10-25' }), 'none')
EX(PE('p4', 'Construction', 'Water', '2026-09-20', 'Water bowser — 2 loads', 'Lanka Water Bowser Services', 6_500), 'full')
EX(PE('p5', 'Construction', 'Engineer / Survey Visit', '2026-09-08', 'Engineer inspection of existing roof structure', 'Eng. R. M. Dissanayake', 20_000), 'full')
EX(PE('p6', 'Design', 'Printing', '2026-08-17', 'A1 plan prints and 3D colour prints — client set', 'Anu Printers', 6_800), 'full')
EX(PE('p6', 'Design', 'Electricity', '2026-08-31', 'Design studio electricity — rendering share (Jul–Aug)', 'Ceylon Electricity Board', 4_200, { notes: 'Design-work electricity, kept separate from site and office electricity.' }), 'full')
EX(PE('p7', 'Design', 'Printing', '2026-10-14', 'Concept presentation prints', 'Anu Printers', 3_900), 'full')
EX(PE('p8', 'Design', 'Printing', '2026-09-24', 'Approval drawing set — 3 copies', 'Anu Printers', 5_600), 'full')
EX(PE('p8', 'Design', 'Electricity', '2026-09-30', 'Design studio electricity — rendering share (Sep)', 'Ceylon Electricity Board', 3_100), 'full')
EX(PE('p9', 'Consultation', 'Transport', '2026-10-07', 'Three-wheeler and fuel — two site visits to Kekirawa', 'Self (fuel bills)', 8_400), 'full')
const months = ['2026-04', '2026-05', '2026-06', '2026-07', '2026-08', '2026-09', '2026-10']
for (const m of months) {
  EX(CE('General Company Expense', `${m}-05`, `Office rent — ${m === '2026-10' ? 'October' : ['April', 'May', 'June', 'July', 'August', 'September'][months.indexOf(m)]}`, 'L. Gunawardena (landlord)', 35_000), 'full', 'Bank Transfer')
  if (m !== '2026-10') EX(CE('General Company Expense', `${m}-28`, 'Office electricity — CEB', 'Ceylon Electricity Board', 7_800 + months.indexOf(m) * 150), 'full')
}
EX(CE('Marketing', '2026-06-02', 'Facebook and Instagram adverts — June', 'Meta Platforms', 25_000), 'full', 'Online Payment')
EX(CE('Marketing', '2026-09-03', 'Facebook and Instagram adverts — September', 'Meta Platforms', 25_000), 'full', 'Online Payment')
EX(CE('Cutout and Banner', '2026-05-18', 'Roadside cutout — Anuradhapura New Town junction', 'Rajarata Sign Arts', 48_000), 'full')
EX(CE('Cutout and Banner', '2026-08-02', 'Site banners (3) — Mihintale Road, Nuwarawewa, Kekirawa', 'Rajarata Sign Arts', 12_500), 'full')
EX(CE('Other Expense', '2026-09-30', 'Accountant — quarterly bookkeeping fee', 'K. Perera & Co. Chartered Accountants', 15_000, { dueDate: '2026-10-31' }), 'none')

// ---------------- labour ----------------
const employees: Employee[] = [
  { id: 'w1', name: 'Sunil Perera', role: 'Mason', phone: '071 223 4401', dailyRate: 4500, overtimeRate: 850, status: 'Active', joinedOn: '2026-04-01' },
  { id: 'w2', name: 'Ajith Kumara', role: 'Mason', phone: '077 640 1189', dailyRate: 4500, overtimeRate: 850, status: 'Active', joinedOn: '2026-04-01' },
  { id: 'w3', name: 'Nimal Dissanayake', role: 'Carpenter', phone: '070 318 2290', dailyRate: 4200, overtimeRate: 800, status: 'Active', joinedOn: '2026-04-01' },
  { id: 'w4', name: 'Kamal Bandara', role: 'Labourer', phone: '076 552 0913', dailyRate: 3200, overtimeRate: 600, status: 'Active', joinedOn: '2026-04-01' },
  { id: 'w5', name: 'Ruwan Priyankara', role: 'Labourer', phone: '071 908 4472', dailyRate: 3200, overtimeRate: 600, status: 'Active', joinedOn: '2026-04-01' },
  { id: 'w6', name: 'Chathura Madushanka', role: 'Labourer', phone: '077 115 6630', dailyRate: 3000, overtimeRate: 560, status: 'Active', joinedOn: '2026-04-01' },
  { id: 'w7', name: 'Lalith Wijeratne', role: 'Bar bender', phone: '072 664 2018', dailyRate: 4000, overtimeRate: 750, status: 'Active', joinedOn: '2026-04-01' },
  { id: 'w8', name: 'Saman Kumara', role: 'Electrician', phone: '075 330 9184', dailyRate: 4800, overtimeRate: 900, status: 'Active', joinedOn: '2026-09-01' },
  { id: 'w9', name: 'Pradeep Jayasinghe', role: 'Site supervisor', phone: '071 774 5520', dailyRate: 5500, overtimeRate: 1000, status: 'Active', joinedOn: '2026-04-01' },
  { id: 'w10', name: 'Dinesh Lakmal', role: 'Plumber', phone: '077 409 3361', dailyRate: 4600, overtimeRate: 860, status: 'Active', joinedOn: '2026-08-01' },
  { id: 'w11', name: 'Upali Herath', role: 'Labourer', phone: '070 882 1047', dailyRate: 3000, overtimeRate: 560, status: 'Inactive', joinedOn: '2026-04-01' },
]

/** Which project each worker was on, by date. */
function assignment(empId: string, d: string): string | undefined {
  switch (empId) {
    case 'w1': case 'w4': return d <= '2026-10-12' ? 'p1' : 'p3'
    case 'w2': case 'w5': return d < '2026-05-04' ? 'p1' : 'p2'
    case 'w3': return d <= '2026-07-31' ? 'p1' : 'p2'
    case 'w6': return d < '2026-06-15' ? 'p1' : 'p3'
    case 'w7': return d <= '2026-06-30' ? 'p1' : d <= '2026-07-31' ? 'p2' : 'p4'
    case 'w8': return d < '2026-09-10' ? 'p1' : d <= '2026-10-05' ? 'p5' : 'p2'
    case 'w9': return d < '2026-05-04' ? 'p1' : 'p2'
    case 'w10': return d <= '2026-08-31' ? 'p4' : d <= '2026-10-12' ? 'p1' : 'p2'
    case 'w11': return d <= '2026-05-30' ? 'p1' : undefined
  }
}

const attendance: Attendance[] = []
const rand = rng(20261022)
const HOLIDAYS = ['2026-05-01', '2026-05-30', '2026-05-31', '2026-06-29', '2026-08-27', '2026-09-26', '2026-10-25']
for (const d of eachDay('2026-04-06', '2026-10-22')) {
  if (weekday(d) === 0 || HOLIDAYS.includes(d)) continue
  for (const e of employees) {
    if (d < e.joinedOn) continue
    const projectId = assignment(e.id, d)
    if (!projectId) continue
    const r = rand()
    const status: AttendanceStatus = r < 0.06 ? 'Absent' : r < 0.11 ? 'Half Day' : 'Present'
    attendance.push({ date: d, employeeId: e.id, status, projectId })
  }
}
// today's sheet is partly filled: supervisor marks the afternoon
for (const a of attendance.filter((x) => x.date === '2026-10-22')) a.notes = undefined

let otN = 0
const OT = (date: string, employeeId: string, hours: number, approved = true, notes?: string): Overtime => {
  const e = employees.find((x) => x.id === employeeId)!
  return { id: `ot${++otN}`, date, employeeId, projectId: assignment(employeeId, date), hours, rate: e.overtimeRate, approved, recordedBy: 'Pradeep Jayasinghe', notes }
}
const overtime: Overtime[] = [
  OT('2026-09-08', 'w2', 3, true, 'Slab concreting ran late'), OT('2026-09-08', 'w5', 3, true, 'Slab concreting ran late'), OT('2026-09-08', 'w3', 2),
  OT('2026-09-23', 'w7', 2, true, 'Column reinforcement'), OT('2026-10-01', 'w2', 4, true, 'Ground-floor roof slab pour'),
  OT('2026-10-01', 'w5', 4, true, 'Ground-floor roof slab pour'), OT('2026-10-01', 'w9', 4, true, 'Supervising slab pour'),
  OT('2026-10-01', 'w3', 3, true, 'Formwork checks during pour'), OT('2026-10-09', 'w1', 2, true, 'Finishing handover snag list'),
  OT('2026-10-09', 'w4', 2, true, 'Site clean-up for handover'), OT('2026-10-15', 'w7', 3, true, 'Foundation beam reinforcement'),
  OT('2026-10-20', 'w1', 2, true, 'Footing excavation catch-up'), OT('2026-10-21', 'w6', 2, false, 'Rubble removal'), OT('2026-10-21', 'w8', 3, false, 'Temporary wiring for night work'),
]

const advances: Advance[] = [
  { id: 'ad1', date: '2026-09-16', employeeId: 'w4', amount: 4000, method: 'Cash', notes: 'Medical expense' },
  { id: 'ad2', date: '2026-10-08', employeeId: 'w3', amount: 2500, method: 'Cash' },
  { id: 'ad3', date: '2026-10-13', employeeId: 'w7', amount: 6000, method: 'Cash', notes: 'School fees' },
  { id: 'ad4', date: '2026-10-14', employeeId: 'w4', amount: 5000, method: 'Cash' },
  { id: 'ad5', date: '2026-10-16', employeeId: 'w5', amount: 3000, method: 'Cash' },
  { id: 'ad6', date: '2026-10-20', employeeId: 'w6', amount: 4000, method: 'Cash' },
  { id: 'ad7', date: '2026-10-21', employeeId: 'w1', amount: 10_000, method: 'Bank Transfer', notes: 'Family function' },
]

// ---------------- commissions ----------------
const commissions: CommissionArrangement[] = [
  { id: 'cm1', projectId: 'p1', recipient: 'Mahesh Gunasekara', role: 'Referral agent', rate: 3, status: 'Confirmed', confirmedOn: '2026-04-02' },
  { id: 'cm2', projectId: 'p2', recipient: 'Mahesh Gunasekara', role: 'Referral agent', rate: 2, status: 'Confirmed', confirmedOn: '2026-05-01' },
  { id: 'cm3', projectId: 'p2', recipient: 'Pradeep Jayasinghe', role: 'Site supervisor bonus', rate: 1, status: 'Confirmed', confirmedOn: '2026-05-01' },
  { id: 'cm4', projectId: 'p3', recipient: 'Harsha Wijesooriya', role: 'Referral agent', rate: 2.5, status: 'Confirmed', confirmedOn: '2026-06-12' },
  { id: 'cm5', projectId: 'p4', recipient: 'Harsha Wijesooriya', role: 'Referral agent', rate: 2, status: 'Confirmed', confirmedOn: '2026-07-30' },
  { id: 'cm6', projectId: 'p6', recipient: 'Kavindi Ranasinghe', role: '3D designer', rate: 12, status: 'Confirmed', confirmedOn: '2026-07-10' },
  { id: 'cm7', projectId: 'p6', recipient: 'Ishara Fernando', role: 'Draughtsman', rate: 8, status: 'Confirmed', confirmedOn: '2026-07-10' },
  { id: 'cm8', projectId: 'p7', recipient: 'Kavindi Ranasinghe', role: '3D designer', rate: 14, status: 'Pending', notes: 'Asked for a higher share because of the sloping site.' },
  { id: 'cm9', projectId: 'p7', recipient: 'Ishara Fernando', role: 'Draughtsman', rate: 10, status: 'Pending' },
  { id: 'cm10', projectId: 'p8', recipient: 'Kavindi Ranasinghe', role: '3D designer', rate: 12, status: 'Confirmed', confirmedOn: '2026-08-21' },
  { id: 'cm11', projectId: 'p8', recipient: 'Ishara Fernando', role: 'Draughtsman', rate: 8, status: 'Confirmed', confirmedOn: '2026-08-21' },
]
const commissionPayments: CommissionPayment[] = [
  { id: 'cmp1', arrangementId: 'cm1', date: '2026-04-10', amount: 147_000, method: 'Bank Transfer' },
  { id: 'cmp2', arrangementId: 'cm1', date: '2026-07-20', amount: 147_000, method: 'Bank Transfer' },
  { id: 'cmp3', arrangementId: 'cm2', date: '2026-05-08', amount: 185_000, method: 'Bank Transfer' },
  { id: 'cmp4', arrangementId: 'cm5', date: '2026-08-08', amount: 200_000, method: 'Cheque', reference: 'Chq 220871' },
  { id: 'cmp5', arrangementId: 'cm6', date: '2026-08-25', amount: 54_000, method: 'Cash' },
  { id: 'cmp6', arrangementId: 'cm7', date: '2026-08-25', amount: 36_000, method: 'Cash' },
  { id: 'cmp7', arrangementId: 'cm10', date: '2026-09-05', amount: 37_200, method: 'Cash' },
]

const activity: ActivityEntry[] = [
  { id: 'a1', date: '2026-10-05', projectId: 'p5', kind: 'status', text: 'Status changed from In Progress to On Hold — waiting for Pradeshiya Sabha approval.' },
  { id: 'a2', date: '2026-10-12', projectId: 'p1', kind: 'status', text: 'Project handed over to the client. Status changed to Completed.' },
  { id: 'a3', date: '2026-10-14', projectId: 'p1', kind: 'deduction', text: 'Delay deduction of LKR 180,000.00 applied (12 days × LKR 15,000) against the completion installment.' },
  { id: 'a4', date: '2026-10-16', projectId: 'p10', kind: 'status', text: 'Project created in Planning. Agreement not yet uploaded.' },
  { id: 'a5', date: '2026-10-03', projectId: 'p7', kind: 'commission', text: 'Commission shares entered (24%) — above the 20% design policy; awaiting owner decision.' },
]

function base(): DataState {
  return {
    clients, projects, installments, clientPayments, writeOffs, otherIncome, otherIncomeReceipts, expenses, expensePayments,
    suppliers, supplierInvoices, supplierPayments, materials, stockMovements, employees, attendance, overtime, advances,
    payrollAdjustments: [], payrollPayments: [], commissions, commissionPayments, activity, settings,
  }
}

/** Builds the initial state; weekly wages up to 17 Oct are paid (two workers part-paid that week). */
export function createSeed(): DataState {
  const s = base()
  const weeks = computePayroll(s)
  let n = 0
  for (const w of weeks) {
    if (w.weekStart > '2026-10-12') continue
    for (const line of w.lines) {
      if (line.net <= 0) continue
      let amount = line.net
      if (w.weekStart === '2026-10-12' && (line.employee.id === 'w5' || line.employee.id === 'w6')) amount = line.net - 10_000
      s.payrollPayments.push({ id: `pp${++n}`, weekStart: w.weekStart, employeeId: line.employee.id, date: w.weekEnd, amount, method: 'Cash' })
    }
  }
  return s
}
