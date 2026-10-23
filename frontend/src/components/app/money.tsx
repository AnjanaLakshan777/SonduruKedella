import { cn } from '@/lib/utils'
import { formatNumber } from '@/lib/format'

/** LKR amount: tabular figures, muted currency and decimals, true minus sign. */
export function Money({
  value, decimals = 2, currency = true, signed, tone = 'default', className,
}: {
  value: number
  decimals?: number
  currency?: boolean
  signed?: boolean
  /** auto = destructive when negative — use for losses only, not deductions */
  tone?: 'default' | 'muted' | 'auto' | 'negative' | 'positive'
  className?: string
}) {
  const n = Number(value) || 0
  const t = tone === 'auto' ? (n < 0 ? 'negative' : 'default') : tone
  const sign = n < 0 ? '− ' : signed && n > 0 ? '+ ' : ''
  const whole = formatNumber(n, decimals)
  const cut = decimals > 0 ? whole.length - decimals - 1 : whole.length
  return (
    <span
      className={cn(
        'tabular whitespace-nowrap',
        t === 'muted' && 'text-muted-foreground',
        t === 'negative' && 'text-destructive',
        t === 'positive' && 'text-success',
        className,
      )}
    >
      {sign}
      {currency && <span className={cn('font-normal', t === 'default' && 'text-muted-foreground')}>LKR&nbsp;</span>}
      {whole.slice(0, cut)}
      {decimals > 0 && <span className={cn(t === 'default' && 'text-muted-foreground')}>{whole.slice(cut)}</span>}
    </span>
  )
}
