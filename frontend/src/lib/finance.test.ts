import { describe, expect, it } from 'vitest'
import { createSeed } from '@/data/seed'
import type { DataState } from '@/data/types'
import { addDays, TODAY, weekStart } from './dates'
import { billingLate, derive, projectActivity, projectFigures, scheduleStatus } from './finance'

// the seed with every money record cleared, so a test only sees what it adds
function blank(): DataState {
  const s = createSeed()
  return {
    ...s, installments: [], clientPayments: [], writeOffs: [], otherIncome: [], otherIncomeReceipts: [], expenses: [], expensePayments: [],
    supplierInvoices: [], supplierPayments: [], stockMovements: [], attendance: [], overtime: [], advances: [], payrollAdjustments: [],
    payrollPayments: [], commissions: [], commissionPayments: [],
  }
}

describe('revenue after write-offs', () => {
  it('takes written-off amounts out of revenue but keeps the write-off out of money received', () => {
    const s = blank()
    s.installments = [{ id: 'i1', projectId: 'p1', description: 'Slab', dueDate: TODAY, amount: 100000, billed: true, billedOn: TODAY }]
    s.clientPayments = [{ id: 'cp1', installmentId: 'i1', date: TODAY, amount: 60000, method: 'Cash' }]
    s.writeOffs = [{ id: 'w1', installmentId: 'i1', date: TODAY, amount: 30000, reason: 'Client cannot pay' }]
    s.otherIncome = [{ id: 'o1', date: TODAY, description: 'Scrap', amount: 5000 }]
    const t = derive(s).totals
    // billed 100,000 + other 5,000, less the 30,000 written off
    expect(t.revenueNet).toBe(75000)
    expect(t.revenueRecorded).toBe(105000)
    expect(t.paymentsReceived).toBe(60000)
    // what's left to collect: 100,000 − 60,000 received − 30,000 written off, plus the unpaid 5,000
    expect(t.receivable).toBe(15000)
    expect(derive(s).monthly.get(TODAY.slice(0, 7))!.revenue).toBe(75000)
  })
})

describe('overdue', () => {
  it('counts from the day after billing, whatever the planned date says', () => {
    const s = blank()
    s.installments = [
      // planned for next month but billed yesterday and unpaid: overdue by one day
      { id: 'i1', projectId: 'p1', description: 'Slab', dueDate: addDays(TODAY, 30), amount: 50000, billed: true, billedOn: addDays(TODAY, -1) },
      // planned long ago but billed today: not late yet
      { id: 'i2', projectId: 'p1', description: 'Roof', dueDate: addDays(TODAY, -60), amount: 50000, billed: true, billedOn: TODAY },
      // not billed: never overdue, it isn't revenue yet
      { id: 'i3', projectId: 'p1', description: 'Finishing', dueDate: addDays(TODAY, -10), amount: 50000, billed: false },
    ]
    const by = new Map(derive(s).installments.map((i) => [i.inst.id, i]))
    expect(by.get('i1')).toMatchObject({ status: 'Overdue', daysOverdue: 1 })
    expect(by.get('i2')).toMatchObject({ status: 'Due', daysOverdue: 0 })
    expect(by.get('i3')!.status).toBe('Upcoming')
  })

  it('treats other income the same way, from the day it was recorded', () => {
    const s = blank()
    s.otherIncome = [
      { id: 'o1', date: addDays(TODAY, -3), description: 'Scrap steel', amount: 10000 },
      { id: 'o2', date: TODAY, description: 'Site visit fee', amount: 5000 },
    ]
    const by = new Map(derive(s).otherIncome.map((o) => [o.income.id, o]))
    expect(by.get('o1')!.status).toBe('Overdue')
    expect(by.get('o2')!.status).toBe('Due')
  })
})

