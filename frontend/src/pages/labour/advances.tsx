import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Info, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { KpiStrip } from '@/components/app/kpi-strip'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { CellMain } from '@/components/app/data-table'
import { AmountInput, DatePicker, Field, SelectField } from '@/components/app/form'
import { useData, useStore } from '@/store/store'
import { sum } from '@/lib/finance'
import { addDays, formatDate, formatRange, TODAY } from '@/lib/dates'
import { formatLKR } from '@/lib/format'
import { errorMessage } from '@/lib/api'

export function AdvancesView() {
  const d = useData()
  const [open, setOpen] = useState(false)
  const rows = d.advances
  return (
    <>
      <div className="bg-muted/60 text-muted-foreground flex items-start gap-3 rounded-lg px-4 py-3 text-sm">
        <Info className="mt-0.5 size-4 shrink-0" />
        <span>An advance is wages paid early. It is deducted from that week's net pay and is <strong className="text-foreground">not</strong> a second labour cost — the wages themselves are the cost.</span>
      </div>
      <KpiStrip items={[
        { label: 'Advances given (Oct)', unit: 'LKR', value: <Money value={sum(rows.filter((a) => a.advance.date >= '2026-10-01'), (a) => a.advance.amount)} decimals={0} currency={false} />, caption: `${rows.filter((a) => a.advance.date >= '2026-10-01').length} advances` },
        { label: 'Deducted from paid wages', unit: 'LKR', value: <Money value={sum(rows, (a) => a.deducted)} decimals={0} currency={false} />, caption: 'In pay runs already paid' },
        { label: 'To deduct this week', unit: 'LKR', value: <Money value={sum(rows, (a) => a.scheduled)} decimals={0} currency={false} />, caption: 'In the current payroll' },
        { label: 'Not yet covered', unit: 'LKR', value: <Money value={sum(rows, (a) => a.remaining)} decimals={0} currency={false} />, caption: 'Larger than wages earned so far' },
      ]} />
      <div className="flex justify-end"><Button onClick={() => setOpen(true)}><Plus />Record Advance</Button></div>
      <Card className="gap-0 py-0">
        <Table>
          <TableHeader><TableRow><TableHead className="pl-6">Date</TableHead><TableHead>Worker</TableHead><TableHead className="hidden md:table-cell">Method</TableHead><TableHead className="text-right">Advance (LKR)</TableHead><TableHead className="hidden text-right sm:table-cell">Deducted (LKR)</TableHead><TableHead className="hidden text-right sm:table-cell">This week (LKR)</TableHead><TableHead className="text-right">Remaining (LKR)</TableHead><TableHead className="pr-6">Status</TableHead></TableRow></TableHeader>
          <TableBody>
            {rows.map((a) => (
              <TableRow key={a.advance.id}>
                <TableCell className="pl-6">{formatDate(a.advance.date)}</TableCell>
                <TableCell className="max-w-56"><CellMain title={a.employee.name} sub={a.advance.notes ?? a.employee.role} /></TableCell>
                <TableCell className="hidden md:table-cell">{a.advance.method}</TableCell>
                <TableCell className="text-right font-medium"><Money value={a.advance.amount} currency={false} /></TableCell>
                <TableCell className="hidden text-right sm:table-cell"><Money value={a.deducted} currency={false} />{a.allocations.filter((x) => x.settled).map((x) => <div key={x.weekStart} className="text-muted-foreground text-xs">week {formatRange(x.weekStart, addDays(x.weekStart, 5))}</div>)}</TableCell>
                <TableCell className="hidden text-right sm:table-cell">{a.scheduled ? <Money value={a.scheduled} currency={false} /> : <span className="text-muted-foreground">—</span>}</TableCell>
                <TableCell className="text-right">{a.remaining ? <Money value={a.remaining} currency={false} /> : <span className="text-muted-foreground">—</span>}</TableCell>
                <TableCell className="pr-6"><StatusBadge status={a.status} /></TableCell>
              </TableRow>
            ))}
          </TableBody>
          <TableFooter><TableRow><TableCell className="pl-6" colSpan={3}>{rows.length} advances</TableCell><TableCell className="text-right"><Money value={sum(rows, (a) => a.advance.amount)} currency={false} /></TableCell><TableCell className="hidden text-right sm:table-cell"><Money value={sum(rows, (a) => a.deducted)} currency={false} /></TableCell><TableCell className="hidden text-right sm:table-cell"><Money value={sum(rows, (a) => a.scheduled)} currency={false} /></TableCell><TableCell className="text-right"><Money value={sum(rows, (a) => a.remaining)} currency={false} /></TableCell><TableCell /></TableRow></TableFooter>
        </Table>
      </Card>
      <AdvanceDialog open={open} onOpenChange={setOpen} />
    </>
  )
}

function AdvanceDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const d = useData()
  const addAdvance = useStore((s) => s.addAdvance)
  const [employeeId, setEmployeeId] = useState('')
  const [date, setDate] = useState(TODAY)
  const [amount, setAmount] = useState<number | ''>('')
  const [method, setMethod] = useState('Cash')
  const [notes, setNotes] = useState('')
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  const [requestId, setRequestId] = useState(() => crypto.randomUUID())
  useEffect(() => { if (open) { setRequestId(crypto.randomUUID()); setEmployeeId(''); setDate(TODAY); setAmount(''); setMethod('Cash'); setNotes(''); setTried(false) } }, [open])
  const week = d.payroll[d.payroll.length - 1]
  const line = week?.lines.find((l) => l.employee.id === employeeId)
  const amt = typeof amount === 'number' ? amount : 0
  const err = { emp: !employeeId ? 'Choose the worker.' : undefined, amount: !(amt > 0) ? 'Enter the amount given.' : undefined }
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader><DialogTitle>Record salary advance</DialogTitle><DialogDescription>Workers can take an advance on any day before payday. It is deducted from the weekly payroll automatically.</DialogDescription></DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Worker" required error={tried ? err.emp : undefined} className="sm:col-span-2"><SelectField value={employeeId} onChange={setEmployeeId} invalid={tried && !!err.emp} placeholder="Select worker" options={d.s.employees.filter((e) => e.status === 'Active').map((e) => ({ value: e.id, label: `${e.name} — ${e.role}` }))} /></Field>
          <Field label="Amount given" required error={tried ? err.amount : undefined}><AmountInput value={amount} onChange={setAmount} invalid={tried && !!err.amount} /></Field>
          <Field label="Date" required><DatePicker value={date} onChange={setDate} /></Field>
          <Field label="Payment method"><SelectField value={method} onChange={setMethod} options={d.s.settings.paymentMethods} /></Field>
          <Field label="Notes" optional className="sm:col-span-2"><Textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
        </div>
        {line && (
          <p className={`rounded-md p-3 text-sm ${amt > line.net ? 'bg-warning-soft text-warning' : 'bg-muted/60'}`}>
            This week so far: {formatLKR(line.gross)} earned, {formatLKR(line.advanceDeduction)} already to be deducted. {amt > line.net ? `${formatLKR(amt - line.net)} would carry over to next week's payroll.` : `Net pay after this advance: ${formatLKR(line.net - amt)}.`}
          </p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={busy} onClick={async () => {
            setTried(true)
            if (err.emp || err.amount) return
            setBusy(true)
            try {
              await addAdvance({ requestId, employeeId, date, amount: amt, method, notes: notes || undefined })
              toast.success('Advance recorded', { description: `${formatLKR(amt)} to ${d.empById.get(employeeId)?.name} — will be deducted on payday.` })
              onOpenChange(false)
            } catch (e) {
              toast.error('Advance not recorded', { description: errorMessage(e) })
            } finally {
              setBusy(false)
            }
          }}>Record {amt ? formatLKR(amt) : 'advance'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
