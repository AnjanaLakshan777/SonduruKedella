import { useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Ban, Calculator, ChevronLeft, ChevronRight, HandCoins, Lock, LockKeyhole, Pencil, Undo2, Wallet } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
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
import { useShallow } from 'zustand/react/shallow'
import { useData, useStore } from '@/store/store'
import { formatDate, formatRange, TODAY } from '@/lib/dates'
import { formatLKR } from '@/lib/format'
import { apiGet, errorMessage } from '@/lib/api'

// What the server says a worker's week comes to. A paid week is frozen there with the rates of the day,
// so these are the real figures - the browser no longer works them out itself.
interface Line {
  employeeId: string
  name: string
  role: string
  dailyWage: number
  status: string
  frozen: boolean
  presentDays: number
  halfDays: number
  absentDays: number | null
  days: number
  baseWages: number
  otHours: number
  otPendingHours: number
  otPay: number
  gross: number
  advancesOutstanding: number
  advanceDeduction: number
  otherDeduction: number
  otherNote?: string
  net: number
  corrections: number
  owed: number
  paid: number
  remaining: number
  blockedReason?: string
  costs: { projectId?: string; amount: number }[]
}
interface Week { weekStart: string; weekEnd: string; payable: boolean; lines: Line[]; gross: number; advances: number; other: number; net: number; corrections: number; paid: number; remaining: number }
interface LinePayment { id: string; date: string; amount: number; method: string; reference?: string; voided: boolean; voidReason?: string; version: number }
interface LineCorrection { id: string; date: string; delta: number; projectId?: string; reason: string; voided: boolean; voidReason?: string; version: number }

const STATUS: Record<string, string> = { DRAFT: 'Draft', NOTHING_TO_PAY: 'Nothing to Pay', READY_TO_PAY: 'Ready to Pay', PARTIALLY_PAID: 'Partially Paid', PAID: 'Paid' }

/* eslint-disable @typescript-eslint/no-explicit-any */
function toWeek(w: any): Week {
  return {
    weekStart: w.weekStart, weekEnd: w.weekEnd, payable: w.payable, gross: w.grossPay, advances: w.advanceDeductions, other: w.otherDeductions,
    net: w.netPay, corrections: w.corrections, paid: w.paid, remaining: w.remaining,
    lines: w.lines.map((l: any): Line => ({
      employeeId: String(l.employeeId), name: l.employee, role: l.role, dailyWage: l.dailyWage, status: STATUS[l.status] ?? l.status, frozen: l.frozen,
      presentDays: l.presentDays, halfDays: l.halfDays, absentDays: l.absentDays, days: l.payableDays, baseWages: l.basePay, otHours: l.overtimeHours,
      otPendingHours: l.pendingOvertimeHours, otPay: l.overtimePay, gross: l.grossPay, advancesOutstanding: l.advancesOutstanding,
      advanceDeduction: l.advanceDeduction, otherDeduction: l.otherDeduction, otherNote: l.otherNote ?? undefined, net: l.netPay,
      corrections: l.corrections, owed: l.owed, paid: l.paid, remaining: l.remaining, blockedReason: l.blockedReason ?? undefined,
      costs: l.costs.map((c: any) => ({ projectId: c.projectId == null ? undefined : String(c.projectId), amount: c.amount })),
    })),
  }
}

// re-read whenever a labour save reloads the store
function useServerWeek(weekStart?: string) {
  const changed = useStore(useShallow((s) => [s.payrollPayments, s.attendance, s.overtime, s.advances, s.payrollAdjustments, s.frozenWeeks]))
  const [week, setWeek] = useState<Week>()
  const [error, setError] = useState<string>()
  useEffect(() => {
    if (!weekStart) return
    let live = true
    apiGet<any>(`/payroll/weeks/${weekStart}`)
      .then((w) => { if (live) { setWeek(toWeek(w)); setError(undefined) } })
      .catch((e) => { if (live) setError(errorMessage(e)) })
    return () => { live = false }
  }, [weekStart, ...changed]) // eslint-disable-line react-hooks/exhaustive-deps
  return { week: week?.weekStart === weekStart ? week : undefined, error }
}

