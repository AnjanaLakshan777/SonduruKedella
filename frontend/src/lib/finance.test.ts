import { describe, expect, it } from 'vitest'
import { createSeed } from '@/data/seed'
import type { DataState } from '@/data/types'
import { addDays, TODAY, weekStart } from './dates'
import { derive, projectActivity, projectFigures } from './finance'

// the seed with every money record cleared, so a test only sees what it adds
function blank(): DataState {
  const s = createSeed()
  return {
    ...s, installments: [], clientPayments: [], writeOffs: [], otherIncome: [], otherIncomeReceipts: [], expenses: [], expensePayments: [],
    supplierInvoices: [], supplierPayments: [], stockMovements: [], attendance: [], overtime: [], advances: [], payrollAdjustments: [],
    payrollPayments: [], commissions: [], commissionPayments: [],
  }
}

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
