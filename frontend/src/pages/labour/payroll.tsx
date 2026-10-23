import { useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Calculator, ChevronLeft, ChevronRight, HandCoins, Pencil, Wallet } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from '@/components/ui/alert-dialog'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { KpiStrip } from '@/components/app/kpi-strip'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { CellMain } from '@/components/app/data-table'
import { FigureList } from '@/components/app/figure-list'
import { AmountInput, DatePicker, Field, SelectField } from '@/components/app/form'
import { PaymentDialog } from '@/components/app/payment-dialog'
import { useData, useStore } from '@/store/store'
import type { PayrollLine } from '@/lib/finance'
import { formatDate, formatRange, TODAY } from '@/lib/dates'
import { formatLKR } from '@/lib/format'

export function PayrollView() {
  const d = useData()
  const { recordPayrollPayments, setPayrollAdjustment } = useStore()
  const weeks = d.payroll
  const defaultIdx = Math.max(0, weeks.findIndex((w) => w.status === 'Partially Paid' || w.status === 'Ready to Pay'))
  const [idx, setIdx] = useState(defaultIdx >= 0 ? defaultIdx : weeks.length - 1)
  const w = weeks[Math.min(idx, weeks.length - 1)]
  const [inspect, setInspect] = useState<PayrollLine | undefined>()
  const [payFor, setPayFor] = useState<PayrollLine | undefined>()
  const [adjFor, setAdjFor] = useState<PayrollLine | undefined>()
  const [payAll, setPayAll] = useState(false)
  const [payDate, setPayDate] = useState(TODAY)
  const [payMethod, setPayMethod] = useState('Cash')
  const liveInspect = inspect ? w.lines.find((l) => l.employee.id === inspect.employee.id) : undefined
  const unpaid = w.lines.filter((l) => l.remaining > 0)

  return (
    <>
      <Card className="gap-4">
        <CardHeader>
          <CardTitle>Week {formatRange(w.weekStart, w.weekEnd)}</CardTitle>
          <CardDescription>{w.closed ? `Payday ${formatDate(w.weekEnd)}` : 'Current week — attendance still being recorded; figures update daily'}</CardDescription>
          <CardAction className="flex items-center gap-2">
            <StatusBadge status={w.status} />
            <Button variant="outline" size="icon" className="size-8" aria-label="Previous week" disabled={idx === 0} onClick={() => setIdx(idx - 1)}><ChevronLeft /></Button>
            <SelectField size="sm" value={String(idx)} onChange={(v) => setIdx(Number(v))} className="w-48" options={weeks.map((x, i) => ({ value: String(i), label: formatRange(x.weekStart, x.weekEnd) })).reverse()} />
            <Button variant="outline" size="icon" className="size-8" aria-label="Next week" disabled={idx >= weeks.length - 1} onClick={() => setIdx(idx + 1)}><ChevronRight /></Button>
          </CardAction>
        </CardHeader>
        <CardContent className="bg-muted/50 mx-6 grid gap-1 rounded-md p-3 text-[13px]">
          <div className="flex items-center gap-2 font-medium"><Calculator className="size-4" />How this is calculated</div>
          <div className="text-muted-foreground grid gap-0.5 sm:grid-cols-3">
            <span>Base wages = attendance days × daily rate</span>
            <span>Overtime = approved hours × hourly rate</span>
            <span>Net = gross − advances − approved deductions</span>
          </div>
        </CardContent>
      </Card>

      <KpiStrip items={[
        { label: 'Gross wages', unit: 'LKR', value: <Money value={w.gross} decimals={0} currency={false} />, basis: 'Recorded', caption: `${w.lines.length} workers · labour cost` },
        { label: 'Advances deducted', unit: 'LKR', value: <Money value={w.advances} decimals={0} currency={false} />, caption: 'Already paid out — not a new cost' },
        { label: 'Net payable', unit: 'LKR', value: <Money value={w.net} decimals={0} currency={false} />, caption: w.other ? `after ${formatLKR(w.other, { decimals: 0 })} other deductions` : 'To hand over on payday' },
        { label: 'Paid', unit: 'LKR', value: <Money value={w.paid} decimals={0} currency={false} />, basis: 'Recorded' },
        { label: 'Remaining', unit: 'LKR', value: <Money value={w.remaining} decimals={0} currency={false} />, tone: w.closed && w.remaining > 0 ? 'destructive' : 'default', caption: unpaid.length ? `${unpaid.length} workers` : 'Fully paid' },
      ]} />

      <Card className="gap-4 pb-0">
        <CardHeader>
          <CardTitle>Payroll</CardTitle>
          <CardDescription>Click a worker to inspect the calculation. Pending overtime is not included until approved.</CardDescription>
          <CardAction><Button disabled={!unpaid.length || !w.closed} title={!w.closed ? 'Available once the week has ended' : undefined} onClick={() => setPayAll(true)}><Wallet />Pay remaining · {formatLKR(w.remaining, { decimals: 0 })}</Button></CardAction>
        </CardHeader>
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="pl-6">Worker</TableHead>
                <TableHead className="text-right">Days</TableHead>
                <TableHead className="hidden text-right md:table-cell">Base (LKR)</TableHead>
                <TableHead className="hidden text-right 2xl:table-cell">OT hrs</TableHead>
                <TableHead className="hidden text-right lg:table-cell">OT pay (LKR)</TableHead>
                <TableHead className="text-right">Gross (LKR)</TableHead>
                <TableHead className="hidden text-right sm:table-cell">Advances (LKR)</TableHead>
                {w.other > 0 && <TableHead className="hidden text-right xl:table-cell">Other (LKR)</TableHead>}
                <TableHead className="text-right">Net (LKR)</TableHead>
                <TableHead className="hidden text-right xl:table-cell">Paid (LKR)</TableHead>
                <TableHead className="text-right">Remaining (LKR)</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="pr-4"><span className="sr-only">Actions</span></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {w.lines.map((l) => (
                <TableRow key={l.employee.id} className="cursor-pointer" onClick={() => setInspect(l)}>
                  <TableCell className="min-w-44 pl-6"><CellMain title={l.employee.name} sub={`${l.employee.role} · ${formatLKR(l.employee.dailyRate, { decimals: 0 })}/day`} /></TableCell>
                  <TableCell className="tabular text-right">{l.days}{l.halfDays ? <span className="text-muted-foreground block text-xs">{l.halfDays} half</span> : null}</TableCell>
                  <TableCell className="hidden text-right md:table-cell"><Money value={l.baseWages} currency={false} decimals={0} /></TableCell>
                  <TableCell className="tabular hidden text-right 2xl:table-cell">{l.otHours || '—'}{l.otPendingHours ? <span className="text-warning block text-xs">+{l.otPendingHours} pending</span> : null}</TableCell>
                  <TableCell className="hidden text-right lg:table-cell">{l.otPay ? <Money value={l.otPay} currency={false} decimals={0} /> : '—'}</TableCell>
                  <TableCell className="text-right"><Money value={l.gross} currency={false} decimals={0} /></TableCell>
                  <TableCell className="hidden text-right sm:table-cell">{l.advanceDeduction ? <Money value={-l.advanceDeduction} currency={false} decimals={0} /> : '—'}</TableCell>
                  {w.other > 0 && <TableCell className="hidden text-right xl:table-cell">{l.otherDeduction ? <Money value={-l.otherDeduction} currency={false} decimals={0} /> : '—'}</TableCell>}
                  <TableCell className="text-right font-semibold"><Money value={l.net} currency={false} decimals={0} /></TableCell>
                  <TableCell className="hidden text-right xl:table-cell">{l.paid ? <Money value={l.paid} currency={false} decimals={0} /> : '—'}</TableCell>
                  <TableCell className="text-right font-medium">{l.remaining ? <Money value={l.remaining} currency={false} decimals={0} /> : <span className="text-muted-foreground font-normal">—</span>}</TableCell>
                  <TableCell><StatusBadge status={l.status} /></TableCell>
                  <TableCell className="pr-4" onClick={(e) => e.stopPropagation()}><Button size="sm" variant="outline" disabled={l.remaining <= 0} onClick={() => setPayFor(l)}><HandCoins />Pay</Button></TableCell>
                </TableRow>
              ))}
            </TableBody>
            <TableFooter>
              <TableRow>
                <TableCell className="pl-6">Total</TableCell>
                <TableCell className="tabular text-right">{w.lines.reduce((t, l) => t + l.days, 0)}</TableCell>
                <TableCell className="hidden text-right md:table-cell"><Money value={w.lines.reduce((t, l) => t + l.baseWages, 0)} currency={false} decimals={0} /></TableCell>
                <TableCell className="tabular hidden text-right 2xl:table-cell">{w.lines.reduce((t, l) => t + l.otHours, 0)}</TableCell>
                <TableCell className="hidden text-right lg:table-cell"><Money value={w.lines.reduce((t, l) => t + l.otPay, 0)} currency={false} decimals={0} /></TableCell>
                <TableCell className="text-right"><Money value={w.gross} currency={false} decimals={0} /></TableCell>
                <TableCell className="hidden text-right sm:table-cell"><Money value={-w.advances} currency={false} decimals={0} /></TableCell>
                {w.other > 0 && <TableCell className="hidden text-right xl:table-cell"><Money value={-w.other} currency={false} decimals={0} /></TableCell>}
                <TableCell className="text-right"><Money value={w.net} currency={false} decimals={0} /></TableCell>
                <TableCell className="hidden text-right xl:table-cell"><Money value={w.paid} currency={false} decimals={0} /></TableCell>
                <TableCell className="text-right"><Money value={w.remaining} currency={false} decimals={0} /></TableCell>
                <TableCell colSpan={2} />
              </TableRow>
            </TableFooter>
          </Table>
        </div>
      </Card>

      <Sheet open={!!liveInspect} onOpenChange={(o) => !o && setInspect(undefined)}>
        <SheetContent className="overflow-y-auto">
          {liveInspect && (
            <>
              <SheetHeader><SheetTitle>{liveInspect.employee.name}</SheetTitle><SheetDescription>Week {formatRange(w.weekStart, w.weekEnd)} · {liveInspect.employee.role}</SheetDescription></SheetHeader>
              <div className="grid gap-5 px-6 pb-6">
                <FigureList rows={[
                  { label: 'Base wages', value: liveInspect.baseWages, note: `${liveInspect.presentDays} present + ${liveInspect.halfDays} × ½ = ${liveInspect.days} days × ${formatLKR(liveInspect.employee.dailyRate)} (${liveInspect.absentDays} absent)` },
                  { label: 'Overtime pay', value: liveInspect.otPay, note: `${liveInspect.otHours} approved hours${liveInspect.otPendingHours ? ` · ${liveInspect.otPendingHours} h pending approval not included` : ''}` },
                  { kind: 'total', label: 'Gross wages', value: liveInspect.gross },
                  { kind: 'less', label: 'Salary advances', value: liveInspect.advanceDeduction, note: liveInspect.advancesOutstanding > liveInspect.advanceDeduction ? `${formatLKR(liveInspect.advancesOutstanding - liveInspect.advanceDeduction)} carries to next week` : 'Money already given — not a new expense' },
                  ...(liveInspect.otherDeduction ? [{ kind: 'less' as const, label: 'Other approved deductions', value: liveInspect.otherDeduction, note: liveInspect.otherNote }] : []),
                  { kind: 'total', label: 'Net payable', value: liveInspect.net },
                  { kind: 'less', label: 'Paid', value: liveInspect.paid },
                  { kind: 'highlight', label: 'Remaining payable', value: liveInspect.remaining },
                ]} />
                <div className="grid gap-2">
                  <div className="text-sm font-medium">Labour cost by project</div>
                  {Object.entries(liveInspect.byProject).map(([pid, v]) => <div key={pid} className="flex justify-between text-sm"><span>{d.projectById.get(pid)?.code} · {d.projectById.get(pid)?.name}</span><Money value={v} /></div>)}
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button disabled={liveInspect.remaining <= 0} onClick={() => setPayFor(liveInspect)}><HandCoins />Record payment</Button>
                  <Button variant="outline" disabled={liveInspect.status === 'Paid'} onClick={() => setAdjFor(liveInspect)}><Pencil />Adjust deductions</Button>
                </div>
              </div>
            </>
          )}
        </SheetContent>
      </Sheet>

      <PaymentDialog
        open={!!payFor} onOpenChange={(o) => !o && setPayFor(undefined)} direction="out" successTitle="Wages paid" allowNotes={false}
        title={`Pay ${payFor?.employee.name ?? ''}`} description={`Week ${formatRange(w.weekStart, w.weekEnd)}`}
        context={payFor ? [{ label: 'Net payable', value: payFor.net }, { kind: 'less', label: 'Already paid', value: payFor.paid }, { kind: 'total', label: 'Remaining', value: payFor.remaining }] : undefined}
        outstanding={payFor?.remaining ?? 0}
        onSubmit={(p) => payFor && recordPayrollPayments([{ weekStart: w.weekStart, employeeId: payFor.employee.id, date: p.date, amount: p.amount, method: p.method }])}
      />
      <AdjustDialog line={adjFor} weekStart={w.weekStart} onClose={() => setAdjFor(undefined)} onSave={(a) => { setPayrollAdjustment(a); toast.success('Deductions updated', { description: 'Net pay recalculated.' }) }} />
      <AlertDialog open={payAll} onOpenChange={setPayAll}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Pay {formatLKR(w.remaining)} to {unpaid.length} workers?</AlertDialogTitle>
            <AlertDialogDescription>Records the remaining net pay for the week {formatRange(w.weekStart, w.weekEnd)}. Check each worker's calculation first if anything changed.</AlertDialogDescription>
          </AlertDialogHeader>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Payment date"><DatePicker value={payDate} onChange={setPayDate} /></Field>
            <Field label="Method"><SelectField value={payMethod} onChange={setPayMethod} options={d.s.settings.paymentMethods} /></Field>
          </div>
          <FigureList rows={unpaid.map((l) => ({ label: l.employee.name, value: l.remaining }))} />
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={() => { recordPayrollPayments(unpaid.map((l) => ({ weekStart: w.weekStart, employeeId: l.employee.id, date: payDate, amount: l.remaining, method: payMethod }))); toast.success('Payroll paid', { description: `${formatLKR(w.remaining)} to ${unpaid.length} workers.` }) }}>Record payments</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}

function AdjustDialog({ line, weekStart, onClose, onSave }: { line?: PayrollLine; weekStart: string; onClose: () => void; onSave: (a: { weekStart: string; employeeId: string; advanceDeduction: number; otherDeduction: number; otherNote?: string }) => void }) {
  const [adv, setAdv] = useState<number | ''>('')
  const [other, setOther] = useState<number | ''>('')
  const [note, setNote] = useState('')
  const [tried, setTried] = useState(false)
  useEffect(() => { if (line) { setAdv(line.advanceDeduction); setOther(line.otherDeduction || ''); setNote(line.otherNote ?? ''); setTried(false) } }, [line])
  const a = Number(adv) || 0, o = Number(other) || 0
  const err = !line ? undefined : a > line.advancesOutstanding ? `Only ${formatLKR(line.advancesOutstanding)} in advances is outstanding.` : a + o > line.gross ? 'Deductions cannot exceed gross wages.' : o > 0 && !note.trim() ? 'Explain the other deduction.' : undefined
  const net = useMemo(() => (line ? line.gross - a - o : 0), [line, a, o])
  return (
    <Dialog open={!!line} onOpenChange={(x) => !x && onClose()}>
      <DialogContent>
        <DialogHeader><DialogTitle>Adjust deductions — {line?.employee.name}</DialogTitle><DialogDescription>Use when part of an advance should carry to next week, or for a documented deduction (e.g. a damaged tool agreed with the worker).</DialogDescription></DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Advance deduction" hint={line ? `${formatLKR(line.advancesOutstanding)} outstanding` : undefined}><AmountInput value={adv} onChange={setAdv} /></Field>
          <Field label="Other approved deduction" optional><AmountInput value={other} onChange={setOther} /></Field>
          <Field label="Reason for other deduction" className="sm:col-span-2" optional={!o}><Input value={note} onChange={(e) => setNote(e.target.value)} /></Field>
        </div>
        <div className="bg-muted/60 flex justify-between rounded-md px-3 py-2 text-sm"><span className="text-muted-foreground">Net payable</span><Money value={net} className="font-semibold" /></div>
        {tried && err && <p className="text-destructive text-sm">{err}</p>}
        <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={() => { setTried(true); if (err || !line) return; onSave({ weekStart, employeeId: line.employee.id, advanceDeduction: a, otherDeduction: o, otherNote: note || undefined }); onClose() }}>Save deductions</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