function useLineDetail(weekStart: string, line?: Line) {
  const changed = useStore(useShallow((s) => [s.payrollPayments, s.frozenWeeks]))
  const [detail, setDetail] = useState<{ payments: LinePayment[]; corrections: LineCorrection[] }>()
  useEffect(() => {
    if (!line) return setDetail(undefined)
    let live = true
    apiGet<any>(`/payroll/weeks/${weekStart}/employees/${line.employeeId}`).then((d) => {
      if (!live) return
      setDetail({
        payments: d.payments.map((p: any) => ({ id: String(p.id), date: p.paymentDate, amount: p.amount, method: p.paymentMethod, reference: p.reference ?? undefined, voided: p.voided, voidReason: p.voidReason ?? undefined, version: p.version })),
        corrections: d.corrections.map((c: any) => ({ id: String(c.id), date: c.correctionDate, delta: c.wageDelta, projectId: c.projectId == null ? undefined : String(c.projectId), reason: c.reason, voided: c.voided, voidReason: c.voidReason ?? undefined, version: c.version })),
      })
    }).catch(() => { if (live) setDetail(undefined) })
    return () => { live = false }
  }, [weekStart, line?.employeeId, ...changed]) // eslint-disable-line react-hooks/exhaustive-deps
  return detail
}
/* eslint-enable @typescript-eslint/no-explicit-any */

