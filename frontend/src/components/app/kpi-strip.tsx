import { Info } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

export interface KpiItem {
  label: string
  value: React.ReactNode
  unit?: string
  /** Recorded renders as plain text; others as a tag. */
  basis?: 'Recorded' | 'Received' | 'Estimated' | 'Expected' | 'Agreed'
  caption?: React.ReactNode
  hint?: string
  tone?: 'default' | 'destructive'
}

const TAG: Record<string, string> = {
  Received: 'bg-success-soft text-success',
  Estimated: 'bg-warning-soft text-warning',
  Expected: 'bg-warning-soft text-warning',
  Agreed: 'bg-primary-soft text-primary-soft-foreground',
}

/** One bordered strip of key figures, each with its basis. Balances to 3×2 / 2×n when narrow. */
export function KpiStrip({ items, className }: { items: KpiItem[]; className?: string }) {
  const n = items.length
  return (
    <section aria-label="Key figures" className={cn('@container min-w-0', className)}>
      <div
        className={cn(
          'bg-card text-card-foreground grid overflow-hidden rounded-xl border shadow-sm',
          'grid-cols-1 @[360px]:grid-cols-2',
          n === 3 && '@[600px]:grid-cols-3',
          n === 4 && '@[760px]:grid-cols-4',
          n >= 5 && '@[600px]:grid-cols-3',
          n === 5 && '@[1040px]:grid-cols-5',
          n === 6 && '@[1240px]:grid-cols-6',
        )}
      >
        {items.map((it) => (
          <div key={it.label} className="-mr-px -mb-px grid min-w-0 content-start gap-1.5 border-r border-b p-4">
            <div className="text-muted-foreground flex items-center gap-1.5 text-[13px] leading-[18px] font-medium">
              <span className="truncate">{it.label}</span>
              {it.hint && (
                <Tooltip>
                  <TooltipTrigger className="text-muted-foreground focus-visible:ring-ring/50 rounded-sm outline-none focus-visible:ring-[3px]" aria-label={it.hint}>
                    <Info className="size-3.5" />
                  </TooltipTrigger>
                  <TooltipContent>{it.hint}</TooltipContent>
                </Tooltip>
              )}
              {it.unit && <span className="ml-auto text-[11px] tracking-wide">{it.unit}</span>}
            </div>
            <div className={cn('tabular text-2xl leading-8 font-semibold tracking-tight whitespace-nowrap', it.tone === 'destructive' && 'text-destructive')}>{it.value}</div>
            <div className="text-muted-foreground flex flex-wrap items-center gap-1.5 text-xs">
              {it.basis && it.basis !== 'Recorded' && <span className={cn('rounded-sm px-1.5 font-medium', TAG[it.basis])}>{it.basis}</span>}
              {(it.basis === 'Recorded' || it.caption) && (
                <span>
                  {it.basis === 'Recorded' && 'Recorded'}
                  {it.basis === 'Recorded' && it.caption ? ' · ' : ''}
                  {it.caption}
                </span>
              )}
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}
