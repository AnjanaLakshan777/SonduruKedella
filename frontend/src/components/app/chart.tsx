import { formatCompact, formatLKR } from '@/lib/format'

/** Shared Recharts styling: axis ticks and tooltip in theme tokens. */
export const axisProps = {
  tick: { fill: 'var(--muted-foreground)', fontSize: 12 },
  tickLine: false,
  axisLine: false,
} as const

export const moneyAxis = { ...axisProps, tickFormatter: (v: number) => formatCompact(v), width: 48 }

export function ChartTooltip({ active, payload, label, labelFormatter }: { active?: boolean; payload?: { name: string; value: number; color: string; dataKey: string }[]; label?: string; labelFormatter?: (l: string) => string }) {
  if (!active || !payload?.length) return null
  return (
    <div className="bg-popover text-popover-foreground grid min-w-44 gap-1.5 rounded-md border px-3 py-2 text-xs shadow-md">
      <div className="font-medium">{labelFormatter ? labelFormatter(label ?? '') : label}</div>
      {payload.map((p) => (
        <div key={p.dataKey} className="flex items-center justify-between gap-4">
          <span className="text-muted-foreground flex items-center gap-1.5"><span className="size-2 rounded-[2px]" style={{ background: p.color }} />{p.name}</span>
          <span className="tabular font-medium">{formatLKR(p.value, { decimals: 0 })}</span>
        </div>
      ))}
    </div>
  )
}

export function Legend({ items }: { items: { label: string; color: string }[] }) {
  return (
    <div className="text-muted-foreground flex flex-wrap gap-x-4 gap-y-1 text-xs">
      {items.map((i) => <span key={i.label} className="flex items-center gap-1.5"><span className="size-2.5 rounded-[3px]" style={{ background: i.color }} />{i.label}</span>)}
    </div>
  )
}
