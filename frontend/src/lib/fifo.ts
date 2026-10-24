import type { StockMovement } from '@/data/types'

// FIFO stock costing for the local store, following the backend's rules (inventory.service.FifoCosting
// and StockLedger, see docs/DATABASE.md "Stock costing"). The movement list is the ledger and its order
// is the posting order. Replaying it is deterministic and the list is append-only, so a movement's cost
// never changes once posted - later purchases only add layers behind it.
//
// Arithmetic is in integers (cents, thousandths of a unit, 1/10000 of a rupee for unit costs) so the
// rounding is exact: each draw is rounded once, HALF_UP, and the last of a layer or of a draw takes the
// exact remainder.

export interface CostLayer {
  /** the movement that brought the stock in */
  id: string
  materialId: string
  date: string
  origin: 'Receipt' | 'Adjustment'
  reference?: string
  originalQty: number
  unitCost: number
  originalCost: number
  remainingQty: number
  remainingValue: number
}

export interface Allocation {
  layerId: string
  /** signed like the movement: negative when taken from the layer, positive when a return puts it back */
  qty: number
  unitCost: number
  cost: number
}

export interface CostedMovement {
  movement: StockMovement
  /** signed: negative for anything leaving stock */
  totalCost: number
  allocations: Allocation[]
  /** issues only */
  returnedQty?: number
  returnableQty?: number
}

export interface FifoResult {
  costed: Map<string, CostedMovement>
  /** every layer, used-up ones included, in FIFO order */
  layers: CostLayer[]
}

const QTY = 1000
const COST = 10000

const toMilli = (q: number) => Math.round(q * QTY)
const toUnit = (u: number) => Math.round(u * COST)
const fromCents = (c: number) => c / 100
const fromMilli = (m: number) => m / QTY

// q (thousandths) x unit cost (1/10000) is in 1e-7 rupees; to cents HALF_UP
function priceCents(qMilli: number, unit: number) {
  return Math.floor((qMilli * unit + 50_000) / 100_000)
}

/** The cost of q out of a pool holding poolQty worth poolCents: q x unit cost, except the last of the pool takes all that's left, and nothing takes more than the pool is worth. */
export function shareCents(qMilli: number, poolMilli: number, poolCents: number, unit: number) {
  if (qMilli === poolMilli) return poolCents
  return Math.min(priceCents(qMilli, unit), poolCents)
}

export function receiptCost(qty: number, unitCost: number) {
  return fromCents(priceCents(toMilli(qty), toUnit(unitCost)))
}

interface Pool { qMilli: number; cents: number; unit: number }
interface Draw { index: number; qMilli: number; cents: number }

// takes q from the pools in the order given; null when they can't cover it
function draw(pools: Pool[], qMilli: number): Draw[] | null {
  const draws: Draw[] = []
  let left = qMilli
  for (let i = 0; i < pools.length && left > 0; i++) {
    const take = Math.min(left, pools[i].qMilli)
    if (take <= 0) continue
    draws.push({ index: i, qMilli: take, cents: shareCents(take, pools[i].qMilli, pools[i].cents, pools[i].unit) })
    left -= take
  }
  return left > 0 ? null : draws
}

interface LiveLayer { layer: CostLayer; qMilli: number; cents: number; unit: number }
interface LiveDraw { layer: LiveLayer; qMilli: number; cents: number; backMilli: number; backCents: number }

