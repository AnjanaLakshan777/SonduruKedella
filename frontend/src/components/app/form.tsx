import { useId, useState } from 'react'
import { CalendarIcon, CircleAlert } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Calendar } from '@/components/ui/calendar'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { formatDate, toDate, toISO } from '@/lib/dates'
import { formatNumber } from '@/lib/format'

export function Field({
  label, required, optional, hint, error, children, className, htmlFor,
}: { label: React.ReactNode; required?: boolean; optional?: boolean; hint?: React.ReactNode; error?: React.ReactNode; children: React.ReactNode; className?: string; htmlFor?: string }) {
  return (
    <div className={cn('grid min-w-0 grid-cols-[minmax(0,1fr)] content-start gap-2', className)}>
      <div className="flex items-baseline justify-between gap-2">
        <Label htmlFor={htmlFor}>
          {label}
          {required && <span className="text-destructive -ml-1" aria-hidden>*</span>}
        </Label>
        {optional && <span className="text-muted-foreground text-xs">Optional</span>}
      </div>
      {children}
      {error ? (
        <p className="text-destructive flex items-center gap-1.5 text-xs" role="alert"><CircleAlert className="size-3.5 shrink-0" />{error}</p>
      ) : hint ? (
        <p className="text-muted-foreground text-xs">{hint}</p>
      ) : null}
    </div>
  )
}

/** LKR amount input: shows grouping on blur, emits a number (NaN when empty). */
export function AmountInput({ value, onChange, invalid, id, placeholder = '0.00', suffix = 'LKR', className, disabled }: {
  value: number | ''
  onChange: (v: number | '') => void
  invalid?: boolean
  id?: string
  placeholder?: string
  suffix?: string
  className?: string
  disabled?: boolean
}) {
  const [focus, setFocus] = useState(false)
  const [raw, setRaw] = useState('')
  const shown = focus ? raw : value === '' ? '' : formatNumber(value, 2)
  return (
    <div
      className={cn(
        'border-input bg-card dark:bg-input/30 flex h-9 items-center rounded-md border shadow-xs transition-[color,box-shadow]',
        'focus-within:border-ring focus-within:ring-ring/50 focus-within:ring-[3px]',
        invalid && 'border-destructive ring-destructive/20 dark:ring-destructive/40 ring-[3px]',
        disabled && 'opacity-50',
        className,
      )}
    >
      <span className="text-muted-foreground pl-3 text-sm">{suffix}</span>
      <input
        id={id}
        inputMode="decimal"
        disabled={disabled}
        aria-invalid={invalid || undefined}
        className="tabular placeholder:text-muted-foreground h-full w-full min-w-0 bg-transparent px-2 text-right text-sm outline-none"
        placeholder={placeholder}
        value={shown}
        onFocus={() => { setFocus(true); setRaw(value === '' ? '' : String(value)) }}
        onBlur={() => setFocus(false)}
        onChange={(e) => {
          const t = e.target.value.replace(/[^0-9.]/g, '')
          setRaw(t)
          onChange(t === '' ? '' : Number(t))
        }}
      />
    </div>
  )
}

/** Date picker showing DD MMM YYYY (never the browser's locale format). */
export function DatePicker({ value, onChange, invalid, placeholder = 'Pick a date', id, disabled, className }: { value?: string; onChange: (v: string) => void; invalid?: boolean; placeholder?: string; id?: string; disabled?: boolean; className?: string }) {
  const [open, setOpen] = useState(false)
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild disabled={disabled}>
        <button
          id={id}
          type="button"
          aria-invalid={invalid || undefined}
          className={cn(
            'border-input dark:bg-input/30 flex h-9 w-full items-center justify-between gap-2 rounded-md border bg-card px-3 text-left text-sm shadow-xs outline-none',
            'focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px] aria-invalid:border-destructive aria-invalid:ring-destructive/20 disabled:opacity-50',
            !value && 'text-muted-foreground',
            className,
          )}
        >
          {value ? formatDate(value) : placeholder}
          <CalendarIcon className="text-muted-foreground size-4" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar
          mode="single"
          selected={value ? toDate(value) : undefined}
          defaultMonth={value ? toDate(value) : undefined}
          onSelect={(d) => {
            if (d) onChange(toISO(new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()))))
            setOpen(false)
          }}
        />
      </PopoverContent>
    </Popover>
  )
}

export function SelectField({ value, onChange, options, placeholder = 'Select', invalid, id, size, className, disabled }: {
  value?: string
  onChange: (v: string) => void
  options: (string | { value: string; label: React.ReactNode })[]
  placeholder?: string
  invalid?: boolean
  id?: string
  size?: 'sm' | 'default'
  className?: string
  disabled?: boolean
}) {
  return (
    <Select value={value || undefined} onValueChange={onChange} disabled={disabled}>
      <SelectTrigger id={id} size={size} aria-invalid={invalid || undefined} className={className}>
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {options.map((o) => {
          const v = typeof o === 'string' ? o : o.value
          return <SelectItem key={v} value={v}>{typeof o === 'string' ? o : o.label}</SelectItem>
        })}
      </SelectContent>
    </Select>
  )
}

export function useFieldId() {
  return useId()
}

export { Input }