export function PayrollView() {
  const d = useData()
  const { recordPayrollPayments, setPayrollAdjustment, closePayrollWeek } = useStore()
  // which weeks exist still comes from the local attendance; their figures come from the server
  const weeks = d.payroll
  const defaultIdx = Math.max(0, weeks.findIndex((w) => w.status === 'Partially Paid' || w.status === 'Ready to Pay'))
  const [idx, setIdx] = useState(defaultIdx >= 0 ? defaultIdx : weeks.length - 1)
  const nav = weeks[Math.min(idx, weeks.length - 1)]
  const { week: w, error } = useServerWeek(nav?.weekStart)
  const [inspectId, setInspectId] = useState<string>()
  const [payFor, setPayFor] = useState<Line | undefined>()
  const [adjFor, setAdjFor] = useState<Line | undefined>()
  const [correctFor, setCorrectFor] = useState<Line | undefined>()
  const [payAll, setPayAll] = useState(false)
  const [payDate, setPayDate] = useState(TODAY)
  const [payMethod, setPayMethod] = useState('Cash')
  const inspect = w?.lines.find((l) => l.employeeId === inspectId)
  const unpaid = (w?.lines ?? []).filter((l) => l.remaining > 0 && !l.blockedReason)

  if (!nav) return null
  const weekStatus = !w ? undefined : w.remaining === 0 && w.paid > 0 ? 'Paid' : w.paid > 0 ? 'Partially Paid' : w.payable && w.remaining > 0 ? 'Ready to Pay' : 'Draft'

  async function close(l: Line) {
    try {
      await closePayrollWeek(nav.weekStart, l.employeeId)
      toast.success('Week closed', { description: `${l.name}: the advance recovery is kept and the week is frozen.` })
    } catch (e) {
      toast.error('Week not closed', { description: errorMessage(e) })
    }
  }

  return (
    <>
      <Card className="gap-4">
        <CardHeader>
          <CardTitle>Week {formatRange(nav.weekStart, nav.weekEnd)}</CardTitle>
          <CardDescription>{w?.payable ? `Payday ${formatDate(w.weekEnd)}` : 'Current week — attendance still being recorded; figures update daily'}</CardDescription>
          <CardAction className="flex items-center gap-2">
            {weekStatus && <StatusBadge status={weekStatus} />}
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
          <div className="text-muted-foreground flex items-center gap-1.5"><Lock className="size-3.5" />A worker's week is frozen at its first payment: attendance, overtime and deductions are locked, and changes go in as corrections.</div>
        </CardContent>
      </Card>

      {error && <p className="text-destructive text-sm">Couldn't load this week: {error}</p>}
      {w && (
        <>
          <KpiStrip items={[
            { label: 'Gross wages', unit: 'LKR', value: <Money value={w.gross} decimals={0} currency={false} />, basis: 'Recorded', caption: `${w.lines.length} workers · labour cost` },
            { label: 'Advances deducted', unit: 'LKR', value: <Money value={w.advances} decimals={0} currency={false} />, caption: 'Already paid out — not a new cost' },
            { label: 'Net payable', unit: 'LKR', value: <Money value={w.net + w.corrections} decimals={0} currency={false} />, caption: w.corrections ? `incl. ${formatLKR(w.corrections, { decimals: 0 })} corrections` : w.other ? `after ${formatLKR(w.other, { decimals: 0 })} other deductions` : 'To hand over on payday' },
            { label: 'Paid', unit: 'LKR', value: <Money value={w.paid} decimals={0} currency={false} />, basis: 'Recorded' },
            { label: 'Remaining', unit: 'LKR', value: <Money value={w.remaining} decimals={0} currency={false} />, tone: w.payable && w.remaining > 0 ? 'destructive' : 'default', caption: unpaid.length ? `${unpaid.length} workers` : 'Fully paid' },
          ]} />

          <Card className="gap-4 pb-0">
            <CardHeader>
              <CardTitle>Payroll</CardTitle>
              <CardDescription>Click a worker to inspect the calculation. Pending overtime is not included until approved.</CardDescription>
              <CardAction><Button disabled={!unpaid.length || !w.payable} title={!w.payable ? 'Available once payday has come' : undefined} onClick={() => setPayAll(true)}><Wallet />Pay remaining · {formatLKR(unpaid.reduce((t, l) => t + l.remaining, 0), { decimals: 0 })}</Button></CardAction>
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
                    {w.corrections !== 0 && <TableHead className="hidden text-right xl:table-cell">Corrections (LKR)</TableHead>}
                    <TableHead className="text-right">Net (LKR)</TableHead>
                    <TableHead className="hidden text-right xl:table-cell">Paid (LKR)</TableHead>
                    <TableHead className="text-right">Remaining (LKR)</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="pr-4"><span className="sr-only">Actions</span></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {w.lines.map((l) => (
                    <TableRow key={l.employeeId} className="cursor-pointer" onClick={() => setInspectId(l.employeeId)}>
                      <TableCell className="min-w-44 pl-6"><CellMain title={<span className="flex items-center gap-1.5">{l.name}{l.frozen && <LockKeyhole className="text-muted-foreground size-3.5" aria-label="Frozen" />}</span>} sub={`${l.role} · ${formatLKR(l.dailyWage, { decimals: 0 })}/day`} /></TableCell>
                      <TableCell className="tabular text-right">{l.days}{l.halfDays ? <span className="text-muted-foreground block text-xs">{l.halfDays} half</span> : null}</TableCell>
                      <TableCell className="hidden text-right md:table-cell"><Money value={l.baseWages} currency={false} decimals={0} /></TableCell>
                      <TableCell className="tabular hidden text-right 2xl:table-cell">{l.otHours || '—'}{l.otPendingHours ? <span className="text-warning block text-xs">+{l.otPendingHours} pending</span> : null}</TableCell>
                      <TableCell className="hidden text-right lg:table-cell">{l.otPay ? <Money value={l.otPay} currency={false} decimals={0} /> : '—'}</TableCell>
                      <TableCell className="text-right"><Money value={l.gross} currency={false} decimals={0} /></TableCell>
                      <TableCell className="hidden text-right sm:table-cell">{l.advanceDeduction ? <Money value={-l.advanceDeduction} currency={false} decimals={0} /> : '—'}</TableCell>
                      {w.other > 0 && <TableCell className="hidden text-right xl:table-cell">{l.otherDeduction ? <Money value={-l.otherDeduction} currency={false} decimals={0} /> : '—'}</TableCell>}
                      {w.corrections !== 0 && <TableCell className="hidden text-right xl:table-cell">{l.corrections ? <Money value={l.corrections} currency={false} decimals={0} /> : '—'}</TableCell>}
                      <TableCell className="text-right font-semibold"><Money value={l.owed} currency={false} decimals={0} /></TableCell>
                      <TableCell className="hidden text-right xl:table-cell">{l.paid ? <Money value={l.paid} currency={false} decimals={0} /> : '—'}</TableCell>
                      <TableCell className="text-right font-medium">{l.remaining ? <Money value={l.remaining} currency={false} decimals={0} /> : <span className="text-muted-foreground font-normal">—</span>}</TableCell>
                      <TableCell><StatusBadge status={l.status} title={l.blockedReason} /></TableCell>
                      <TableCell className="pr-4" onClick={(e) => e.stopPropagation()}>
                        {l.status === 'Nothing to Pay' && !l.frozen && w.payable && l.advanceDeduction > 0
                          ? <Button size="sm" variant="outline" onClick={() => close(l)}><Lock />Close</Button>
                          : <Button size="sm" variant="outline" disabled={l.remaining <= 0 || !!l.blockedReason || !w.payable} title={l.blockedReason} onClick={() => setPayFor(l)}><HandCoins />Pay</Button>}
                      </TableCell>
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
                    {w.corrections !== 0 && <TableCell className="hidden text-right xl:table-cell"><Money value={w.corrections} currency={false} decimals={0} /></TableCell>}
                    <TableCell className="text-right"><Money value={w.net + w.corrections} currency={false} decimals={0} /></TableCell>
                    <TableCell className="hidden text-right xl:table-cell"><Money value={w.paid} currency={false} decimals={0} /></TableCell>
                    <TableCell className="text-right"><Money value={w.remaining} currency={false} decimals={0} /></TableCell>
                    <TableCell colSpan={2} />
                  </TableRow>
                </TableFooter>
              </Table>
            </div>
          </Card>
        </>
      )}

      <LineSheet weekStart={nav.weekStart} weekEnd={nav.weekEnd} line={inspect} onClose={() => setInspectId(undefined)} payable={!!w?.payable}
        onPay={setPayFor} onAdjust={setAdjFor} onCorrect={setCorrectFor} onClosed={close} />

      <PaymentDialog
        open={!!payFor} onOpenChange={(o) => !o && setPayFor(undefined)} direction="out" successTitle="Wages paid" allowNotes={false}
        title={`Pay ${payFor?.name ?? ''}`} description={`Week ${formatRange(nav.weekStart, nav.weekEnd)}`}
        context={payFor ? [{ label: 'Net payable', value: payFor.owed }, { kind: 'less', label: 'Already paid', value: payFor.paid }, { kind: 'total', label: 'Remaining', value: payFor.remaining }] : undefined}
        outstanding={payFor?.remaining ?? 0}
        onSubmit={(p) => (payFor ? recordPayrollPayments([{ weekStart: nav.weekStart, employeeId: payFor.employeeId, ...p }]) : undefined)}
      />
      <AdjustDialog line={adjFor} weekStart={nav.weekStart} onClose={() => setAdjFor(undefined)} onSave={async (a) => { await setPayrollAdjustment(a); toast.success('Deductions updated', { description: 'Net pay recalculated.' }) }} />
      <CorrectionDialog line={correctFor} weekStart={nav.weekStart} onClose={() => setCorrectFor(undefined)} />
      <AlertDialog open={payAll} onOpenChange={setPayAll}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Pay {formatLKR(unpaid.reduce((t, l) => t + l.remaining, 0))} to {unpaid.length} workers?</AlertDialogTitle>
            <AlertDialogDescription>Records the remaining net pay for the week {formatRange(nav.weekStart, nav.weekEnd)}. Each worker's week is frozen at its first payment.</AlertDialogDescription>
          </AlertDialogHeader>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Payment date"><DatePicker value={payDate} onChange={setPayDate} /></Field>
            <Field label="Method"><SelectField value={payMethod} onChange={setPayMethod} options={d.s.settings.paymentMethods} /></Field>
          </div>
          <FigureList rows={unpaid.map((l) => ({ label: l.name, value: l.remaining }))} />
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={() => {
              const total = unpaid.reduce((t, l) => t + l.remaining, 0), count = unpaid.length
              recordPayrollPayments(unpaid.map((l) => ({ requestId: crypto.randomUUID(), weekStart: nav.weekStart, employeeId: l.employeeId, date: payDate, amount: l.remaining, method: payMethod })))
                .then(() => toast.success('Payroll paid', { description: `${formatLKR(total)} to ${count} workers.` }))
                // the ones before the failure are paid; the page now shows what's still owed
                .catch((e) => toast.error('Not all payments were recorded', { description: errorMessage(e) }))
            }}>Record payments</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}

function LineSheet({ weekStart, weekEnd, line, payable, onClose, onPay, onAdjust, onCorrect, onClosed }: {
  weekStart: string; weekEnd: string; line?: Line; payable: boolean; onClose: () => void
  onPay: (l: Line) => void; onAdjust: (l: Line) => void; onCorrect: (l: Line) => void; onClosed: (l: Line) => void
}) {
  const d = useData()
  const { voidPayrollPayment, voidPayrollCorrection } = useStore()
  const detail = useLineDetail(weekStart, line)
  const [voiding, setVoiding] = useState<{ kind: 'payment' | 'correction'; id: string; version: number; label: string }>()
  const project = (id?: string) => (id ? `${d.projectById.get(id)?.code ?? ''} · ${d.projectById.get(id)?.name ?? ''}` : 'Company (no project)')
  return (
    <Sheet open={!!line} onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="overflow-y-auto">
        {line && (
          <>
            <SheetHeader>
              <SheetTitle className="flex items-center gap-2">{line.name}{line.frozen && <StatusBadge status="Frozen" />}</SheetTitle>
              <SheetDescription>Week {formatRange(weekStart, weekEnd)} · {line.role}</SheetDescription>
            </SheetHeader>
            <div className="grid gap-5 px-6 pb-6">
              <FigureList rows={[
                { label: 'Base wages', value: line.baseWages, note: `${line.presentDays} present + ${line.halfDays} × ½ = ${line.days} days × ${formatLKR(line.dailyWage)}${line.absentDays != null ? ` (${line.absentDays} absent)` : ''}` },
                { label: 'Overtime pay', value: line.otPay, note: `${line.otHours} approved hours${line.otPendingHours ? ` · ${line.otPendingHours} h pending approval not included` : ''}` },
                { kind: 'total', label: 'Gross wages', value: line.gross },
                { kind: 'less', label: 'Salary advances', value: line.advanceDeduction, note: line.advancesOutstanding > line.advanceDeduction ? `${formatLKR(line.advancesOutstanding - line.advanceDeduction)} carries to next week` : 'Money already given — not a new expense' },
                ...(line.otherDeduction ? [{ kind: 'less' as const, label: 'Other approved deductions', value: line.otherDeduction, note: line.otherNote }] : []),
                ...(line.corrections ? [{ label: 'Corrections', value: line.corrections, note: 'Recorded after the week was paid' }] : []),
                { kind: 'total', label: 'Net payable', value: line.owed },
                { kind: 'less', label: 'Paid', value: line.paid },
                { kind: 'highlight', label: 'Remaining payable', value: line.remaining },
              ]} />
              {line.blockedReason && <p className="bg-warning-soft text-warning rounded-md p-3 text-sm">{line.blockedReason}</p>}
              <div className="grid gap-2">
                <div className="text-sm font-medium">Labour cost by project</div>
                {line.costs.map((c) => <div key={c.projectId ?? 'company'} className="flex justify-between text-sm"><span>{project(c.projectId)}</span><Money value={c.amount} /></div>)}
              </div>
              {!!detail?.payments.length && (
                <div className="grid gap-2">
                  <div className="text-sm font-medium">Payments</div>
                  {detail.payments.map((p) => (
                    <div key={p.id} className={`flex items-center justify-between gap-2 text-sm ${p.voided ? 'text-muted-foreground line-through' : ''}`}>
                      <span>{formatDate(p.date)} · {p.method}{p.reference ? ` · ${p.reference}` : ''}</span>
                      <span className="flex items-center gap-1"><Money value={p.amount} />{!p.voided && <Button size="icon" variant="ghost" className="size-7" aria-label="Void payment" onClick={() => setVoiding({ kind: 'payment', id: p.id, version: p.version, label: `the ${formatLKR(p.amount)} payment of ${formatDate(p.date)}` })}><Ban /></Button>}</span>
                    </div>
                  ))}
                </div>
              )}
              {!!detail?.corrections.length && (
                <div className="grid gap-2">
                  <div className="text-sm font-medium">Corrections</div>
                  {detail.corrections.map((c) => (
                    <div key={c.id} className={`grid gap-0.5 text-sm ${c.voided ? 'text-muted-foreground line-through' : ''}`}>
                      <div className="flex items-center justify-between gap-2">
                        <span>{formatDate(c.date)} · {project(c.projectId)}</span>
                        <span className="flex items-center gap-1"><Money value={c.delta} />{!c.voided && <Button size="icon" variant="ghost" className="size-7" aria-label="Void correction" onClick={() => setVoiding({ kind: 'correction', id: c.id, version: c.version, label: `the ${formatLKR(c.delta)} correction of ${formatDate(c.date)}` })}><Undo2 /></Button>}</span>
                      </div>
                      <span className="text-muted-foreground text-xs">{c.reason}{c.voidReason ? ` — voided: ${c.voidReason}` : ''}</span>
                    </div>
                  ))}
                </div>
              )}
              <div className="flex flex-wrap gap-2">
                <Button disabled={line.remaining <= 0 || !!line.blockedReason || !payable} onClick={() => onPay(line)}><HandCoins />Record payment</Button>
                {line.frozen
                  ? <Button variant="outline" onClick={() => onCorrect(line)}><Pencil />Record correction</Button>
                  : <Button variant="outline" onClick={() => onAdjust(line)}><Pencil />Adjust deductions</Button>}
                {line.status === 'Nothing to Pay' && !line.frozen && payable && line.advanceDeduction > 0 && <Button variant="outline" onClick={() => onClosed(line)}><Lock />Close week</Button>}
              </div>
            </div>
          </>
        )}
      </SheetContent>
      <VoidDialog target={voiding} onClose={() => setVoiding(undefined)} onVoid={async (reason) => {
        if (!voiding || !line) return
        if (voiding.kind === 'payment') await voidPayrollPayment(weekStart, line.employeeId, voiding.id, voiding.version, reason)
        else await voidPayrollCorrection(weekStart, line.employeeId, voiding.id, voiding.version, reason)
      }} />
    </Sheet>
  )
}

// voiding keeps the record with its reason; it just stops counting
function VoidDialog({ target, onClose, onVoid }: { target?: { label: string }; onClose: () => void; onVoid: (reason: string) => Promise<void> }) {
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (target) setReason('') }, [target])
  return (
    <Dialog open={!!target} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader><DialogTitle>Void {target?.label}?</DialogTitle><DialogDescription>It stays in the history, marked void, and stops counting. Record the right one afterwards if needed.</DialogDescription></DialogHeader>
        <Field label="Reason" required><Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Paid the wrong worker" /></Field>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button variant="destructive" disabled={busy || !reason.trim()} onClick={async () => {
            setBusy(true)
            try {
              await onVoid(reason.trim())
              toast.success('Voided')
              onClose()
            } catch (e) {
              toast.error('Not voided', { description: errorMessage(e) })
            } finally {
              setBusy(false)
            }
          }}>Void</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

const NONE = 'company'

function CorrectionDialog({ line, weekStart, onClose }: { line?: Line; weekStart: string; onClose: () => void }) {
  const d = useData()
  const recordPayrollCorrection = useStore((s) => s.recordPayrollCorrection)
  const [direction, setDirection] = useState<'more' | 'less'>('more')
  const [amount, setAmount] = useState<number | ''>('')
  const [date, setDate] = useState(TODAY)
  const [projectId, setProjectId] = useState(NONE)
  const [reason, setReason] = useState('')
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  const [requestId, setRequestId] = useState(() => crypto.randomUUID())
  useEffect(() => {
    if (!line) return
    setRequestId(crypto.randomUUID()); setDirection('more'); setAmount(''); setDate(TODAY); setReason(''); setTried(false)
    setProjectId(line.costs.find((c) => c.projectId)?.projectId ?? NONE)
  }, [line])
  const projects = useMemo(() => d.s.projects.filter((p) => p.type === 'Construction'), [d])
  const amt = typeof amount === 'number' ? amount : 0
  const err = { amount: !(amt > 0) ? 'Enter the amount.' : undefined, reason: !reason.trim() ? 'Give the reason for the correction.' : undefined }
  return (
    <Dialog open={!!line} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Record correction — {line?.name}</DialogTitle>
          <DialogDescription>The week is paid and frozen. A correction changes what is owed (and the labour cost of the project you choose); it is never edited afterwards, only voided.</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Change" required className="sm:col-span-2">
            <RadioGroup value={direction} onValueChange={(v) => setDirection(v as typeof direction)} className="flex gap-6">
              <Label className="font-normal"><RadioGroupItem value="more" />Owed more (underpaid)</Label>
              <Label className="font-normal"><RadioGroupItem value="less" />Owed less (overpaid)</Label>
            </RadioGroup>
          </Field>
          <Field label="Amount" required error={tried ? err.amount : undefined}><AmountInput value={amount} onChange={setAmount} invalid={tried && !!err.amount} /></Field>
          <Field label="Date" required><DatePicker value={date} onChange={setDate} /></Field>
          <Field label="Labour cost goes to" className="sm:col-span-2">
            <SelectField value={projectId} onChange={setProjectId} options={[{ value: NONE, label: 'Company (no project)' }, ...projects.map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }))]} />
          </Field>
          <Field label="Reason" required className="sm:col-span-2" error={tried ? err.reason : undefined}><Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Half day on 14 Oct was missed" /></Field>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button disabled={busy} onClick={async () => {
            setTried(true)
            if (!line || err.amount || err.reason) return
            setBusy(true)
            try {
              await recordPayrollCorrection(weekStart, line.employeeId, {
                requestId, correctionDate: date, wageDelta: direction === 'more' ? amt : -amt, projectId: projectId === NONE ? undefined : projectId, reason: reason.trim(),
              })
              toast.success('Correction recorded', { description: `${direction === 'more' ? '+' : '−'}${formatLKR(amt)} for ${line.name}.` })
              onClose()
            } catch (e) {
              toast.error('Correction not recorded', { description: errorMessage(e) })
            } finally {
              setBusy(false)
            }
          }}>Record correction</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function AdjustDialog({ line, weekStart, onClose, onSave }: { line?: Line; weekStart: string; onClose: () => void; onSave: (a: { weekStart: string; employeeId: string; advanceDeduction: number; otherDeduction: number; otherNote?: string }) => Promise<void> }) {
  const [adv, setAdv] = useState<number | ''>('')
  const [other, setOther] = useState<number | ''>('')
  const [note, setNote] = useState('')
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (line) { setAdv(line.advanceDeduction); setOther(line.otherDeduction || ''); setNote(line.otherNote ?? ''); setTried(false) } }, [line])
  const a = Number(adv) || 0, o = Number(other) || 0
  const err = !line ? undefined : a > line.advancesOutstanding ? `Only ${formatLKR(line.advancesOutstanding)} in advances is outstanding.` : a + o > line.gross ? 'Deductions cannot exceed gross wages.' : o > 0 && !note.trim() ? 'Explain the other deduction.' : undefined
  const net = useMemo(() => (line ? line.gross - a - o : 0), [line, a, o])
  return (
    <Dialog open={!!line} onOpenChange={(x) => !x && onClose()}>
      <DialogContent>
        <DialogHeader><DialogTitle>Adjust deductions — {line?.name}</DialogTitle><DialogDescription>Use when part of an advance should carry to next week, or for a documented deduction (e.g. a damaged tool agreed with the worker).</DialogDescription></DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Advance deduction" hint={line ? `${formatLKR(line.advancesOutstanding)} outstanding` : undefined}><AmountInput value={adv} onChange={setAdv} /></Field>
          <Field label="Other approved deduction" optional><AmountInput value={other} onChange={setOther} /></Field>
          <Field label="Reason for other deduction" className="sm:col-span-2" optional={!o}><Input value={note} onChange={(e) => setNote(e.target.value)} /></Field>
        </div>
        <div className="bg-muted/60 flex justify-between rounded-md px-3 py-2 text-sm"><span className="text-muted-foreground">Net payable</span><Money value={net} className="font-semibold" /></div>
        {tried && err && <p className="text-destructive text-sm">{err}</p>}
        <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button disabled={busy} onClick={async () => {
          setTried(true)
          if (err || !line) return
          setBusy(true)
          try {
            await onSave({ weekStart, employeeId: line.employeeId, advanceDeduction: a, otherDeduction: o, otherNote: note || undefined })
            onClose()
          } catch (e) {
            toast.error('Deductions not saved', { description: errorMessage(e) })
          } finally {
            setBusy(false)
          }
        }}>Save deductions</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
