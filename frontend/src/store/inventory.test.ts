import { beforeEach, describe, expect, it } from 'vitest'
import { derive } from '@/lib/finance'
import { movementError, replayFifo } from '@/lib/fifo'
import type { StockMovement } from '@/data/types'
import { useStore } from './store'

// The pages work every stock figure out from the ledger in the store (derive -> replayFifo), the way the
// UI reads it through useData(). The backend posts the real movements; these tests check the frontend's
// own FIFO maths on the demo data. post() appends to the local ledger with the same checks the
// prototype used, since the store's addStockMovement now goes to the server.
const data = () => derive(useStore.getState())
let n = 0
function post(m: Omit<StockMovement, 'id'>) {
  const s = useStore.getState()
  const mat = s.materials.find((x) => x.id === m.materialId)
  const problem = movementError(s.stockMovements, m, mat?.unit, mat?.name)
  if (problem) throw new Error(problem)
  const posted = { ...m, id: `test${++n}` }
  const stockMovements = [...s.stockMovements, posted]
  useStore.setState({ stockMovements })
  return replayFifo(stockMovements).costed.get(posted.id)!
}
const base = { date: '2026-10-21', reference: 'TEST' }

beforeEach(() => useStore.getState().reset())

describe('inventory figures from the seed data', () => {
  it('values stock as the sum of the FIFO layers, not quantity x default unit cost', () => {
    const d = data()
    for (const m of d.materials) {
      expect(m.value).toBeCloseTo(m.layers.reduce((t, l) => t + l.remainingValue, 0), 2)
      expect(m.qty).toBeCloseTo(m.layers.reduce((t, l) => t + l.remainingQty, 0), 3)
    }
    expect(d.totals.stockValue).toBeCloseTo(d.materials.reduce((t, m) => t + m.value, 0), 2)

    // the default unit cost is only a reference: changing it revalues nothing
    useStore.setState((s) => ({ materials: s.materials.map((m) => ({ ...m, unitCost: 1 })) }))
    expect(data().totals.stockValue).toBe(d.totals.stockValue)
    expect(data().materials.map((m) => m.value)).toEqual(d.materials.map((m) => m.value))
  })

  it('charges projects each issue at its FIFO cost', () => {
    const d = data()
    for (const line of d.expenseLines.filter((l) => l.source === 'Stock issue')) {
      expect(line.amount).toBe(-d.stockCost.get(line.refId)!.totalCost)
    }
  })
})

describe('posting stock movements', () => {
  it('refreshes stock after a receipt, an issue, a return and an adjustment', () => {
    const cement = data().materials.find((m) => m.material.sku === 'CEM-50')!
    const before = { qty: cement.qty, value: cement.value }
    const id = cement.material.id

    post({ ...base, materialId: id, type: 'Received', qty: 10, unitCost: 2600 })
    expect(data().materials.find((m) => m.material.id === id)!.qty).toBeCloseTo(before.qty + 10, 3)

    const issued = post({ ...base, materialId: id, type: 'Issued', qty: -5, projectId: 'p2' })
    expect(issued.totalCost).toBeLessThan(0)
    const projectBefore = data().projectFinById.get('p2')!.costs.materialsStock

    const returned = post({ ...base, materialId: id, type: 'Returned', qty: 5, projectId: 'p2', returnedFromId: issued.movement.id })
    expect(returned.totalCost).toBe(-issued.totalCost) // the same cost comes back
    expect(data().projectFinById.get('p2')!.costs.materialsStock).toBeCloseTo(projectBefore + issued.totalCost, 2)

    post({ ...base, materialId: id, type: 'Adjustment', qty: 2, unitCost: 2500, reason: 'Count found 2 more' })
    const after = data().materials.find((m) => m.material.id === id)!
    expect(after.qty).toBeCloseTo(before.qty + 12, 3)
    expect(after.value).toBeCloseTo(before.value + 26000 + 5000, 2)
    expect(after.layers.at(-1)).toMatchObject({ origin: 'Adjustment', unitCost: 2500, remainingQty: 2 })
  })

  it('refuses an increase without a cost or a reduction with one, saving nothing', () => {
    const id = data().materials[0].material.id
    const count = useStore.getState().stockMovements.length
    expect(() => post({ ...base, materialId: id, type: 'Adjustment', qty: 2, reason: 'Count found 2 more' })).toThrow(/unit cost/)
    expect(() => post({ ...base, materialId: id, type: 'Adjustment', qty: -1, unitCost: 2000, reason: 'Damaged in store' })).toThrow(/leave the unit cost empty/)
    expect(() => post({ ...base, materialId: id, type: 'Issued', qty: -1e6, projectId: 'p2' })).toThrow(/in stock/)
    expect(useStore.getState().stockMovements).toHaveLength(count)
  })

  it('leaves the default unit cost alone when stock comes in at another price', () => {
    const cement = useStore.getState().materials.find((m) => m.sku === 'CEM-50')!
    post({ ...base, materialId: cement.id, type: 'Received', qty: 10, unitCost: 9999 })
    expect(useStore.getState().materials.find((m) => m.id === cement.id)!.unitCost).toBe(cement.unitCost)
    expect(data().materials.find((m) => m.material.id === cement.id)!.layers.at(-1)).toMatchObject({ unitCost: 9999 })
  })
})