describe('billing late reminder', () => {
  it('lists unbilled installments past their planned date, longest late first', () => {
    const s = blank()
    s.installments = [
      { id: 'i1', projectId: 'p1', description: 'Slab', dueDate: addDays(TODAY, -2), amount: 50000, billed: false },
      { id: 'i2', projectId: 'p1', description: 'Roof', dueDate: addDays(TODAY, -9), amount: 50000, billed: false },
      // planned today: not late yet
      { id: 'i3', projectId: 'p1', description: 'Walls', dueDate: TODAY, amount: 50000, billed: false },
      // already billed: that's the overdue rule's job, not this one
      { id: 'i4', projectId: 'p1', description: 'Footing', dueDate: addDays(TODAY, -20), amount: 50000, billed: true, billedOn: addDays(TODAY, -20) },
    ]
    const late = billingLate(derive(s).installments)
    expect(late.map((i) => [i.inst.id, i.daysLate])).toEqual([['i2', 9], ['i1', 2]])
    // yellow "Billing Late" on the schedule, red "Overdue" once billed and unpaid; the real status stays Upcoming
    const by = new Map(derive(s).installments.map((i) => [i.inst.id, i]))
    expect(by.get('i1')!.status).toBe('Upcoming')
    expect(['i1', 'i3', 'i4'].map((id) => scheduleStatus(by.get(id)!))).toEqual(['Billing Late', 'Upcoming', 'Overdue'])
  })

  it('puts overdue money in red and late billing in yellow on the project', () => {
    const s = blank()
    s.installments = [
      { id: 'i1', projectId: 'p2', description: 'Slab', dueDate: addDays(TODAY, -10), amount: 60000, billed: true, billedOn: addDays(TODAY, -3) },
      { id: 'i2', projectId: 'p2', description: 'Roof', dueDate: addDays(TODAY, -4), amount: 50000, billed: false },
    ]
    const att = derive(s).projectFinById.get('p2')!.attention
    expect(att.filter((a) => a.tone === 'danger').map((a) => a.text)).toEqual([expect.stringContaining('60,000 overdue')])
    expect(att).toContainEqual({ text: 'Roof not billed · planned date passed', tone: 'warning' })
  })

  it('skips projects that are on hold or cancelled', () => {
    const s = blank()
    s.projects = s.projects.map((p) => (p.id === 'p1' ? { ...p, status: 'On Hold' as const } : p.id === 'p2' ? { ...p, status: 'Cancelled' as const } : p))
    s.installments = [
      { id: 'i1', projectId: 'p1', description: 'Slab', dueDate: addDays(TODAY, -5), amount: 50000, billed: false },
      { id: 'i2', projectId: 'p2', description: 'Slab', dueDate: addDays(TODAY, -5), amount: 50000, billed: false },
    ]
    expect(billingLate(derive(s).installments)).toEqual([])
  })
})

