import { Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatLKR } from '@/lib/format'
import { addDays, diffDays } from '@/lib/dates'
import { cn } from '@/lib/utils'
import { AmountInput, DatePicker } from './form'
import { Money } from './money'

export interface ScheduleRow { key: string; id?: string; description: string; dueDate: string; amount: number | ''; billed?: boolean; locked?: boolean }

let k = 0
export const newRowKey = () => `r${++k}`

/** Editable installment schedule. Rows already billed or paid are locked. */
export function ScheduleEditor({ rows, onChange, contractValue, showErrors }: { rows: ScheduleRow[]; onChange: (r: ScheduleRow[]) => void; contractValue: number; showErrors?: boolean }) {
  const total = rows.reduce((t, r) => t + (typeof r.amount === 'number' ? r.amount : 0), 0)
  const diff = Math.round((contractValue - total) * 100) / 100
  const set = (key: string, patch: Partial<ScheduleRow>) => onChange(rows.map((r) => (r.key === key ? { ...r, ...patch } : r)))
  return (
    <div className="grid gap-3">
      <div className="overflow-hidden rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="pl-4">Installment</TableHead>
              <TableHead className="w-44">Planned date</TableHead>
              <TableHead className="w-24 text-right">Share</TableHead>
              <TableHead className="w-52 text-right">Amount</TableHead>
              <TableHead className="w-12"><span className="sr-only">Remove</span></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((r) => (
              <TableRow key={r.key} className="hover:bg-transparent">
                <TableCell className="pl-4">
                  <Input value={r.description} disabled={r.locked} onChange={(e) => set(r.key, { description: e.target.value })} aria-label="Installment description" aria-invalid={(showErrors && !r.description.trim()) || undefined} placeholder="e.g. Foundation complete" />
                </TableCell>
                <TableCell><DatePicker value={r.dueDate} disabled={r.locked} onChange={(v) => set(r.key, { dueDate: v })} invalid={showErrors && !r.dueDate} /></TableCell>
                <TableCell className="text-muted-foreground tabular text-right">{contractValue > 0 && typeof r.amount === 'number' ? `${((r.amount / contractValue) * 100).toFixed(1)}%` : '—'}</TableCell>
                <TableCell><AmountInput value={r.amount} disabled={r.locked} onChange={(v) => set(r.key, { amount: v })} invalid={showErrors && !(typeof r.amount === 'number' && r.amount > 0)} /></TableCell>
                <TableCell className="pr-2">
                  <Button variant="ghost" size="icon" className="size-8" disabled={r.locked || rows.length <= 1} aria-label="Remove installment" title={r.locked ? 'Billed installments cannot be removed' : undefined} onClick={() => onChange(rows.filter((x) => x.key !== r.key))}><Trash2 /></Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
          <TableFooter>
            <TableRow className="hover:bg-transparent">
              <TableCell className="pl-4">Schedule total</TableCell>
              <TableCell />
              <TableCell className="tabular text-right">{contractValue > 0 ? `${((total / contractValue) * 100).toFixed(1)}%` : ''}</TableCell>
              <TableCell className="pr-4 text-right"><Money value={total} /></TableCell>
              <TableCell />
            </TableRow>
          </TableFooter>
        </Table>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button variant="outline" size="sm" onClick={() => onChange([...rows, { key: newRowKey(), description: '', dueDate: '', amount: diff > 0 ? diff : '' }])}><Plus />Add installment</Button>
        <p className={cn('text-sm', diff === 0 ? 'text-success' : 'text-destructive')} role="status">
          {contractValue <= 0 ? 'Enter the contract value to check the schedule.' : diff === 0 ? 'Schedule matches the contract value.' : diff > 0 ? `${formatLKR(diff)} of the contract value is not scheduled yet.` : `Schedule is ${formatLKR(-diff)} more than the contract value.`}
        </p>
      </div>
    </div>
  )
}

export function scheduleTemplate(type: 'Design' | 'Construction' | 'Consultation', value: number, start: string, end: string): ScheduleRow[] {
  const v = value || 0
  const split = (parts: [string, number][]) => {
    let used = 0
    return parts.map(([d, pct], i) => {
      const amt = i === parts.length - 1 ? Math.round((v - used) * 100) / 100 : Math.round(v * pct) / 100
      used += amt
      let due = i === 0 ? start : i === parts.length - 1 ? end : ''
      if (!due && start && end && end > start) {
        // spread intermediate installments evenly between start and expected completion
        const span = diffDays(end, start)
        due = addDays(start, Math.round((span * i) / (parts.length - 1)))
      }
      return { key: newRowKey(), description: d, dueDate: due, amount: v ? amt : ('' as const) }
    })
  }
  if (type === 'Construction') return split([['Advance (20%)', 20], ['Foundation complete (20%)', 20], ['Structure & roof (25%)', 25], ['Finishing (20%)', 20], ['Completion & handover (15%)', 15]])
  if (type === 'Design') return split([['Design advance (50%)', 50], ['Design fee balance (50%)', 50]])
  return split([['Consultation fee', 100]])
}
