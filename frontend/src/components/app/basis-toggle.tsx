import { useState } from 'react'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import type { ProfitBasis } from '@/lib/finance'

const KEY = 'sk.profitBasis'

// remembers the last choice so every screen opens on the basis the owner was looking at
export function useBasis() {
  const [basis, setBasis] = useState<ProfitBasis>(() => {
    try {
      return localStorage.getItem(KEY) === 'cash' ? 'cash' : 'accrual'
    } catch {
      return 'accrual'
    }
  })
  const set = (b: ProfitBasis) => {
    setBasis(b)
    try { localStorage.setItem(KEY, b) } catch { /* private window, nothing to keep */ }
  }
  return [basis, set] as const
}

/** Accrual = counted when billed or incurred. Cash = counted when the money actually came in or went out. */
export function BasisToggle({ value, onChange }: { value: ProfitBasis; onChange: (b: ProfitBasis) => void }) {
  return (
    <Tabs value={value} onValueChange={(v) => onChange(v as ProfitBasis)}>
      <TabsList aria-label="Calculation basis">
        <TabsTrigger value="accrual" title="Revenue when billed, costs when incurred">Accrual</TabsTrigger>
        <TabsTrigger value="cash" title="Money received less money paid out">Cash</TabsTrigger>
      </TabsList>
    </Tabs>
  )
}
