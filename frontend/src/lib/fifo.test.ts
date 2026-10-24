import { describe, expect, it } from 'vitest'
import type { StockMovement } from '@/data/types'
import { describeAllocations, movementError, receiptCost, replayFifo, stockOf } from './fifo'

let n = 0
const mv = (type: StockMovement['type'], qty: number, extra: Partial<StockMovement> = {}): StockMovement =>
  ({ id: `t${++n}`, materialId: 'cem', date: '2026-10-20', type, qty, ...extra })
const receive = (qty: number, unitCost: number, materialId = 'cem') => mv('Received', qty, { unitCost, materialId })
const issue = (qty: number, materialId = 'cem') => mv('Issued', -qty, { projectId: 'p1', materialId })
const giveBack = (of: StockMovement, qty: number) => mv('Returned', qty, { projectId: of.projectId, returnedFromId: of.id, materialId: of.materialId })

describe('FIFO issues', () => {
  it("charges the owner's example from the oldest layer first: 10 x 2,000 + 2 x 2,400", () => {
    const a = receive(10, 2000)
    const b = receive(10, 2400)
    const out = issue(12)
    const r = replayFifo([a, b, out])

    const c = r.costed.get(out.id)!
    expect(c.totalCost).toBe(-24800)
    expect(c.allocations.map((x) => [x.layerId, x.qty, x.cost])).toEqual([[a.id, -10, -20000], [b.id, -2, -4800]])
    expect(describeAllocations(c)).toBe('10 × 2,000.00 + 2 × 2,400.00')
    expect(stockOf(r, 'cem')).toEqual({ qty: 8, value: 19200 })
  })

  it('takes part of the oldest layer, then the rest of it and part of the next', () => {
    const r = replayFifo([receive(10, 100), receive(10, 200), receive(10, 300), issue(5), issue(10), issue(10)])
    expect([...r.costed.values()].filter((c) => c.movement.type === 'Issued').map((c) => c.totalCost)).toEqual([-500, -1500, -2500])
    expect(stockOf(r, 'cem')).toEqual({ qty: 5, value: 1500 })
  })

  it('never changes an earlier issue when a dearer purchase comes later', () => {
    const first = [receive(10, 2000), receive(10, 2400)]
    const out = issue(12)
    const before = replayFifo([...first, out]).costed.get(out.id)!.totalCost
    const after = replayFifo([...first, out, receive(10, 4000)]).costed.get(out.id)!.totalCost
    expect(after).toBe(before)
  })

  it('keeps each material on its own layers', () => {
    const r = replayFifo([receive(10, 2000), receive(5, 18000, 'sand'), receive(10, 2400), issue(2, 'sand'), issue(11)])
    expect(stockOf(r, 'sand')).toEqual({ qty: 3, value: 54000 })
    expect(stockOf(r, 'cem')).toEqual({ qty: 9, value: 21600 })
  })

  it('values the stock as the sum of the open layers, not quantity x a default price', () => {
    const r = replayFifo([receive(100, 2500), receive(50, 2800), receive(30, 2710)])
    const layers = r.layers.filter((l) => l.remainingQty > 0)
    expect(layers.map((l) => l.remainingValue)).toEqual([250000, 140000, 81300])
    expect(stockOf(r, 'cem').value).toBe(471300)
  })

  it('gives receipts their own layer and no allocations', () => {
    const a = receive(12.345, 101.3333)
    const c = replayFifo([a]).costed.get(a.id)!
    expect(c.totalCost).toBe(1250.96)
    expect(receiptCost(12.345, 101.3333)).toBe(1250.96)
    expect(describeAllocations(c)).toBe('')
  })
})