export function replayFifo(movements: StockMovement[]): FifoResult {
  const costed = new Map<string, CostedMovement>()
  const layers: LiveLayer[] = []
  const open = new Map<string, LiveLayer[]>()
  const drawsOf = new Map<string, LiveDraw[]>()

  for (const m of movements) {
    const qMilli = toMilli(m.qty)
    const inbound = m.type === 'Received' || (m.type === 'Adjustment' && qMilli > 0)
    if (qMilli === 0) {
      costed.set(m.id, { movement: m, totalCost: 0, allocations: [] })
    } else if (inbound) {
      const unit = toUnit(m.unitCost ?? 0)
      const cents = priceCents(qMilli, unit)
      const layer: CostLayer = { id: m.id, materialId: m.materialId, date: m.date, origin: m.type === 'Received' ? 'Receipt' : 'Adjustment', reference: m.reference, originalQty: fromMilli(qMilli), unitCost: unit / COST, originalCost: fromCents(cents), remainingQty: 0, remainingValue: 0 }
      const live = { layer, qMilli, cents, unit }
      layers.push(live)
      open.set(m.materialId, [...(open.get(m.materialId) ?? []), live])
      costed.set(m.id, { movement: m, totalCost: fromCents(cents), allocations: [] })
    } else if (m.type === 'Returned') {
      // newest draw first, each quantity and its cost back into the layer it came from
      const taken = (drawsOf.get(m.returnedFromId ?? '') ?? []).slice().reverse()
      const ds = draw(taken.map((t) => ({ qMilli: t.qMilli - t.backMilli, cents: t.cents - t.backCents, unit: t.layer.unit })), qMilli)
      if (!ds) throw new Error(`Return ${m.id} puts back more than its issue took.`)
      let cents = 0
      const allocations: Allocation[] = []
      for (const d of ds) {
        const t = taken[d.index]
        t.backMilli += d.qMilli
        t.backCents += d.cents
        t.layer.qMilli += d.qMilli
        t.layer.cents += d.cents
        cents += d.cents
        allocations.push({ layerId: t.layer.layer.id, qty: fromMilli(d.qMilli), unitCost: t.layer.unit / COST, cost: fromCents(d.cents) })
      }
      costed.set(m.id, { movement: m, totalCost: fromCents(cents), allocations })
    } else {
      // issue or reduction: oldest layer first
      const pools = open.get(m.materialId) ?? []
      const ds = draw(pools.map((l) => ({ qMilli: l.qMilli, cents: l.cents, unit: l.unit })), -qMilli)
      if (!ds) throw new Error(`Movement ${m.id} takes more stock than the layers hold.`)
      let cents = 0
      const allocations: Allocation[] = []
      const mine: LiveDraw[] = []
      for (const d of ds) {
        const l = pools[d.index]
        l.qMilli -= d.qMilli
        l.cents -= d.cents
        cents += d.cents
        allocations.push({ layerId: l.layer.id, qty: -fromMilli(d.qMilli), unitCost: l.unit / COST, cost: -fromCents(d.cents) })
        mine.push({ layer: l, qMilli: d.qMilli, cents: d.cents, backMilli: 0, backCents: 0 })
      }
      drawsOf.set(m.id, mine)
      costed.set(m.id, { movement: m, totalCost: -fromCents(cents), allocations })
    }
  }

  for (const [issueId, ds] of drawsOf) {
    const c = costed.get(issueId)!
    if (c.movement.type !== 'Issued') continue
    const back = ds.reduce((t, d) => t + d.backMilli, 0)
    c.returnedQty = fromMilli(back)
    c.returnableQty = fromMilli(-toMilli(c.movement.qty) - back)
  }
  return {
    costed,
    layers: layers.map((l) => ({ ...l.layer, remainingQty: fromMilli(l.qMilli), remainingValue: fromCents(l.cents) })),
  }
}

/** On-hand quantity and FIFO value of a material: what is left in its layers. */
export function stockOf(result: FifoResult, materialId: string) {
  const mine = result.layers.filter((l) => l.materialId === materialId)
  return {
    qty: fromMilli(mine.reduce((t, l) => t + toMilli(l.remainingQty), 0)),
    value: fromCents(mine.reduce((t, l) => t + Math.round(l.remainingValue * 100), 0)),
  }
}

// The same checks the backend makes before posting, with its messages. Returns the first problem, if any.
export function movementError(movements: StockMovement[], m: Omit<StockMovement, 'id'>, unitName = '', materialName = ''): string | undefined {
  const q = toMilli(m.qty)
  if (q === 0) return m.type === 'Adjustment' ? 'An adjustment must add or remove some stock.' : 'Enter a quantity.'
  const fifo = replayFifo(movements)
  const stock = stockOf(fifo, m.materialId)
  const short = `Only ${fromMilli(toMilli(stock.qty))} ${unitName} of ${materialName} in stock.`.replace(/\s+/g, ' ')
  switch (m.type) {
    case 'Received':
      if (q < 0) return 'Enter a quantity.'
      if (!(Number(m.unitCost) > 0)) return 'Enter the unit cost.'
      return undefined
    case 'Issued':
      if (q > 0) return 'Enter a quantity.'
      if (!m.projectId) return 'Choose the project receiving the material.'
      return -q > toMilli(stock.qty) ? short : undefined
    case 'Adjustment':
      if (!m.reason || m.reason.trim().length < 5) return 'Give a reason for the adjustment (count difference, damage…).'
      if (q < 0) {
        if (m.unitCost != null) return 'A stock reduction is costed from the oldest stock (FIFO); leave the unit cost empty.'
        return -q > toMilli(stock.qty) ? short : undefined
      }
      if (m.unitCost == null || Number.isNaN(m.unitCost)) return 'Enter the unit cost of the stock being added; it becomes a new FIFO cost layer.'
      return m.unitCost < 0 ? "The unit cost can't be negative." : undefined
    case 'Returned': {
      const issue = fifo.costed.get(m.returnedFromId ?? '')
      if (!issue || issue.movement.type !== 'Issued') return 'The selected issue does not exist.'
      if (issue.movement.materialId !== m.materialId) return 'That issue was for a different material.'
      if (q < 0) return 'Enter a quantity.'
      return q > toMilli(issue.returnableQty ?? 0) ? `Only ${issue.returnableQty} ${unitName} of this issue can still be returned.`.replace(/\s+/g, ' ') : undefined
    }
  }
}

/** Which layers a movement drew from (or a return went back to), e.g. "10 × 2,000.00 + 2 × 2,400.00". Empty for receipts. */
export function describeAllocations(c: CostedMovement) {
  const money = (v: number) => v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  const qty = (v: number) => v.toLocaleString('en-US', { maximumFractionDigits: 3 })
  return c.allocations.map((a) => `${qty(Math.abs(a.qty))} × ${money(a.unitCost)}`).join(' + ')
}
