const nf = (d: number) => new Intl.NumberFormat('en-LK', { minimumFractionDigits: d, maximumFractionDigits: d })
const cache: Record<number, Intl.NumberFormat> = {}

export function formatNumber(value: number, decimals = 2) {
  const f = (cache[decimals] ??= nf(decimals))
  return f.format(Math.abs(value))
}

/** LKR 1,250,000.00 · − LKR 180,000.00 */
export function formatLKR(value: number, opts: { decimals?: number; currency?: boolean; signed?: boolean } = {}) {
  const n = Number(value) || 0
  const sign = n < 0 ? '− ' : opts.signed && n > 0 ? '+ ' : ''
  return sign + (opts.currency === false ? '' : 'LKR ') + formatNumber(n, opts.decimals ?? 2)
}

/** Compact axis label: LKR 12.5M, 850K */
export function formatCompact(value: number) {
  const a = Math.abs(value)
  const s = value < 0 ? '−' : ''
  if (a >= 1_000_000) return `${s}${(a / 1_000_000).toFixed(a >= 10_000_000 ? 0 : 1)}M`
  if (a >= 1_000) return `${s}${Math.round(a / 1000)}K`
  return `${s}${a}`
}

export function formatPercent(value: number, decimals = 0) {
  return `${value.toFixed(decimals)}%`
}

export function formatQty(value: number, unit?: string) {
  const s = new Intl.NumberFormat('en-LK', { maximumFractionDigits: 2 }).format(value)
  return unit ? `${s} ${unit}` : s
}

export function formatBytes(b?: number) {
  if (b == null) return ''
  return b > 1048576 ? `${(b / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`
}

export function parseAmount(input: string) {
  const n = Number(String(input).replace(/[^0-9.-]/g, ''))
  return Number.isFinite(n) ? n : NaN
}

export const round2 = (n: number) => Math.round(n * 100) / 100