describe('FIFO returns', () => {
  it('puts partial returns back into the layers the issue came from, newest draw first', () => {
    const a = receive(10, 2000)
    const b = receive(10, 2400)
    const out = issue(12)
    const r1 = giveBack(out, 1)
    const r2 = giveBack(out, 2)
    const r3 = giveBack(out, 9)
    const r = replayFifo([a, b, out, receive(10, 4000), r1, r2, r3])

    expect(r.costed.get(r1.id)!.allocations.map((x) => x.layerId)).toEqual([b.id])
    expect(r.costed.get(r1.id)!.totalCost).toBe(2400)
    expect(r.costed.get(r2.id)!.totalCost).toBe(4400) // the last 1 of B, then 1 of A
    expect(r.costed.get(r3.id)!.totalCost).toBe(18000)
    // fully returned: the layers hold exactly what they did, and the issue nets to 0
    expect(r.layers.slice(0, 2).map((l) => [l.remainingQty, l.remainingValue])).toEqual([[10, 20000], [10, 24000]])
    expect(r.costed.get(out.id)!.returnableQty).toBe(0)
  })

  it('reverses a multi-layer issue in one go, to the cent', () => {
    const out = issue(12)
    const back = giveBack(out, 12)
    const r = replayFifo([receive(10, 2000), receive(10, 2400), out, back])
    expect(r.costed.get(back.id)!.totalCost).toBe(24800)
  })

  it('lets the last return take the exact remainder when costs do not divide evenly', () => {
    const out = issue(4)
    const backs = [1, 1, 1, 1].map(() => giveBack(out, 1))
    const r = replayFifo([receive(3, 33.3333), receive(1.5, 10.0001), out, ...backs])
    expect(r.costed.get(out.id)!.totalCost).toBe(-110)
    expect(backs.map((b) => r.costed.get(b.id)!.totalCost)).toEqual([10, 33.33, 33.33, 33.34])
  })
})

describe('adjustments', () => {
  it('turns an increase into a layer at the stated cost and costs a reduction FIFO', () => {
    const found = mv('Adjustment', 2, { unitCost: 2500, reason: 'Count found 2 more' })
    const lost = mv('Adjustment', -12, { reason: 'Hardened in the rain' })
    const r = replayFifo([receive(10, 2000), receive(10, 2400), lost, found, mv('Adjustment', -9, { reason: 'Count difference' })])
    expect(r.costed.get(lost.id)!.totalCost).toBe(-24800)
    expect(r.costed.get(found.id)!.totalCost).toBe(5000)
    expect(stockOf(r, 'cem')).toEqual({ qty: 1, value: 2500 })
  })
})

describe('the checks made before posting', () => {
  const ledger = [receive(10, 2000)]
  const adj = (qty: number, unitCost?: number) => ({ materialId: 'cem', date: '2026-10-20', type: 'Adjustment' as const, qty, unitCost, reason: 'Stock count' })

  it('needs a unit cost on an increase', () => {
    expect(movementError(ledger, adj(2))).toMatch(/Enter the unit cost/)
    expect(movementError(ledger, adj(2, 0))).toBeUndefined() // stated, even if zero
    expect(movementError(ledger, adj(2, -1))).toMatch(/can't be negative/)
  })

  it('refuses a unit cost on a reduction, and more than is in stock', () => {
    expect(movementError(ledger, adj(-1, 2000))).toMatch(/leave the unit cost empty/)
    expect(movementError(ledger, adj(-1))).toBeUndefined()
    expect(movementError(ledger, adj(-11), 'bag', 'Cement')).toBe('Only 10 bag of Cement in stock.')
  })

  it('caps returns at what is still out on the issue', () => {
    const out = issue(4)
    const l = [...ledger, out, giveBack(out, 3)]
    const ret = (qty: number, of = out.id) => ({ materialId: 'cem', date: '2026-10-20', type: 'Returned' as const, qty, returnedFromId: of, projectId: 'p1' })
    expect(movementError(l, ret(2), 'bag')).toBe('Only 1 bag of this issue can still be returned.')
    expect(movementError(l, ret(1))).toBeUndefined()
    expect(movementError(l, ret(1, ledger[0].id))).toBe('The selected issue does not exist.')
  })

  it('refuses an issue larger than the stock', () => {
    expect(movementError(ledger, { materialId: 'cem', date: '2026-10-20', type: 'Issued', qty: -11, projectId: 'p1' }, 'bag', 'Cement'))
      .toBe('Only 10 bag of Cement in stock.')
  })
})
