// Dev only - a year of realistic-looking business for Sonduru Kedella, Anuradhapura: December 2025 to
// 3 November 2026. Names, phones and amounts are made up. seed-dev-data.ts loads it through the API
// when SEED_DATASET=business. Built from a fixed random seed, so every run gives the same data.
import type {
  Advance, Attendance, AttendanceStatus, Client, ClientPayment, CommissionArrangement, CommissionPayment, DataState, Employee,
  Expense, ExpensePayment, Installment, Material, OtherIncome, OtherIncomeReceipt, Overtime, Project, StockMovement, Supplier,
  SupplierInvoice, SupplierPayment, WriteOff,
} from '../src/data/types'
import { settings } from '../src/data/seed'
import { addDays, eachDay, weekday } from '../src/lib/dates'

// last day with data (a Tuesday); the current pay week is left unpaid
export const END = '2026-11-03'

function rng(seed: number) {
  return () => {
    seed |= 0
    seed = (seed + 0x6d2b79f5) | 0
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
const rand = rng(20251201)
// Sundays move to Monday, so deliveries and issues land on a work day
const workday = (d: string) => (weekday(d) === 0 ? addDays(d, 1) : d)

// ---------------- holidays (Poya days and public holidays; dates approximate) ----------------
export const holidays: { date: string; note: string }[] = [
  { date: '2025-12-04', note: 'Unduvap Poya' }, { date: '2025-12-25', note: 'Christmas' },
  { date: '2026-01-03', note: 'Duruthu Poya' }, { date: '2026-02-04', note: 'Independence Day' },
  { date: '2026-03-02', note: 'Medin Poya' }, { date: '2026-04-01', note: 'Bak Poya' },
  { date: '2026-04-13', note: 'Sinhala & Tamil New Year' }, { date: '2026-04-14', note: 'Sinhala & Tamil New Year' },
  { date: '2026-04-15', note: 'New Year leave' }, { date: '2026-05-01', note: 'Vesak Poya / May Day' },
  { date: '2026-05-02', note: 'Day after Vesak' }, { date: '2026-05-30', note: 'Adhi Poson Poya' },
  { date: '2026-06-29', note: 'Poson Poya' }, { date: '2026-07-29', note: 'Esala Poya' },
  { date: '2026-08-27', note: 'Nikini Poya' }, { date: '2026-09-26', note: 'Binara Poya' },
  { date: '2026-10-25', note: 'Vap Poya' },
]
const isHoliday = (d: string) => holidays.some((h) => h.date === d)

// ---------------- clients and projects ----------------
const clients: Client[] = [
  { id: 'c1', name: 'Dilani Jayawardena', kind: 'Individual', phone: '071 438 2291', email: 'dilani.j@gmail.com', address: 'No. 18, Mihintale Road', city: 'Anuradhapura', createdAt: '2025-12-02' },
  { id: 'c2', name: 'Wasantha Kumara', kind: 'Individual', phone: '072 845 0016', address: 'No. 54, Dambulla Road', city: 'Kekirawa', notes: 'Runs a hardware shop in Kekirawa town. Prefers calls after 6 pm.', createdAt: '2025-12-10' },
  { id: 'c3', name: 'Sanjeewa Rathnayake', kind: 'Individual', phone: '070 221 6684', email: 'sanjeewa.r@yahoo.com', address: 'No. 7, Jaya Mawatha', city: 'Anuradhapura', createdAt: '2025-12-18' },
  { id: 'c4', name: 'K. M. Bandara', kind: 'Individual', phone: '077 512 9043', address: 'Nuwarawewa Lake Road', city: 'Anuradhapura', createdAt: '2026-01-05' },
  { id: 'c5', name: 'Sri Sudarshanaramaya Temple', kind: 'Business', phone: '025 227 3318', address: 'Temple Road', city: 'Talawa', notes: 'Building fund run by the dayaka sabha. Payments by cheque after each committee meeting.', createdAt: '2026-02-09' },
  { id: 'c6', name: 'Nirosha & Chaminda Herath', kind: 'Individual', phone: '076 904 1187', address: 'Hospital Road', city: 'Medawachchiya', createdAt: '2026-03-27' },
  { id: 'c7', name: 'Chamari Dissanayake', kind: 'Individual', phone: '071 552 8830', email: 'chamari.d@gmail.com', address: 'Kurunegala Road', city: 'Galenbindunuwewa', createdAt: '2026-04-29' },
  { id: 'c8', name: 'Dr. Anoma Senaratne', kind: 'Individual', phone: '077 318 7752', email: 'anoma.clinic@gmail.com', address: 'Kala Wewa Road', city: 'Thambuttegama', createdAt: '2026-05-22' },
  { id: 'c9', name: 'Rajarata Educational Trust', kind: 'Business', phone: '025 226 4410', email: 'admin@rajaratatrust.lk', address: 'Main Street', city: 'Kekirawa', notes: 'Approvals go through the board, which meets on the first Monday of the month.', createdAt: '2026-08-05' },
  { id: 'c10', name: 'Thilina Wickramasinghe', kind: 'Individual', phone: '071 660 3329', email: 'thilina.w@gmail.com', address: 'Hillside Lane', city: 'Mihintale', createdAt: '2026-09-26' },
  { id: 'c11', name: 'Ruwan Ekanayake', kind: 'Individual', phone: '070 884 2156', address: 'Padaviya Road', city: 'Horowpothana', createdAt: '2026-10-15' },
  { id: 'c12', name: 'Priyanka Gunawardena', kind: 'Individual', phone: '077 230 4498', email: 'priyanka.g@gmail.com', address: 'Eppawala Junction', city: 'Eppawala', createdAt: '2026-10-22' },
]

const agreement = (fileName: string, size: number, uploadedAt: string) => ({ fileName, size, uploadedAt })

// created in this order, so the backend numbers them PRJ-2025-001.. and PRJ-2026-001.. by start year
const projects: Project[] = [
  {
    id: 'p1', code: '', name: 'Mihintale Road Single-Storey House', type: 'Construction', clientId: 'c1', location: 'Mihintale Road, Anuradhapura',
    description: 'Three-bedroom single-storey house, 1,550 sq ft, with car porch and rear verandah.',
    contractValue: 10_500_000, startDate: '2025-12-08', expectedCompletion: '2026-06-30', actualCompletion: '2026-07-14', status: 'Completed', createdAt: '2025-12-02',
    agreement: agreement('Construction Agreement - Jayawardena (signed).pdf', 1_984_220, '2025-12-05'),
    delay: { enabled: true, contractDeadline: '2026-06-30', rule: 'per_day', ratePerDay: 12_000, graceDays: 0, maxDeduction: 525_000, actualCompletion: '2026-07-14', appliedAmount: 168_000, appliedToInstallmentId: 'p1-i4', notes: 'Clause 11: LKR 12,000 per day after 30 Jun 2026, capped at 5%. Handed over 14 Jul 2026, 14 days late.' },
  },
  {
    id: 'p2', code: '', name: 'Kekirawa Shop & Residence - Plans and 3D', type: 'Design', clientId: 'c2', location: 'Dambulla Road, Kekirawa',
    description: 'Ground-floor shop with a two-bedroom residence above: plans, 3D renders and the council submission set.',
    contractValue: 560_000, startDate: '2025-12-15', expectedCompletion: '2026-02-15', actualCompletion: '2026-02-12', status: 'Completed', createdAt: '2025-12-10',
    agreement: agreement('Design Agreement - Kumara Shop-House.pdf', 512_870, '2025-12-12'),
  },
  {
    id: 'p3', code: '', name: 'Jaya Mawatha House Plan & 3D', type: 'Design', clientId: 'c3', location: 'Jaya Mawatha, Anuradhapura',
    description: 'Two-storey house plan, elevations and 3D exterior renders.',
    contractValue: 420_000, startDate: '2025-12-22', expectedCompletion: '2026-01-31', actualCompletion: '2026-01-28', status: 'Completed', createdAt: '2025-12-18',
  },
  {
    id: 'p4', code: '', name: 'Nuwarawewa Two-Storey House', type: 'Construction', clientId: 'c4', location: 'Nuwarawewa, Anuradhapura',
    description: 'Two-storey four-bedroom house, 2,650 sq ft, reinforced concrete slab and roof terrace facing the tank.',
    contractValue: 19_800_000, startDate: '2026-01-12', expectedCompletion: '2026-11-30', status: 'In Progress', createdAt: '2026-01-05',
    agreement: agreement('Construction Agreement - Bandara (signed).pdf', 2_411_724, '2026-01-09'),
    delay: { enabled: true, contractDeadline: '2026-11-30', rule: 'per_day', ratePerDay: 25_000, graceDays: 0, maxDeduction: 990_000, notes: 'Clause 12: LKR 25,000 per day, at most 5% of the contract value.' },
  },
  {
    id: 'p5', code: '', name: 'Talawa Temple Dharmashala Hall', type: 'Construction', clientId: 'c5', location: 'Talawa',
    description: 'Single-storey preaching hall, 2,200 sq ft, with a store room and toilets.',
    contractValue: 12_400_000, startDate: '2026-02-16', expectedCompletion: '2026-10-31', status: 'On Hold', createdAt: '2026-02-09',
    notes: 'On hold since 14 Sep 2026: the building fund is waiting for the Kathina donations.',
    agreement: agreement('Dharmashala Hall - Contract.pdf', 3_120_400, '2026-02-12'),
  },
  {
    id: 'p6', code: '', name: 'Kekirawa Shop & Residence', type: 'Construction', clientId: 'c2', location: 'Dambulla Road, Kekirawa',
    description: 'Building the approved shop-house: ground-floor shop, residence above, 2,400 sq ft.',
    contractValue: 15_600_000, startDate: '2026-03-02', expectedCompletion: '2026-12-31', status: 'In Progress', createdAt: '2026-02-23',
    agreement: agreement('Construction Agreement - Kumara Shop-House.pdf', 2_804_113, '2026-02-27'),
    delay: { enabled: true, contractDeadline: '2026-12-31', rule: 'percent_per_week', percentPerWeek: 0.5, graceDays: 14, maxDeduction: 1_560_000, notes: '0.5% of the contract value per full week after 14 days of grace, capped at 10%.' },
  },
  {
    id: 'p7', code: '', name: 'Medawachchiya House Renovation', type: 'Construction', clientId: 'c6', location: 'Hospital Road, Medawachchiya',
    description: 'New kitchen and bathroom, roof repair, rewiring and repainting of an existing house.',
    contractValue: 4_200_000, startDate: '2026-04-06', expectedCompletion: '2026-07-31', actualCompletion: '2026-07-29', status: 'Completed', createdAt: '2026-03-27',
    agreement: agreement('Renovation Agreement - Herath.pdf', 1_204_000, '2026-04-02'),
    delay: { enabled: true, contractDeadline: '2026-07-31', rule: 'fixed_amount', fixedAmount: 100_000, notes: 'A single LKR 100,000 deduction if not finished by the deadline. Finished two days early.' },
  },
  {
    id: 'p8', code: '', name: 'Galenbindunuwewa House Design', type: 'Design', clientId: 'c7', location: 'Galenbindunuwewa',
    description: 'Single-storey house plan and 3D renders.',
    contractValue: 350_000, startDate: '2026-05-04', expectedCompletion: '2026-06-30', actualCompletion: '2026-06-24', status: 'Completed', createdAt: '2026-04-29',
  },
  {
    id: 'p9', code: '', name: 'Thambuttegama Clinic Extension', type: 'Construction', clientId: 'c8', location: 'Kala Wewa Road, Thambuttegama',
    description: 'Extension to a private clinic: two consultation rooms, a pharmacy counter and a waiting area.',
    contractValue: 8_900_000, startDate: '2026-06-01', expectedCompletion: '2026-12-15', status: 'In Progress', createdAt: '2026-05-22',
    agreement: agreement('Clinic Extension Agreement.pdf', 1_620_500, '2026-05-28'),
    delay: { enabled: true, contractDeadline: '2026-12-15', rule: 'per_day', ratePerDay: 15_000, graceDays: 7, maxDeduction: 445_000, notes: 'LKR 15,000 per day after 7 days of grace, capped at 5%.' },
  },
  {
    id: 'p10', code: '', name: 'Structural Review - Rajarata School Hostel', type: 'Consultation', clientId: 'c9', location: 'Kekirawa',
    description: 'Site inspection and structural adequacy report for an existing two-storey hostel.',
    contractValue: 150_000, startDate: '2026-08-10', expectedCompletion: '2026-08-31', actualCompletion: '2026-08-28', status: 'Completed', createdAt: '2026-08-05',
  },
  {
    id: 'p11', code: '', name: 'Mihintale Hillside Home - 3D and Plans', type: 'Design', clientId: 'c10', location: 'Mihintale',
    description: '3D design and approval drawings for a hillside home on a sloping block.',
    contractValue: 380_000, startDate: '2026-10-01', expectedCompletion: '2026-11-20', status: 'In Progress', createdAt: '2026-09-26',
  },
  {
    id: 'p12', code: '', name: 'Horowpothana Land - Site Assessment', type: 'Consultation', clientId: 'c11', location: 'Padaviya Road, Horowpothana',
    description: 'Soil check, levels and a buildability note for a 40-perch block before purchase.',
    contractValue: 60_000, startDate: '2026-10-19', expectedCompletion: '2026-11-10', status: 'In Progress', createdAt: '2026-10-15',
  },
  {
    id: 'p13', code: '', name: 'Eppawala Two-Unit Rental Houses', type: 'Construction', clientId: 'c12', location: 'Eppawala',
    description: 'Two attached two-bedroom rental units on one block, 1,900 sq ft in total.',
    contractValue: 13_800_000, startDate: '2026-12-01', expectedCompletion: '2027-08-31', status: 'Planning', createdAt: '2026-10-22',
  },
]

// ---------------- payment schedules, billing, receipts, write-offs ----------------
const installments: Installment[] = []
const clientPayments: ClientPayment[] = []
let cpN = 0
// one schedule line; billedOn when it has been billed, then the receipts against it
function I(projectId: string, n: number, description: string, dueDate: string, amount: number, billedOn?: string, ...paid: [string, number, string, string?][]) {
  const id = `${projectId}-i${n}`
  installments.push({ id, projectId, description, dueDate, amount, billed: !!billedOn, billedOn })
  for (const [date, amt, method, reference] of paid) clientPayments.push({ id: `cp${++cpN}`, installmentId: id, date, amount: amt, method, reference })
}
I('p1', 1, 'Advance (20%)', '2025-12-08', 2_100_000, '2025-12-08', ['2025-12-09', 2_100_000, 'Bank Transfer', 'BOC TT 410552'])
I('p1', 2, 'Foundation complete (25%)', '2026-02-10', 2_625_000, '2026-02-12', ['2026-02-20', 2_625_000, 'Cheque', 'Chq 330118'])
I('p1', 3, 'Walls and roof (30%)', '2026-04-25', 3_150_000, '2026-04-27', ['2026-05-05', 1_500_000, 'Bank Transfer', 'BOC TT 438871'], ['2026-05-22', 1_650_000, 'Bank Transfer', 'BOC TT 441090'])
I('p1', 4, 'Completion and handover (25%)', '2026-07-14', 2_625_000, '2026-07-15', ['2026-07-30', 1_500_000, 'Bank Transfer', 'BOC TT 452317'], ['2026-09-10', 600_000, 'Cash', 'Receipt 0611'])
I('p2', 1, 'Design advance (40%)', '2025-12-15', 224_000, '2025-12-15', ['2025-12-15', 224_000, 'Cash', 'Receipt 0402'])
I('p2', 2, 'Plans approved by client (30%)', '2026-01-20', 168_000, '2026-01-20', ['2026-01-24', 168_000, 'Bank Transfer', 'HNB 51190'])
I('p2', 3, 'Council submission set (30%)', '2026-02-12', 168_000, '2026-02-12', ['2026-02-18', 168_000, 'Bank Transfer', 'HNB 51744'])
I('p3', 1, 'Design advance (50%)', '2025-12-22', 210_000, '2025-12-22', ['2025-12-23', 210_000, 'Online Payment', 'LankaPay 70031'])
I('p3', 2, 'Design fee balance (50%)', '2026-01-28', 210_000, '2026-01-28', ['2026-02-02', 210_000, 'Cash', 'Receipt 0431'])
I('p4', 1, 'Advance (15%)', '2026-01-12', 2_970_000, '2026-01-12', ['2026-01-14', 2_970_000, 'Bank Transfer', 'PB 300918'])
I('p4', 2, 'Foundation complete (20%)', '2026-03-15', 3_960_000, '2026-03-18', ['2026-03-30', 3_960_000, 'Bank Transfer', 'PB 302261'])
I('p4', 3, 'Ground-floor roof slab (20%)', '2026-06-10', 3_960_000, '2026-06-12', ['2026-06-30', 3_960_000, 'Bank Transfer', 'PB 305507'])
I('p4', 4, 'Upper-floor slab (20%)', '2026-09-05', 3_960_000, '2026-09-08', ['2026-10-02', 2_000_000, 'Bank Transfer', 'PB 309114'])
I('p4', 5, 'Completion and handover (25%)', '2026-11-30', 4_950_000)
I('p5', 1, 'Advance (25%)', '2026-02-16', 3_100_000, '2026-02-16', ['2026-02-20', 3_100_000, 'Cheque', 'Chq 118204'])
I('p5', 2, 'Foundation complete (25%)', '2026-04-30', 3_100_000, '2026-05-04', ['2026-05-25', 3_100_000, 'Cheque', 'Chq 118266'])
I('p5', 3, 'Structure and roof (30%)', '2026-08-31', 3_720_000, '2026-09-01', ['2026-09-12', 1_000_000, 'Cheque', 'Chq 118310'])
I('p5', 4, 'Completion (20%)', '2026-12-31', 2_480_000)
I('p6', 1, 'Advance (20%)', '2026-03-02', 3_120_000, '2026-03-02', ['2026-03-04', 3_120_000, 'Bank Transfer', 'COM 77812'])
I('p6', 2, 'Foundation complete (20%)', '2026-05-15', 3_120_000, '2026-05-18', ['2026-05-29', 3_120_000, 'Bank Transfer', 'COM 79140'])
I('p6', 3, 'Ground-floor slab (25%)', '2026-08-20', 3_900_000, '2026-08-24', ['2026-09-07', 3_900_000, 'Bank Transfer', 'COM 82205'])
// planned date passed and not billed yet: shows as "billing late"
I('p6', 4, 'Upper floor and roof (20%)', '2026-10-25', 3_120_000)
I('p6', 5, 'Completion and handover (15%)', '2026-12-31', 2_340_000)
I('p7', 1, 'Advance (30%)', '2026-04-06', 1_260_000, '2026-04-06', ['2026-04-07', 1_260_000, 'Bank Transfer', 'SAMP 11902'])
I('p7', 2, 'Kitchen, bathroom and roof done (40%)', '2026-06-05', 1_680_000, '2026-06-05', ['2026-06-15', 1_680_000, 'Bank Transfer', 'SAMP 12417'])
I('p7', 3, 'Completion (30%)', '2026-07-29', 1_260_000, '2026-07-30', ['2026-08-10', 1_260_000, 'Cheque', 'Chq 772031'])
I('p8', 1, 'Design advance (50%)', '2026-05-04', 175_000, '2026-05-04', ['2026-05-05', 175_000, 'Cash', 'Receipt 0517'])
I('p8', 2, 'Design fee balance (50%)', '2026-06-24', 175_000, '2026-06-24', ['2026-07-20', 100_000, 'Cash', 'Receipt 0569'])
I('p9', 1, 'Advance (25%)', '2026-06-01', 2_225_000, '2026-06-01', ['2026-06-03', 2_225_000, 'Bank Transfer', 'HNB 60120'])
I('p9', 2, 'Foundation and columns (25%)', '2026-08-15', 2_225_000, '2026-08-17', ['2026-09-01', 2_225_000, 'Bank Transfer', 'HNB 61877'])
I('p9', 3, 'Roof complete (30%)', '2026-10-30', 2_670_000, '2026-10-30')
I('p9', 4, 'Completion (20%)', '2026-12-15', 1_780_000)
I('p10', 1, 'Site inspection report', '2026-08-14', 75_000, '2026-08-14', ['2026-08-18', 75_000, 'Cheque', 'Chq 540102'])
I('p10', 2, 'Final structural report', '2026-08-28', 75_000, '2026-08-28', ['2026-09-15', 75_000, 'Cheque', 'Chq 540131'])
I('p11', 1, 'Design advance (50%)', '2026-10-01', 190_000, '2026-10-01', ['2026-10-02', 190_000, 'Online Payment', 'LankaPay 88120'])
I('p11', 2, 'Design fee balance (50%)', '2026-11-20', 190_000)
I('p12', 1, 'Site visit and levels (50%)', '2026-10-19', 30_000, '2026-10-19', ['2026-10-19', 30_000, 'Cash', 'Receipt 0702'])
I('p12', 2, 'Buildability note (50%)', '2026-11-10', 30_000)
I('p13', 1, 'Advance (20%)', '2026-12-01', 2_760_000)
I('p13', 2, 'Foundation complete (20%)', '2027-02-15', 2_760_000)
I('p13', 3, 'Structure and roof (30%)', '2027-05-31', 4_140_000)
I('p13', 4, 'Completion and handover (30%)', '2027-08-31', 4_140_000)

const writeOffs: WriteOff[] = [
  { id: 'wo1', installmentId: 'p8-i2', date: '2026-10-15', amount: 75_000, reason: 'Client disputed the extra revision rounds; owner agreed to waive the balance.' },
]

const otherIncome: OtherIncome[] = [
  { id: 'oi1', date: '2026-02-24', description: 'Sale of scrap steel and formwork offcuts', payer: 'Rajarata Scrap Collectors', amount: 38_000, reference: 'Receipt 0446' },
  { id: 'oi2', date: '2026-06-18', description: 'Concrete mixer hire to a neighbouring contractor (5 days)', payer: 'W. P. Construction', amount: 30_000, dueDate: '2026-06-30', reference: 'Inv OI-002' },
  { id: 'oi3', date: '2026-08-21', description: 'Sale of scrap steel and empty cement bags', payer: 'Rajarata Scrap Collectors', amount: 26_500, reference: 'Receipt 0598' },
  { id: 'oi4', date: '2026-10-12', description: 'Scaffolding hire to Mihintale contractor (3 weeks)', payer: 'S. M. Builders', amount: 54_000, dueDate: '2026-10-31', reference: 'Inv OI-004' },
]
const otherIncomeReceipts: OtherIncomeReceipt[] = [
  { id: 'oir1', incomeId: 'oi1', date: '2026-02-24', amount: 38_000, method: 'Cash' },
  { id: 'oir2', incomeId: 'oi2', date: '2026-06-29', amount: 30_000, method: 'Cash' },
  { id: 'oir3', incomeId: 'oi3', date: '2026-08-21', amount: 26_500, method: 'Cash' },
  { id: 'oir4', incomeId: 'oi4', date: '2026-10-20', amount: 20_000, method: 'Cash' },
]

// ---------------- suppliers, materials, invoices and stock ----------------
const suppliers: Supplier[] = [
  { id: 's1', name: 'Rajarata Hardware & Steel', contactPerson: 'Mahinda Rajapaksha', phone: '025 222 8814', email: 'sales@rajaratahardware.lk', address: 'Main Street, Anuradhapura', supplies: 'Cement, steel, binding wire, paint', creditDays: 14 },
  { id: 's2', name: 'Malwathu Oya Sand Suppliers', contactPerson: 'Priyantha Silva', phone: '077 902 4410', address: 'Malwathu Oya Road, Anuradhapura', supplies: 'River sand, delivered to site', creditDays: 14 },
  { id: 's3', name: 'New Town Block Works', contactPerson: 'Gamini Abeykoon', phone: '025 223 1097', address: 'Puttalam Road, Anuradhapura', supplies: 'Cement blocks, clay bricks', creditDays: 14 },
  { id: 's4', name: 'Mihintale Metal Crushers', contactPerson: 'Nuwan Ekanayake', phone: '071 330 8812', address: 'Quarry Road, Mihintale', supplies: 'Metal / aggregate, delivered to site', creditDays: 14 },
  { id: 's5', name: 'Nuwara Timber Depot', contactPerson: 'Rohana Weerasinghe', phone: '025 222 6630', address: 'Harischandra Mawatha, Anuradhapura', supplies: 'Roofing and formwork timber', creditDays: 21 },
  { id: 's6', name: 'Kala Wewa Tiles & Sanitary', contactPerson: 'Shiromi Perera', phone: '025 225 7781', email: 'orders@kalawewatiles.lk', address: 'Kurunegala Road, Anuradhapura', supplies: 'Tiles, sanitaryware, PVC pipes', creditDays: 30 },
  { id: 's7', name: 'Anuradhapura Electricals', contactPerson: 'Kasun Jayalath', phone: '025 222 9031', address: 'Freeman Mawatha, Anuradhapura', supplies: 'Wiring, switches, DB boards', creditDays: 30 },
]

const materials: Material[] = [
  { id: 'm1', sku: 'CEM-50', name: 'Cement (50 kg bag)', category: 'Cement', unit: 'bag', unitCost: 2450 },
  { id: 'm2', sku: 'STL-12', name: 'Steel bar 12 mm (tor)', category: 'Steel', unit: 'kg', unitCost: 315 },
  { id: 'm3', sku: 'STL-10', name: 'Steel bar 10 mm (tor)', category: 'Steel', unit: 'kg', unitCost: 310 },
  { id: 'm4', sku: 'WIR-BD', name: 'Binding wire', category: 'Steel', unit: 'kg', unitCost: 490 },
  { id: 'm5', sku: 'BLK-06', name: 'Cement block 6"', category: 'Blocks & Bricks', unit: 'nos', unitCost: 95 },
  { id: 'm6', sku: 'BRK-ST', name: 'Clay brick (standard)', category: 'Blocks & Bricks', unit: 'nos', unitCost: 33 },
  { id: 'm7', sku: 'PVC-04', name: 'PVC pipe 4" (6 m length)', category: 'Plumbing', unit: 'length', unitCost: 2950 },
  { id: 'm8', sku: 'ELW-25', name: 'Electrical wire 2.5 mm (100 m roll)', category: 'Electrical', unit: 'nos', unitCost: 18_500 },
  { id: 'm9', sku: 'PNT-EM', name: 'Emulsion paint (white)', category: 'Paint', unit: 'litre', unitCost: 1_450 },
]
const mat = (id: string) => materials.find((m) => m.id === id)!

// prices drift up through the year
function price(materialId: string, d: string): number {
  const step = d < '2026-03-01' ? 0 : d < '2026-07-01' ? 1 : 2
  const table: Record<string, number[]> = {
    m1: [2350, 2400, 2450], m2: [295, 305, 315], m3: [290, 300, 310], m4: [470, 480, 490], m5: [92, 94, 95],
    m6: [32, 32, 33], m7: [2850, 2900, 2950], m8: [17_800, 18_200, 18_500], m9: [1_380, 1_420, 1_450],
  }
  return table[materialId][step]
}
// who sells what we keep in stock, and how much one order brings in
const STOCK_SOURCE: Record<string, { supplier: string; lot: number }> = {
  m1: { supplier: 's1', lot: 200 }, m2: { supplier: 's1', lot: 1500 }, m3: { supplier: 's1', lot: 500 }, m4: { supplier: 's1', lot: 25 },
  m5: { supplier: 's3', lot: 4000 }, m6: { supplier: 's3', lot: 4000 }, m7: { supplier: 's6', lot: 20 }, m8: { supplier: 's7', lot: 10 },
  m9: { supplier: 's1', lot: 100 },
}
const PREFIX: Record<string, string> = { s1: 'RHS', s2: 'MOS', s3: 'NTB', s4: 'MMC', s5: 'NTD', s6: 'KWT', s7: 'AEL' }
const invNo: Record<string, number> = { s1: 4100, s2: 410, s3: 1020, s4: 2230, s5: 110, s6: 90, s7: 300 }

const supplierInvoices: SupplierInvoice[] = []
const stockMovements: StockMovement[] = []
let siN = 0
let smN = 0
let ginN = 0
function invoice(supplierId: string, date: string, lines: { description: string; qty: number; unit: string; unitPrice: number; materialId?: string }[], projectId?: string, notes?: string) {
  const sp = suppliers.find((s) => s.id === supplierId)!
  const no = `${PREFIX[supplierId]}-${++invNo[supplierId]}`
  const inv: SupplierInvoice = {
    id: `si${++siN}`, supplierId, invoiceNo: no, date, dueDate: addDays(date, sp.creditDays), projectId, notes,
    lines: lines.map((l, i) => ({ id: `l${i + 1}`, ...l })), attachment: { fileName: `${no}.pdf`, size: 160_000 + siN * 3_100 },
  }
  supplierInvoices.push(inv)
  if (!projectId) for (const l of inv.lines) if (l.materialId) stockMovements.push({ id: `sm${++smN}`, materialId: l.materialId, date, type: 'Received', qty: l.qty, unitCost: l.unitPrice, invoiceId: inv.id, reference: no })
  return inv
}

// what each kind of work draws from the store per issue (twice a month)
const DRAW: Record<string, Record<string, number>> = {
  foundation: { m1: 35, m2: 250, m4: 4, m5: 450 },
  structure: { m1: 45, m2: 350, m3: 120, m4: 6, m5: 750 },
  walls: { m1: 30, m5: 1100, m6: 800 },
  slab: { m1: 65, m2: 420, m3: 150, m4: 8 },
  finishing: { m1: 15, m7: 3, m8: 1, m9: 25 },
  renovation: { m1: 20, m6: 700, m7: 3, m8: 2, m9: 35 },
}
// the work on each site, phase by phase (inclusive ranges)
const PHASES: Record<string, [string, string, string][]> = {
  p1: [['2025-12-10', '2026-01-31', 'foundation'], ['2026-02-01', '2026-03-15', 'structure'], ['2026-03-16', '2026-04-30', 'walls'], ['2026-05-01', '2026-05-31', 'slab'], ['2026-06-01', '2026-07-10', 'finishing']],
  p4: [['2026-01-15', '2026-03-10', 'foundation'], ['2026-03-11', '2026-06-05', 'structure'], ['2026-06-06', '2026-07-31', 'walls'], ['2026-08-01', '2026-09-05', 'slab'], ['2026-09-06', '2026-10-15', 'walls'], ['2026-10-16', END, 'finishing']],
  p5: [['2026-02-18', '2026-04-25', 'foundation'], ['2026-04-26', '2026-07-15', 'structure'], ['2026-07-16', '2026-09-12', 'slab']],
  p6: [['2026-03-04', '2026-05-12', 'foundation'], ['2026-05-13', '2026-08-18', 'structure'], ['2026-08-19', '2026-10-10', 'walls'], ['2026-10-11', END, 'slab']],
  p7: [['2026-04-07', '2026-07-25', 'renovation']],
  p9: [['2026-06-03', '2026-08-12', 'foundation'], ['2026-08-13', '2026-10-25', 'structure'], ['2026-10-26', END, 'slab']],
}
const phaseOn = (projectId: string, d: string) => PHASES[projectId].find(([from, to]) => d >= from && d <= to)?.[2]

// site deliveries billed straight to the project (not stock)
type SiteBuy = { supplier: string; description: string; unit: string; qty: number; price: number }
const SITE: Record<string, SiteBuy[]> = {
  foundation: [{ supplier: 's2', description: 'River sand', unit: 'cube', qty: 5, price: 18_500 }, { supplier: 's4', description: 'Metal 3/4"', unit: 'cube', qty: 4, price: 16_000 }],
  structure: [{ supplier: 's2', description: 'River sand', unit: 'cube', qty: 4, price: 18_500 }, { supplier: 's4', description: 'Metal 3/4"', unit: 'cube', qty: 4, price: 16_000 }, { supplier: 's5', description: 'Formwork timber', unit: 'cft', qty: 35, price: 2_300 }],
  walls: [{ supplier: 's2', description: 'River sand', unit: 'cube', qty: 4, price: 18_500 }],
  slab: [{ supplier: 's2', description: 'River sand', unit: 'cube', qty: 4, price: 18_500 }, { supplier: 's4', description: 'Metal 3/4"', unit: 'cube', qty: 5, price: 16_000 }, { supplier: 's5', description: 'Formwork and roofing timber', unit: 'cft', qty: 70, price: 2_400 }],
  finishing: [{ supplier: 's6', description: 'Floor tiles 2x2 (box)', unit: 'box', qty: 45, price: 3_850 }, { supplier: 's2', description: 'Plaster sand', unit: 'cube', qty: 2, price: 19_000 }],
  renovation: [{ supplier: 's6', description: 'Wall and floor tiles (box)', unit: 'box', qty: 30, price: 3_900 }, { supplier: 's6', description: 'Sanitaryware set (WC, basin, fittings)', unit: 'nos', qty: 2, price: 68_000 }],
}

{
  const onHand: Record<string, number> = Object.fromEntries(materials.map((m) => [m.id, 0]))
  // walk the year day by day: site deliveries at the start of each month, store issues on the 6th and 20th
  for (const d of eachDay('2025-12-01', END)) {
    const day = Number(d.slice(8))
    for (const projectId of Object.keys(PHASES)) {
      const phase = phaseOn(projectId, d)
      if (!phase) continue
      if (day === 3 || (d === PHASES[projectId][0][0] && day !== 3)) {
        const buys = SITE[phase]
        for (const supplierId of [...new Set(buys.map((b) => b.supplier))]) {
          invoice(supplierId, workday(d), buys.filter((b) => b.supplier === supplierId).map((b) => ({
            description: b.description, qty: Math.round(b.qty * (0.8 + rand() * 0.4)), unit: b.unit, unitPrice: b.price + (d >= '2026-07-01' ? 500 : 0),
          })), projectId)
        }
      }
      if (day === 6 || day === 20) {
        const on = workday(d)
        const ref = `GIN-${String(++ginN).padStart(3, '0')}`
        for (const [materialId, base] of Object.entries(DRAW[phase])) {
          const qty = materialId === 'm2' || materialId === 'm3' || materialId === 'm5' || materialId === 'm6'
            ? Math.round((base * (0.85 + rand() * 0.3)) / 50) * 50 : Math.max(1, Math.round(base * (0.85 + rand() * 0.3)))
          // buy before running out: an order two days before the issue
          if (onHand[materialId] < qty) {
            const { supplier, lot } = STOCK_SOURCE[materialId]
            const buyOn = workday(addDays(on, -2))
            const size = Math.max(lot, qty)
            invoice(supplier, buyOn, [{ description: mat(materialId).name, qty: size, unit: mat(materialId).unit, unitPrice: price(materialId, buyOn), materialId }])
            onHand[materialId] += size
          }
          onHand[materialId] -= qty
          stockMovements.push({ id: `sm${++smN}`, materialId, date: on, type: 'Issued', qty: -qty, unitCost: price(materialId, on), projectId, reference: ref })
        }
      }
    }
  }
}
stockMovements.push({ id: `sm${++smN}`, materialId: 'm1', date: '2026-06-02', type: 'Adjustment', qty: -6, unitCost: 2400, reason: 'Bags hardened after rain got into the store' })
stockMovements.push({ id: `sm${++smN}`, materialId: 'm5', date: '2026-09-30', type: 'Adjustment', qty: -40, unitCost: 95, reason: 'Quarterly count: 40 blocks broken in handling' })

const invoiceTotal = (inv: SupplierInvoice) => inv.lines.reduce((t, l) => t + l.qty * l.unitPrice, 0)
const supplierPayments: SupplierPayment[] = []
let spN = 0
for (const inv of supplierInvoices) {
  const total = invoiceTotal(inv)
  const method = total > 150_000 ? 'Bank Transfer' : total > 60_000 ? 'Cheque' : 'Cash'
  const ref = method === 'Bank Transfer' ? `TT ${60_000 + spN * 37}` : method === 'Cheque' ? `Chq ${210_000 + spN * 11}` : undefined
  // older bills are settled a day before they fall due; the last few weeks are partly open
  if (inv.dueDate < '2026-10-20') supplierPayments.push({ id: `sp${++spN}`, invoiceId: inv.id, date: addDays(inv.dueDate, -1), amount: total, method, reference: ref })
  else if (inv.date < '2026-10-12' && rand() < 0.5) supplierPayments.push({ id: `sp${++spN}`, invoiceId: inv.id, date: addDays(inv.date, 7), amount: Math.round(total / 2), method, reference: ref })
}

// ---------------- expenses ----------------
const expenses: Expense[] = []
const expensePayments: ExpensePayment[] = []
let exN = 0
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
const machine = (m: string, period: string, rate: string) => ({ details: { Machine: m, 'Usage period': period, Rate: rate } })

// project start-up and one-off costs
EX(PE('p1', 'Construction', 'Other Construction Expense', '2025-12-03', 'Building plan approval fee - Municipal Council', 'Anuradhapura Municipal Council', 42_000), 'full')
EX(PE('p1', 'Construction', 'Engineer / Survey Visit', '2025-12-05', 'Land survey and setting-out', 'Sumanadasa & Sons Surveyors', 32_000), 'full')
EX(PE('p1', 'Construction', 'Machinery Rental', '2025-12-09', 'Excavator - foundation excavation, 2 days', 'Ruwanpura Machinery Hire', 80_000, machine('JCB 3CX backhoe', '09-10 Dec 2025', 'LKR 40,000 / day')), 'full', 'Bank Transfer')
EX(PE('p1', 'Construction', 'Engineer / Survey Visit', '2025-12-29', 'Structural engineer - footing inspection', 'Eng. R. M. Dissanayake', 22_000), 'full')
EX(PE('p1', 'Construction', 'Vehicle Hire', '2026-05-12', 'Crane truck - roof slab formwork lift', 'Senarath Transport', 45_000), 'full')
EX(PE('p1', 'Construction', 'Vehicle Hire', '2026-06-20', 'Lorry hire - tiles and timber transport', 'Senarath Transport', 28_000), 'full')
EX(PE('p4', 'Construction', 'Engineer / Survey Visit', '2026-01-08', 'Survey, soil test and setting-out', 'Sumanadasa & Sons Surveyors', 55_000), 'full', 'Bank Transfer')
EX(PE('p4', 'Construction', 'Machinery Rental', '2026-01-13', 'Excavator - foundation and septic pit, 3 days', 'Ruwanpura Machinery Hire', 126_000, machine('JCB 3CX backhoe', '13-15 Jan 2026', 'LKR 42,000 / day')), 'full', 'Bank Transfer')
EX(PE('p4', 'Construction', 'Machinery Rental', '2026-03-02', 'Concrete mixer and poker vibrator - Mar to Oct', 'Nuwara Equipment Rentals', 256_000, { dueDate: '2026-10-31', ...machine('Concrete mixer + poker vibrator', '01 Mar - 31 Oct 2026', 'LKR 32,000 / month') }), 160_000, 'Cash', '2026-07-01')
EX(PE('p4', 'Construction', 'Engineer / Survey Visit', '2026-06-08', 'Structural engineer - slab reinforcement check', 'Eng. R. M. Dissanayake', 25_000), 'full')
EX(PE('p4', 'Construction', 'Vehicle Hire', '2026-08-29', 'Concrete pump - upper-floor slab pour', 'Rajarata Concrete Pumping', 85_000), 'full', 'Bank Transfer')
EX(PE('p4', 'Construction', 'Engineer / Survey Visit', '2026-10-28', 'Engineer visit - terrace waterproofing', 'Eng. R. M. Dissanayake', 25_000, { dueDate: '2026-11-10' }), 'none')
EX(PE('p5', 'Construction', 'Other Construction Expense', '2026-02-10', 'Building plan approval fee - Pradeshiya Sabha', 'Talawa Pradeshiya Sabha', 35_000), 'full')
EX(PE('p5', 'Construction', 'Machinery Rental', '2026-02-17', 'Excavator - foundation, 2 days', 'Ruwanpura Machinery Hire', 84_000, machine('JCB 3CX backhoe', '17-18 Feb 2026', 'LKR 42,000 / day')), 'full', 'Bank Transfer')
EX(PE('p5', 'Construction', 'Vehicle Hire', '2026-08-18', 'Crane truck - roof trusses', 'Senarath Transport', 52_000), 'full')
EX(PE('p6', 'Construction', 'Engineer / Survey Visit', '2026-02-26', 'Survey and setting-out', 'Sumanadasa & Sons Surveyors', 38_000), 'full')
EX(PE('p6', 'Construction', 'Machinery Rental', '2026-03-03', 'Excavator and roller - foundation, 3 days', 'Ruwanpura Machinery Hire', 132_000, machine('Excavator + vibratory roller', '03-05 Mar 2026', 'LKR 44,000 / day')), 'full', 'Bank Transfer')
EX(PE('p6', 'Construction', 'Vehicle Hire', '2026-08-22', 'Concrete pump - ground-floor slab', 'Rajarata Concrete Pumping', 80_000), 'full', 'Bank Transfer')
EX(PE('p6', 'Construction', 'Machinery Rental', '2026-10-14', 'Scaffolding hire - upper floor, Oct to Nov', 'Nuwara Equipment Rentals', 48_000, { dueDate: '2026-11-15', ...machine('Steel scaffolding (40 frames)', '14 Oct - 14 Nov 2026', 'LKR 48,000 / month') }), 'none')
EX(PE('p7', 'Construction', 'Other Construction Expense', '2026-04-08', 'Debris removal - old kitchen and bathroom', 'Kumara Tractor Service', 18_000), 'full')
EX(PE('p7', 'Construction', 'Vehicle Hire', '2026-05-20', 'Lorry hire - roofing sheets and sanitaryware', 'Senarath Transport', 16_000), 'full')
EX(PE('p9', 'Construction', 'Engineer / Survey Visit', '2026-05-28', 'Engineer - existing structure and new footings', 'Eng. R. M. Dissanayake', 30_000), 'full')
EX(PE('p9', 'Construction', 'Machinery Rental', '2026-06-02', 'Excavator - foundation, 2 days', 'Ruwanpura Machinery Hire', 88_000, machine('JCB 3CX backhoe', '02-03 Jun 2026', 'LKR 44,000 / day')), 'full', 'Bank Transfer')
EX(PE('p9', 'Construction', 'Vehicle Hire', '2026-10-27', 'Crane truck - roof sheets and trusses', 'Senarath Transport', 46_000, { dueDate: '2026-11-05' }), 'none')
// running site costs: water bowser monthly, CEB temporary connection every two months
for (const projectId of ['p1', 'p4', 'p5', 'p6', 'p7', 'p9']) {
  const first = PHASES[projectId][0][0]
  const last = PHASES[projectId].at(-1)![1]
  let n = 0
  for (const day of eachDay(first, last < END ? last : END).filter((x) => x.endsWith('-25'))) {
    const on = workday(day)
    EX(PE(projectId, 'Construction', 'Water', on, 'Water bowser - curing and mixing', 'Lanka Water Bowser Services', 6_000 + Math.round(rand() * 6) * 1_000), 'full')
    // the CEB bill comes every second month; the latest one isn't paid yet
    if (++n % 2 === 0) {
      const open = on >= '2026-10-20'
      EX(PE(projectId, 'Construction', 'Electricity', on, 'Temporary site connection - CEB bill (2 months)', 'Ceylon Electricity Board', 8_000 + Math.round(rand() * 8) * 1_000, open ? { dueDate: '2026-11-15' } : {}), open ? 'none' : 'full')
    }
  }
}
// design and consultation work
EX(PE('p2', 'Design', 'Printing', '2026-01-19', 'A1 plan prints and 3D colour prints - client set', 'Anu Printers', 6_400), 'full')
EX(PE('p2', 'Design', 'Printing', '2026-02-10', 'Council submission set - 4 copies', 'Anu Printers', 7_200), 'full')
EX(PE('p2', 'Design', 'Electricity', '2026-01-31', 'Design studio electricity - rendering share (Dec-Jan)', 'Ceylon Electricity Board', 3_800, { notes: 'Design-work electricity, kept separate from site and office electricity.' }), 'full')
EX(PE('p3', 'Design', 'Printing', '2026-01-26', 'Final plan set and 3D prints', 'Anu Printers', 5_100), 'full')
EX(PE('p8', 'Design', 'Printing', '2026-06-22', 'Plan prints - three revisions', 'Anu Printers', 6_900), 'full')
EX(PE('p8', 'Design', 'Electricity', '2026-06-30', 'Design studio electricity - rendering share (May-Jun)', 'Ceylon Electricity Board', 3_200), 'full')
EX(PE('p11', 'Design', 'Printing', '2026-10-22', 'Concept presentation prints', 'Anu Printers', 3_900), 'full')
EX(PE('p10', 'Consultation', 'Transport', '2026-08-26', 'Van hire and fuel - two site visits to Kekirawa', 'Self (fuel bills)', 9_600), 'full')
EX(PE('p12', 'Consultation', 'Transport', '2026-10-19', 'Fuel - site visit to Horowpothana', 'Self (fuel bills)', 4_800), 'full')
// office: rent, power, phone, bookkeeping, marketing
const MONTHS = ['2025-12', '2026-01', '2026-02', '2026-03', '2026-04', '2026-05', '2026-06', '2026-07', '2026-08', '2026-09', '2026-10']
const monthName = (m: string) => new Date(`${m}-01T00:00:00`).toLocaleString('en-GB', { month: 'long', year: 'numeric' })
MONTHS.forEach((m, i) => {
  EX(CE('General Company Expense', workday(`${m}-05`), `Office rent - ${monthName(m)}`, 'L. Gunawardena (landlord)', 35_000), 'full', 'Bank Transfer')
  EX(CE('General Company Expense', workday(`${m}-26`), 'Office electricity - CEB', 'Ceylon Electricity Board', 7_400 + i * 120), 'full')
  EX(CE('General Company Expense', workday(`${m}-18`), 'Office phone and internet', 'Sri Lanka Telecom', 6_490), 'full', 'Online Payment')
  EX(CE('General Company Expense', workday(`${m}-28`), 'Site van fuel', 'Lanka IOC - Anuradhapura', 26_000 + Math.round(rand() * 8) * 1_000), 'full')
  if (i % 2 === 0) EX(CE('Marketing', workday(`${m}-02`), `Facebook and Instagram adverts - ${monthName(m)}`, 'Meta Platforms', 20_000), 'full', 'Online Payment')
  if (i % 3 === 0) EX(CE('Other Expense', workday(`${m}-28`), 'Accountant - quarterly bookkeeping fee', 'K. Perera & Co. Chartered Accountants', 15_000, i === 9 ? { dueDate: '2026-10-31' } : {}), i === 9 ? 'none' : 'full', 'Bank Transfer')
})
EX(CE('General Company Expense', '2026-11-02', 'Office rent - November 2026', 'L. Gunawardena (landlord)', 35_000), 'full', 'Bank Transfer')
EX(CE('Cutout and Banner', '2026-01-16', 'Roadside cutout - Anuradhapura New Town junction', 'Rajarata Sign Arts', 48_000), 'full')
EX(CE('Cutout and Banner', '2026-03-09', 'Site banners (3) - Nuwarawewa, Talawa, Kekirawa', 'Rajarata Sign Arts', 13_500), 'full')
EX(CE('Cutout and Banner', '2026-06-04', 'Site banner - Thambuttegama clinic', 'Rajarata Sign Arts', 4_500), 'full')
EX(CE('Marketing', '2026-04-22', 'Stall at the Anuradhapura Home & Build exhibition', 'Rajarata Chamber of Commerce', 30_000), 'full', 'Bank Transfer')

// ---------------- labour ----------------
const employees: Employee[] = [
  { id: 'w1', name: 'Sunil Perera', role: 'Mason', phone: '071 223 4401', dailyRate: 4500, overtimeRate: 850, status: 'Active', joinedOn: '2025-12-01' },
  { id: 'w2', name: 'Ajith Kumara', role: 'Mason', phone: '077 640 1189', dailyRate: 4500, overtimeRate: 850, status: 'Active', joinedOn: '2025-12-01' },
  { id: 'w3', name: 'Nimal Dissanayake', role: 'Carpenter', phone: '070 318 2290', dailyRate: 4200, overtimeRate: 800, status: 'Active', joinedOn: '2025-12-01' },
  { id: 'w4', name: 'Kamal Bandara', role: 'Labourer', phone: '076 552 0913', dailyRate: 3200, overtimeRate: 600, status: 'Active', joinedOn: '2025-12-01' },
  { id: 'w5', name: 'Ruwan Priyankara', role: 'Labourer', phone: '071 908 4472', dailyRate: 3200, overtimeRate: 600, status: 'Active', joinedOn: '2025-12-01' },
  { id: 'w6', name: 'Chathura Madushanka', role: 'Labourer', phone: '077 115 6630', dailyRate: 3000, overtimeRate: 560, status: 'Active', joinedOn: '2025-12-01' },
  { id: 'w7', name: 'Lalith Wijeratne', role: 'Bar bender', phone: '072 664 2018', dailyRate: 4000, overtimeRate: 750, status: 'Active', joinedOn: '2025-12-01' },
  { id: 'w8', name: 'Pradeep Jayasinghe', role: 'Site supervisor', phone: '071 774 5520', dailyRate: 5500, overtimeRate: 1000, status: 'Active', joinedOn: '2025-12-01' },
  { id: 'w9', name: 'Upali Herath', role: 'Labourer', phone: '070 882 1047', dailyRate: 3000, overtimeRate: 560, status: 'Inactive', joinedOn: '2025-12-01' },
  { id: 'w10', name: 'Rohana Silva', role: 'Mason', phone: '075 220 6193', dailyRate: 4500, overtimeRate: 850, status: 'Active', joinedOn: '2026-02-16' },
  { id: 'w11', name: 'Saman Kumara', role: 'Electrician', phone: '075 330 9184', dailyRate: 4800, overtimeRate: 900, status: 'Active', joinedOn: '2026-03-02' },
  { id: 'w12', name: 'Dinesh Lakmal', role: 'Plumber', phone: '077 409 3361', dailyRate: 4600, overtimeRate: 860, status: 'Active', joinedOn: '2026-04-20' },
  { id: 'w13', name: 'Gayan Sampath', role: 'Mason', phone: '071 993 2075', dailyRate: 4500, overtimeRate: 850, status: 'Active', joinedOn: '2026-06-01' },
  { id: 'w14', name: 'Tharindu Madushan', role: 'Labourer', phone: '076 118 4520', dailyRate: 3000, overtimeRate: 560, status: 'Active', joinedOn: '2026-06-08' },
]

// where each worker was on a given day (undefined: not working for us that day; '' = yard work)
function assignment(empId: string, d: string): string | undefined {
  switch (empId) {
    case 'w1': return d < '2025-12-08' ? undefined : d <= '2026-07-14' ? 'p1' : 'p6'
    case 'w2': return d < '2025-12-08' ? undefined : d < '2026-01-12' ? 'p1' : 'p4'
    case 'w3': return d < '2025-12-08' ? undefined : d < '2026-02-16' ? 'p1' : d <= '2026-09-12' ? 'p5' : 'p4'
    case 'w4': return d < '2025-12-08' ? '' : d <= '2026-07-14' ? 'p1' : 'p9'
    case 'w5': return d < '2025-12-08' ? undefined : d < '2026-01-12' ? 'p1' : 'p4'
    case 'w6': return d < '2025-12-08' ? '' : d < '2026-03-02' ? 'p1' : 'p6'
    case 'w7': return d < '2025-12-08' ? undefined : d < '2026-01-12' ? 'p1' : d < '2026-03-02' ? 'p4' : d < '2026-06-01' ? 'p6' : 'p9'
    case 'w8': return d < '2025-12-08' ? '' : d < '2026-01-12' ? 'p1' : 'p4'
    case 'w9': return d > '2026-05-29' ? undefined : d < '2025-12-08' ? '' : d < '2026-02-16' ? 'p1' : 'p5'
    case 'w10': return d <= '2026-09-12' ? 'p5' : 'p9'
    case 'w11': return d < '2026-04-06' ? 'p1' : d <= '2026-07-29' ? 'p7' : d <= '2026-09-30' ? 'p4' : 'p9'
    case 'w12': return d <= '2026-07-14' ? 'p1' : d <= '2026-07-29' ? 'p7' : 'p4'
    case 'w13': return 'p9'
    case 'w14': return d <= '2026-09-12' ? 'p5' : 'p6'
  }
}
// Sunday slab pours: the crew on those sites came in
const SUNDAY_WORK: Record<string, string[]> = { '2026-06-07': ['p4'], '2026-08-30': ['p4'], '2026-08-23': ['p6'], '2026-05-17': ['p1'] }

const attendance: Attendance[] = []
for (const d of eachDay('2025-12-01', END)) {
  if (isHoliday(d)) continue
  const sunday = weekday(d) === 0
  if (sunday && !SUNDAY_WORK[d]) continue
  for (const e of employees) {
    if (d < e.joinedOn) continue
    const where = assignment(e.id, d)
    if (where === undefined) continue
    if (sunday && !SUNDAY_WORK[d].includes(where)) continue
    const r = rand()
    const status: AttendanceStatus = sunday ? 'Present' : r < 0.05 ? 'Absent' : r < 0.1 ? 'Half Day' : 'Present'
    attendance.push({ date: d, employeeId: e.id, status, projectId: where || undefined, notes: where === '' ? 'Store yard clean-up' : sunday ? 'Sunday slab pour' : undefined })
  }
}

let otN = 0
const overtime: Overtime[] = []
const OT = (date: string, employeeId: string, hours: number, approved: boolean, notes: string) => {
  const e = employees.find((x) => x.id === employeeId)!
  overtime.push({ id: `ot${++otN}`, date, employeeId, projectId: assignment(employeeId, date) || undefined, hours, rate: e.overtimeRate, approved, recordedBy: 'Pradeep Jayasinghe', notes })
}
// slab pours and deadline pushes ran late
for (const [date, workers, hours, note] of [
  ['2026-01-29', ['w1', 'w4', 'w6'], 2, 'Footing concrete ran late'],
  ['2026-03-13', ['w2', 'w5', 'w8'], 3, 'Ground beam concreting'],
  ['2026-05-16', ['w1', 'w4', 'w12'], 3, 'Roof slab preparation'],
  ['2026-06-06', ['w2', 'w5', 'w3'], 3, 'Ground-floor slab pour'],
  ['2026-07-13', ['w1', 'w4', 'w12'], 2, 'Handover snag list'],
  ['2026-08-22', ['w1', 'w6', 'w14'], 3, 'Ground-floor slab pour'],
  ['2026-08-29', ['w2', 'w5', 'w3', 'w8'], 4, 'Upper-floor slab pour'],
  ['2026-09-11', ['w3', 'w10'], 2, 'Securing the site before the break'],
  ['2026-10-09', ['w7', 'w13'], 2, 'Column reinforcement'],
  ['2026-10-28', ['w4', 'w13'], 3, 'Roof trusses'],
] as [string, string[], number, string][]) for (const w of workers) OT(date, w, hours, true, note)
OT('2026-11-02', 'w1', 2, false, 'Upper-floor formwork')
OT('2026-11-02', 'w6', 2, false, 'Upper-floor formwork')

const advances: Advance[] = [
  ['2025-12-20', 'w4', 5_000, 'Christmas'], ['2026-01-10', 'w6', 3_000, undefined], ['2026-02-14', 'w9', 4_000, 'Medical'],
  ['2026-03-21', 'w5', 5_000, undefined], ['2026-04-09', 'w1', 10_000, 'New Year'], ['2026-04-09', 'w2', 10_000, 'New Year'],
  ['2026-04-09', 'w4', 6_000, 'New Year'], ['2026-04-10', 'w7', 8_000, 'New Year'], ['2026-05-13', 'w6', 2_000, undefined],
  ['2026-06-17', 'w3', 4_000, 'School books'], ['2026-07-22', 'w14', 3_500, undefined], ['2026-08-12', 'w10', 6_000, 'Family function'],
  ['2026-09-16', 'w4', 4_000, 'Medical'], ['2026-10-14', 'w5', 3_000, undefined], ['2026-10-21', 'w13', 5_000, undefined],
  ['2026-11-02', 'w7', 6_000, 'School fees'],
].map(([date, employeeId, amount, notes], i) => ({
  id: `ad${i + 1}`, date: date as string, employeeId: employeeId as string, amount: amount as number, method: (amount as number) >= 10_000 ? 'Bank Transfer' : 'Cash', notes: notes as string | undefined,
}))

// ---------------- commissions ----------------
const commissions: CommissionArrangement[] = [
  { id: 'cm1', projectId: 'p1', recipient: 'Mahesh Gunasekara', role: 'Referral agent', rate: 3, status: 'Confirmed', confirmedOn: '2025-12-05' },
  { id: 'cm2', projectId: 'p2', recipient: 'Kavindi Ranasinghe', role: '3D designer', rate: 12, status: 'Confirmed', confirmedOn: '2025-12-15' },
  { id: 'cm3', projectId: 'p2', recipient: 'Ishara Fernando', role: 'Draughtsman', rate: 8, status: 'Confirmed', confirmedOn: '2025-12-15' },
  { id: 'cm4', projectId: 'p3', recipient: 'Kavindi Ranasinghe', role: '3D designer', rate: 12, status: 'Confirmed', confirmedOn: '2025-12-22' },
  { id: 'cm5', projectId: 'p3', recipient: 'Ishara Fernando', role: 'Draughtsman', rate: 8, status: 'Confirmed', confirmedOn: '2025-12-22' },
  { id: 'cm6', projectId: 'p4', recipient: 'Mahesh Gunasekara', role: 'Referral agent', rate: 2, status: 'Confirmed', confirmedOn: '2026-01-09' },
  { id: 'cm7', projectId: 'p4', recipient: 'Pradeep Jayasinghe', role: 'Site supervisor bonus', rate: 1, status: 'Confirmed', confirmedOn: '2026-01-09' },
  { id: 'cm8', projectId: 'p6', recipient: 'Harsha Wijesooriya', role: 'Referral agent', rate: 2.5, status: 'Confirmed', confirmedOn: '2026-02-27' },
  { id: 'cm9', projectId: 'p8', recipient: 'Kavindi Ranasinghe', role: '3D designer', rate: 12, status: 'Confirmed', confirmedOn: '2026-05-04' },
  { id: 'cm10', projectId: 'p8', recipient: 'Ishara Fernando', role: 'Draughtsman', rate: 8, status: 'Confirmed', confirmedOn: '2026-05-04' },
  { id: 'cm11', projectId: 'p9', recipient: 'Harsha Wijesooriya', role: 'Referral agent', rate: 2, status: 'Confirmed', confirmedOn: '2026-05-28' },
  { id: 'cm12', projectId: 'p11', recipient: 'Kavindi Ranasinghe', role: '3D designer', rate: 14, status: 'Pending', notes: 'Asked for a higher share because of the sloping site.' },
  { id: 'cm13', projectId: 'p11', recipient: 'Ishara Fernando', role: 'Draughtsman', rate: 10, status: 'Pending' },
]
const commissionPayments: CommissionPayment[] = [
  { id: 'cmp1', arrangementId: 'cm1', date: '2025-12-15', amount: 105_000, method: 'Bank Transfer' },
  { id: 'cmp2', arrangementId: 'cm1', date: '2026-05-30', amount: 105_000, method: 'Bank Transfer' },
  { id: 'cmp3', arrangementId: 'cm1', date: '2026-08-05', amount: 105_000, method: 'Bank Transfer' },
  { id: 'cmp4', arrangementId: 'cm2', date: '2026-02-20', amount: 67_200, method: 'Cash' },
  { id: 'cmp5', arrangementId: 'cm3', date: '2026-02-20', amount: 44_800, method: 'Cash' },
  { id: 'cmp6', arrangementId: 'cm4', date: '2026-02-05', amount: 50_400, method: 'Cash' },
  { id: 'cmp7', arrangementId: 'cm5', date: '2026-02-05', amount: 33_600, method: 'Cash' },
  { id: 'cmp8', arrangementId: 'cm6', date: '2026-01-20', amount: 198_000, method: 'Bank Transfer' },
  { id: 'cmp9', arrangementId: 'cm6', date: '2026-07-10', amount: 198_000, method: 'Bank Transfer' },
  { id: 'cmp10', arrangementId: 'cm8', date: '2026-03-10', amount: 195_000, method: 'Cheque', reference: 'Chq 220871' },
  { id: 'cmp11', arrangementId: 'cm9', date: '2026-07-25', amount: 33_000, method: 'Cash' },
  { id: 'cmp12', arrangementId: 'cm10', date: '2026-07-25', amount: 22_000, method: 'Cash' },
  { id: 'cmp13', arrangementId: 'cm11', date: '2026-06-10', amount: 89_000, method: 'Bank Transfer' },
]

export function createBusinessData(): DataState {
  return {
    clients, projects, installments, clientPayments, writeOffs, otherIncome, otherIncomeReceipts, expenses, expensePayments,
    suppliers, supplierInvoices, supplierPayments, materials, stockMovements, employees, attendance, overtime, advances,
    payrollAdjustments: [], payrollPayments: [], commissions, commissionPayments, activity: [], settings,
  }
}

// weekly wages: every week from the first to the last listed is paid in full on payday, except these
// part-payments in the last paid week (the rest is still owed)
export const payrollPlan = {
  firstWeek: '2025-12-01',
  lastWeek: '2026-10-26',
  short: new Map([['w5', 5_000], ['w6', 5_000]]),
}
