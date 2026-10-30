/** Dates are ISO strings (YYYY-MM-DD) handled in UTC so arithmetic never drifts with the viewer's time zone. */
// today in Colombo, same as the backend's clock (en-CA formats as YYYY-MM-DD)
export const TODAY = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo' }).format(new Date())

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

export function toDate(iso: string) {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, d))
}
export function toISO(date: Date) {
  return date.toISOString().slice(0, 10)
}
export function addDays(iso: string, n: number) {
  const d = toDate(iso)
  d.setUTCDate(d.getUTCDate() + n)
  return toISO(d)
}
export function diffDays(a: string, b: string) {
  return Math.round((toDate(a).getTime() - toDate(b).getTime()) / 86400000)
}
export function weekday(iso: string) {
  return toDate(iso).getUTCDay()
}
/** Monday of the week containing iso. */
export function weekStart(iso: string) {
  const wd = weekday(iso)
  return addDays(iso, wd === 0 ? -6 : 1 - wd)
}
export function eachDay(from: string, to: string) {
  const out: string[] = []
  for (let d = from; d <= to; d = addDays(d, 1)) out.push(d)
  return out
}
/** DD MMM YYYY — 22 Oct 2026 */
export function formatDate(iso?: string | null) {
  if (!iso) return '—'
  const d = toDate(iso)
  return `${String(d.getUTCDate()).padStart(2, '0')} ${MONTHS[d.getUTCMonth()]} ${d.getUTCFullYear()}`
}
export function formatDateShort(iso: string) {
  const d = toDate(iso)
  return `${String(d.getUTCDate()).padStart(2, '0')} ${MONTHS[d.getUTCMonth()]}`
}
export function formatRange(from: string, to: string) {
  const a = toDate(from), b = toDate(to)
  if (a.getUTCMonth() === b.getUTCMonth() && a.getUTCFullYear() === b.getUTCFullYear())
    return `${String(a.getUTCDate()).padStart(2, '0')}–${formatDate(to)}`
  return `${formatDateShort(from)} – ${formatDate(to)}`
}
export function formatLongToday(iso = TODAY) {
  const d = toDate(iso)
  return `${DAYS[d.getUTCDay()]}, ${formatDate(iso)}`
}
export function dayName(iso: string) {
  return DAYS[weekday(iso)]
}
export function monthKey(iso: string) {
  return iso.slice(0, 7)
}
export function monthLabel(key: string) {
  const [y, m] = key.split('-').map(Number)
  return `${MONTHS[m - 1]} ${String(y).slice(2)}`
}
export function monthsBetween(fromKey: string, toKey: string) {
  const out: string[] = []
  let [y, m] = fromKey.split('-').map(Number)
  const [ty, tm] = toKey.split('-').map(Number)
  while (y < ty || (y === ty && m <= tm)) {
    out.push(`${y}-${String(m).padStart(2, '0')}`)
    m++
    if (m > 12) { m = 1; y++ }
  }
  return out
}

// first and last day of a month ('2026-10' -> '2026-10-01' / '2026-10-31')
export function monthStart(key: string) {
  return `${key}-01`
}
export function monthEnd(key: string) {
  const [y, m] = key.split('-').map(Number)
  return toISO(new Date(Date.UTC(y, m, 0)))
}
// 'YYYY-MM' moved by n months
export function addMonths(key: string, n: number) {
  const [y, m] = key.split('-').map(Number)
  const d = new Date(Date.UTC(y, m - 1 + n, 1))
  return toISO(d).slice(0, 7)
}
// the financial year that starts on fyStart, as accountants write it: 2026/27
export function fyLabel(fyStart: string) {
  const y = Number(fyStart.slice(0, 4))
  return `${y}/${String(y + 1).slice(2)}`
}
