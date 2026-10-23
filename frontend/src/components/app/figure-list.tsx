import { cn } from '@/lib/utils'
import { Money } from './money'

export interface FigureRow {
  label: React.ReactNode
  value: number | React.ReactNode
  kind?: 'base' | 'less' | 'sub' | 'total' | 'highlight'
  note?: React.ReactNode
  decimals?: number
  tone?: 'default' | 'muted' | 'auto' | 'negative' | 'positive'
}

/** Ledger-style breakdown that keeps figures separate and shows how one derives from the others. */
export function FigureList({ rows, className, decimals }: { rows: FigureRow[]; className?: string; decimals?: number }) {
  return (
    <dl className={cn('grid', className)}>
      {rows.map((r, i) => {
        const kind = r.kind ?? 'base'
        const value = typeof r.value === 'number'
          ? <Money value={kind === 'less' ? -Math.abs(r.value) : r.value} decimals={r.decimals ?? decimals ?? 2} tone={r.tone} />
          : r.value
        return (
          <div
            key={i}
            className={cn(
              'flex items-baseline justify-between gap-4 border-b py-2.5 last:border-b-0',
              kind === 'total' && 'border-t-foreground -mt-px border-t border-b-0',
              kind === 'highlight' && 'bg-muted -mx-3 rounded-md border-b-0 px-3',
            )}
          >
            <dt className={cn('grid gap-0.5', (kind === 'less' || kind === 'sub') && 'pl-4', kind === 'sub' && 'text-muted-foreground text-[13px]', (kind === 'total' || kind === 'highlight') && 'font-semibold')}>
              <span>
                {kind === 'less' && <span className="text-muted-foreground">Less: </span>}
                {r.label}
              </span>
              {r.note && <span className="text-muted-foreground text-xs font-normal">{r.note}</span>}
            </dt>
            <dd className={cn('text-right font-medium whitespace-nowrap', kind === 'sub' && 'text-muted-foreground text-[13px]', (kind === 'total' || kind === 'highlight') && 'font-semibold')}>{value}</dd>
          </div>
        )
      })}
    </dl>
  )
}