describe('cash basis', () => {
  const lastWeek = addDays(weekStart(TODAY), -7)

  function sample() {
    const s = blank()
    s.installments = [{ id: 'i1', projectId: 'p1', description: 'Foundation', dueDate: lastWeek, amount: 100000, billed: true, billedOn: lastWeek }]
    s.clientPayments = [{ id: 'cp1', installmentId: 'i1', date: lastWeek, amount: 60000, method: 'Cash' }]
    s.expenses = [{ id: 'x1', date: lastWeek, scope: 'Project', projectId: 'p1', group: 'Construction', category: 'Transport', description: 'Lorry hire', payee: 'Lorry', amount: 30000 }]
    s.expensePayments = [{ id: 'xp1', expenseId: 'x1', date: lastWeek, amount: 10000, method: 'Cash' }]
    // 10 bags into stock at 2,000, all paid; 4 of them issued to p1
    s.supplierInvoices = [{ id: 'si1', supplierId: 's1', invoiceNo: 'INV-1', date: lastWeek, dueDate: lastWeek, lines: [{ id: 'l1', description: 'Cement', materialId: 'm1', qty: 10, unit: 'bag', unitPrice: 2000 }] }]
    s.supplierPayments = [{ id: 'sp1', invoiceId: 'si1', date: lastWeek, amount: 20000, method: 'Bank' }]
    s.stockMovements = [
      { id: 'mv1', materialId: 'm1', date: lastWeek, type: 'Received', qty: 10, unitCost: 2000, invoiceId: 'si1' },
      { id: 'mv2', materialId: 'm1', date: lastWeek, type: 'Issued', qty: -4, projectId: 'p1' },
    ]
    return s
  }

  it('counts only money received and paid, with stock issued at cost', () => {
    const p = derive(sample()).projectFinById.get('p1')!
    // accrual: 100,000 billed − 30,000 expense − 8,000 stock issued
    expect(p.profit).toBe(62000)
    // cash: 60,000 received − 10,000 paid on the expense − 8,000 stock issued
    expect(p.cash).toMatchObject({ received: 60000, expenses: 10000, materials: 8000, costs: 18000, profit: 42000 })
    expect(projectFigures(p, 'cash').profit).toBe(42000)
    expect(projectFigures(p, 'accrual').profit).toBe(62000)
  })

  it('puts cash in and out in the month the money moved, without the stock issue', () => {
    const m = derive(sample()).monthly.get(lastWeek.slice(0, 7))!
    expect(m.received).toBe(60000)
    // 10,000 expense payment + 20,000 stock invoice payment; the 8,000 issue is not new cash
    expect(m.paid).toBe(30000)
  })

  it('counts the stock purchase once in company cash, not again when issued', () => {
    const d = derive(sample())
    // 10,000 expense payment + 20,000 stock invoice payment; the 8,000 issue is not new cash
    expect(d.totals.cashPaidOut).toBe(30000)
    expect(d.cashLines.filter((l) => l.fromStock).map((l) => l.amount)).toEqual([8000])
  })

  it('splits a wage payment over the projects worked that week and keeps advances at company level', () => {
    const s = blank()
    // 2 days on p1 and 1 day on p2 at 4,500; a 3,000 advance taken off the 13,500 gross, 10,500 paid
    s.attendance = [
      { date: lastWeek, employeeId: 'w1', status: 'Present', projectId: 'p1' },
      { date: addDays(lastWeek, 1), employeeId: 'w1', status: 'Present', projectId: 'p1' },
      { date: addDays(lastWeek, 2), employeeId: 'w1', status: 'Present', projectId: 'p2' },
    ]
    s.advances = [{ id: 'a1', date: lastWeek, employeeId: 'w1', amount: 3000, method: 'Cash' }]
    s.payrollPayments = [{ id: 'pp1', weekStart: lastWeek, employeeId: 'w1', date: addDays(lastWeek, 5), amount: 10500, method: 'Cash' }]
    const d = derive(s)
    expect(d.projectFinById.get('p1')!.cash.labour).toBe(7000)
    expect(d.projectFinById.get('p2')!.cash.labour).toBe(3500)
    // advance and net wages together are the gross: nothing counted twice
    expect(d.totals.cashPaidOut).toBe(13500)
    // accrual still charges the gross wages to the projects
    expect(d.projectFinById.get('p1')!.costs.labour).toBe(9000)
  })

  it('counts commission when it is paid, not when it is confirmed', () => {
    const s = blank()
    s.commissions = [{ id: 'k1', projectId: 'p1', recipient: 'Agent', role: 'Introducer', rate: 1, status: 'Confirmed', confirmedOn: lastWeek }]
    s.commissionPayments = [{ id: 'kp1', arrangementId: 'k1', date: lastWeek, amount: 1000, method: 'Cash' }]
    const p = derive(s).projectFinById.get('p1')!
    expect(p.cash.commission).toBe(1000)
    expect(p.commissionCommitted).toBeGreaterThan(1000)
  })
})

describe('project timeline', () => {
  it('lists only the activity log, newest first, ordered within a day by when it was recorded', () => {
    const s = createSeed()
    s.activity = [
      { id: '99', date: '2026-10-30', projectId: 'p1', kind: 'note', text: 'billed', createdAt: '2026-10-30T05:13:09Z' },
      { id: '100', date: '2026-10-30', projectId: 'p1', kind: 'payment', text: 'paid', createdAt: '2026-10-30T05:13:12Z' },
      { id: '7', date: '2026-10-29', projectId: 'p1', kind: 'status', text: 'created' },
      { id: '8', date: '2026-10-30', projectId: 'p2', kind: 'note', text: 'other project' },
    ]
    // the payment was recorded after the billing on the same day, so it comes first
    expect(projectActivity(derive(s), 'p1').map((a) => a.text)).toEqual(['paid', 'billed', 'created'])
  })

  it('breaks a tie on the same moment by the higher id, compared as numbers', () => {
    const s = createSeed()
    s.activity = [
      { id: '99', date: '2026-10-30', projectId: 'p1', kind: 'note', text: 'older', createdAt: '2026-10-30T05:13:09Z' },
      { id: '100', date: '2026-10-30', projectId: 'p1', kind: 'note', text: 'newer', createdAt: '2026-10-30T05:13:09Z' },
    ]
    expect(projectActivity(derive(s), 'p1').map((a) => a.text)).toEqual(['newer', 'older'])
  })
})
