import { describe, expect, it } from 'vitest'
import { createSeed } from '@/data/seed'
import type { DataState } from '@/data/types'
import { addDays, TODAY } from './dates'
import { derive } from './finance'
import { notifications } from './notifications'

function blank(): DataState {
  const s = createSeed()
  return {
    ...s, installments: [], clientPayments: [], writeOffs: [], otherIncome: [], otherIncomeReceipts: [], expenses: [], expensePayments: [],
    supplierInvoices: [], supplierPayments: [], stockMovements: [], attendance: [], overtime: [], advances: [], payrollAdjustments: [],
    payrollPayments: [], commissions: [], commissionPayments: [],
  }
}

describe('notifications', () => {
  it('lists the newest first, by the day each one came up', () => {
    const s = blank()
    s.installments = [
      // overdue since it was billed 10 days ago
      { id: 'i1', projectId: 'p2', description: 'Slab', dueDate: addDays(TODAY, -12), amount: 50000, billed: true, billedOn: addDays(TODAY, -10) },
      // billed today and unpaid
      { id: 'i2', projectId: 'p2', description: 'Roof', dueDate: TODAY, amount: 50000, billed: true, billedOn: TODAY },
      // planned 5 days ago, still unbilled: late since 4 days ago
      { id: 'i3', projectId: 'p2', description: 'Walls', dueDate: addDays(TODAY, -5), amount: 50000, billed: false },
    ]
    s.overtime = [{ id: 'ot1', date: addDays(TODAY, -2), employeeId: 'w1', hours: 2, rate: 500, approved: false, recordedBy: 'Site' }]
    const list = notifications(derive(s))
    expect(list.map((n) => n.key)).toEqual(['overdue:i2', 'overtime:ot1', 'billing-late:i3', 'overdue:i1'])
  })

  it('gives a new overtime entry a new key, so it shows as unread again', () => {
    const s = blank()
    s.overtime = [{ id: 'ot1', date: TODAY, employeeId: 'w1', hours: 2, rate: 500, approved: false, recordedBy: 'Site' }]
    const before = notifications(derive(s))[0].key
    s.overtime.push({ id: 'ot2', date: TODAY, employeeId: 'w1', hours: 1, rate: 500, approved: false, recordedBy: 'Site' })
    expect(notifications(derive(s))[0].key).not.toBe(before)
  })
})
