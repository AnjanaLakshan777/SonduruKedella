import { Search, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { DatePicker } from './form'
import { useStore } from '@/store/store'
import { addMonths, fyLabel, monthEnd, monthLabel, monthStart, TODAY } from '@/lib/dates'

export function SearchInput({ value, onChange, placeholder = 'Search…', className }: { value: string; onChange: (v: string) => void; placeholder?: string; className?: string }) {
  return (
    <label className={cn('border-input dark:bg-input/30 focus-within:border-ring focus-within:ring-ring/50 text-muted-foreground flex h-8 w-full items-center gap-2 rounded-md border bg-transparent px-2.5 shadow-xs focus-within:ring-[3px] sm:w-64', className)}>
      <Search className="size-4 shrink-0" />
      <input className="text-foreground placeholder:text-muted-foreground w-full min-w-0 bg-transparent text-sm outline-none" placeholder={placeholder} value={value} onChange={(e) => onChange(e.target.value)} aria-label={placeholder} />
      {value && <button type="button" onClick={() => onChange('')} aria-label="Clear search" className="hover:text-foreground"><X className="size-3.5" /></button>}
    </label>
  )
}

export const ALL = '__all'

export function FilterSelect({ value, onChange, label, options, className }: { value: string; onChange: (v: string) => void; label: string; options: (string | { value: string; label: string })[]; className?: string }) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger size="sm" className={cn('w-auto min-w-36', value !== ALL && 'border-primary-text/40 text-foreground', className)} aria-label={label}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={ALL}>All {label.toLowerCase()}</SelectItem>
        {options.map((o) => {
          const v = typeof o === 'string' ? o : o.value
          return <SelectItem key={v} value={v}>{typeof o === 'string' ? o : o.label}</SelectItem>
        })}
      </SelectContent>
    </Select>
  )
}

export function FilterBar({ children, onClear, active }: { children: React.ReactNode; onClear?: () => void; active?: boolean }) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {children}
      {active && onClear && <Button variant="ghost" size="sm" onClick={onClear}><X />Clear filters</Button>}
    </div>
  )
}

export interface DateRange { from: string; to: string; preset: string }
// Worked out from today and the financial year start in Settings; only quarters that have begun.
export function rangePresets(): { value: string; label: string; from: string; to: string }[] {
  const fy = useStore.getState().settings.financialYearStart
  const fyMonth = fy.slice(0, 7)
  const month = TODAY.slice(0, 7)
  const short = (key: string) => monthLabel(key).split(' ')[0]
  const quarters = [0, 1, 2, 3].map((i) => {
    const first = addMonths(fyMonth, i * 3), last = addMonths(first, 2)
    return { value: `q${i + 1}`, label: `Q${i + 1} (${short(first)}–${monthLabel(last)})`, from: monthStart(first), to: monthEnd(last) }
  })
  return [
    { value: 'fy', label: `This financial year (${fyLabel(fy)})`, from: fy, to: monthEnd(addMonths(fyMonth, 11)) },
    { value: 'month', label: `This month (${monthLabel(month)})`, from: monthStart(month), to: monthEnd(month) },
    { value: 'last3', label: 'Last 3 months', from: monthStart(addMonths(month, -2)), to: monthEnd(month) },
    ...quarters.filter((q) => q.from <= TODAY),
  ]
}
export const defaultRange = (): DateRange => ({ ...rangePresets()[0], preset: 'fy' })
export const inRange = (date: string, r: DateRange) => date >= r.from && date <= r.to

export function DateRangeFilter({ value, onChange }: { value: DateRange; onChange: (r: DateRange) => void }) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Select value={value.preset} onValueChange={(v) => { const p = rangePresets().find((x) => x.value === v); onChange(p ? { from: p.from, to: p.to, preset: v } : { ...value, preset: 'custom' }) }}>
        <SelectTrigger size="sm" className={cn('w-auto min-w-44', value.preset !== 'fy' && 'border-primary-text/40')} aria-label="Date range"><SelectValue /></SelectTrigger>
        <SelectContent>
          {rangePresets().map((p) => <SelectItem key={p.value} value={p.value}>{p.label}</SelectItem>)}
          <SelectItem value="custom">Custom range…</SelectItem>
        </SelectContent>
      </Select>
      {value.preset === 'custom' && (
        <div className="flex items-center gap-1.5">
          <DatePicker value={value.from} onChange={(v) => onChange({ ...value, from: v })} className="h-8 w-36" />
          <span className="text-muted-foreground text-sm">to</span>
          <DatePicker value={value.to} onChange={(v) => onChange({ ...value, to: v })} className="h-8 w-36" invalid={value.to < value.from} />
        </div>
      )}
    </div>
  )
}
