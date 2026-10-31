import { useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Ban, CircleAlert, Ellipsis, FilePenLine, HandCoins, Pencil, ReceiptText, Scale, Undo2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from '@/components/ui/alert-dialog'
import { Textarea } from '@/components/ui/textarea'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { FigureList } from '@/components/app/figure-list'
import { PaymentDialog } from '@/components/app/payment-dialog'
import { VoidDialog } from '@/components/app/void-dialog'
import { WriteOffDialog } from '@/components/app/write-off-dialog'
import { ScheduleEditor, newRowKey, type ScheduleRow } from '@/components/app/schedule-editor'
import { DelayTermsForm, RULE_LABEL } from '@/components/app/delay-terms-form'
import { AmountInput, DatePicker, Field } from '@/components/app/form'
import { useStore } from '@/store/store'
import { calcDelay, scheduleStatus, sum, type InstallmentView, type ProjectFinance } from '@/lib/finance'
import { formatDate, TODAY } from '@/lib/dates'
import { formatLKR } from '@/lib/format'
import { errorMessage } from '@/lib/api'
import type { DelayTerms } from '@/data/types'

export function ProjectPayments({ pf, payFor, setPayFor }: { pf: ProjectFinance; payFor: string | null; setPayFor: (id: string | null) => void }) {
  const { recordClientPayment, billInstallment, reviseSchedule, voidReceipt, reverseWriteOff } = useStore()
  const [undoing, setUndoing] = useState<{ id: string; instId: string; kind: 'Payment' | 'Write-off'; amount: number; date: string }>()
  const [woFor, setWoFor] = useState<InstallmentView | undefined>()
  const [billFor, setBillFor] = useState<InstallmentView | undefined>()
  const [editOpen, setEditOpen] = useState(false)
  const payView = pf.installments.find((i) => i.inst.id === payFor)
  const overdue = pf.installments.filter((i) => i.status === 'Overdue')
  const late = pf.installments.filter((i) => i.daysLate > 0)
  const history = pf.installments.flatMap((i) => [
    ...i.payments.map((p) => ({ id: p.id, instId: i.inst.id, date: p.date, inst: i.inst.description, kind: 'Payment' as const, method: `${p.method}${p.reference ? ` · ${p.reference}` : ''}`, amount: p.amount })),
    ...i.writeOffs.map((w) => ({ id: w.id, instId: i.inst.id, date: w.date, inst: i.inst.description, kind: 'Write-off' as const, method: w.reason, amount: w.amount })),
  ]).sort((a, b) => b.date.localeCompare(a.date))

  return (
    <div className="grid gap-6">
      {overdue.length > 0 && (
        <div role="alert" className="bg-destructive-soft text-destructive-soft-foreground flex flex-wrap items-start gap-3 rounded-lg border border-current/20 px-4 py-3 text-sm">
          <CircleAlert className="mt-0.5 size-4 shrink-0" />
          <div className="flex-1">
            <div className="font-medium">{formatLKR(sum(overdue, (i) => i.remaining))} overdue from {pf.client.name}</div>
            <div>{overdue.map((i) => `${i.inst.description} — ${i.daysOverdue} days since billed on ${formatDate(i.inst.billedOn ?? i.inst.dueDate)}`).join(' · ')}</div>
          </div>
          <Button size="sm" variant="outline" className="bg-card text-foreground" onClick={() => setPayFor(overdue[0].inst.id)}>Record Payment</Button>
        </div>
      )}
      {late.length > 0 && (
        <div className="bg-warning-soft text-warning flex flex-wrap items-start gap-3 rounded-lg border border-current/20 px-4 py-3 text-sm">
          <CircleAlert className="mt-0.5 size-4 shrink-0" />
          <div className="flex-1">
            <div className="font-medium">{formatLKR(sum(late, (i) => i.net))} not billed yet, planned date passed</div>
            <div>{late.map((i) => `${i.inst.description} — planned ${formatDate(i.inst.dueDate)}, ${i.daysLate} ${i.daysLate === 1 ? 'day' : 'days'} late`).join(' · ')}</div>
          </div>
          <Button size="sm" variant="outline" className="bg-card text-foreground" onClick={() => setBillFor(late[0])}>Bill now</Button>
        </div>
      )}

      <Card className="gap-4 pb-0">
        <CardHeader>
          <CardTitle>Payment schedule and receivables</CardTitle>
          <CardDescription>Outstanding = amount payable − received − written off. Installments become revenue when billed.</CardDescription>
          <CardAction><Button variant="outline" size="sm" onClick={() => setEditOpen(true)}><Pencil />Edit schedule</Button></CardAction>
        </CardHeader>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="pl-6">Installment</TableHead>
              <TableHead>Billed / planned</TableHead>
              <TableHead className="text-right">Scheduled (LKR)</TableHead>
              <TableHead className="hidden text-right lg:table-cell">Deduction (LKR)</TableHead>
              <TableHead className="text-right">Received (LKR)</TableHead>
              <TableHead className="text-right">Outstanding (LKR)</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="w-12 pr-4"><span className="sr-only">Actions</span></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {pf.installments.map((i, n) => (
              <TableRow key={i.inst.id}>
                <TableCell className="max-w-72 pl-6">
                  <span className="grid"><span className="truncate font-medium">{n + 1}. {i.inst.description}</span>
                    <span className="text-muted-foreground text-xs">{i.inst.billed ? `Billed ${formatDate(i.inst.billedOn)}` : 'Not billed yet'}{i.writtenOff ? ` · ${formatLKR(i.writtenOff)} written off` : ''}</span></span>
                </TableCell>
                <TableCell>{formatDate(i.inst.billedOn ?? i.inst.dueDate)}{!i.inst.billed && (i.daysLate > 0 ? <div className="text-warning text-xs">planned · {i.daysLate} {i.daysLate === 1 ? 'day' : 'days'} late</div> : <div className="text-muted-foreground text-xs">planned</div>)}{i.status === 'Overdue' && <div className="text-destructive-soft-foreground text-xs">{i.daysOverdue} days overdue</div>}</TableCell>
                <TableCell className="text-right"><Money value={i.inst.amount} currency={false} /></TableCell>
                <TableCell className="hidden text-right lg:table-cell">{i.deduction ? <Money value={-i.deduction} currency={false} /> : <span className="text-muted-foreground">—</span>}</TableCell>
                <TableCell className="text-right">{i.paid ? <Money value={i.paid} currency={false} /> : <span className="text-muted-foreground">—</span>}</TableCell>
                <TableCell className="text-right font-medium">{i.inst.billed ? <Money value={i.remaining} currency={false} /> : <span className="text-muted-foreground font-normal">not billed</span>}</TableCell>
                <TableCell><StatusBadge status={scheduleStatus(i)} /></TableCell>
                <TableCell className="pr-4">
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${i.inst.description}`}><Ellipsis /></Button></DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem disabled={!i.inst.billed || i.remaining <= 0} onSelect={() => setPayFor(i.inst.id)}><HandCoins />Record payment</DropdownMenuItem>
                      <DropdownMenuItem disabled={i.inst.billed} onSelect={() => setBillFor(i)}><ReceiptText />Bill now (record revenue)</DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem variant="destructive" disabled={!i.inst.billed || i.remaining <= 0} onSelect={() => setWoFor(i)}><FilePenLine />Write off as bad debt…</DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
          <TableFooter>
            <TableRow>
              <TableCell className="pl-6">Total</TableCell>
              <TableCell />
              <TableCell className="text-right"><Money value={pf.scheduledTotal} currency={false} /></TableCell>
              <TableCell className="hidden text-right lg:table-cell">{pf.deduction ? <Money value={-pf.deduction} currency={false} /> : '—'}</TableCell>
              <TableCell className="text-right"><Money value={pf.received} currency={false} /></TableCell>
              <TableCell className="text-right"><Money value={pf.receivable} currency={false} /></TableCell>
              <TableCell colSpan={2} className="text-muted-foreground text-xs font-normal">{pf.unbilled > 0 ? `+ ${formatLKR(pf.unbilled, { decimals: 0 })} not billed` : ''}</TableCell>
            </TableRow>
          </TableFooter>
        </Table>
      </Card>

      <div className="grid items-start gap-6 xl:grid-cols-2">
        {pf.project.type === 'Construction' && <DelaySection pf={pf} />}
        <Card className="gap-4 pb-0">
          <CardHeader><CardTitle>Payment history</CardTitle><CardDescription>Money received and amounts written off — kept separate</CardDescription></CardHeader>
          {history.length ? (
            <Table>
              <TableHeader><TableRow><TableHead className="pl-6">Date</TableHead><TableHead>Against</TableHead><TableHead className="text-right">Amount (LKR)</TableHead><TableHead className="w-10 pr-4"><span className="sr-only">Actions</span></TableHead></TableRow></TableHeader>
              <TableBody>
                {history.map((h) => (
                  <TableRow key={h.id}>
                    <TableCell className="pl-6">{formatDate(h.date)}</TableCell>
                    <TableCell className="max-w-64"><span className="grid"><span className="flex items-center gap-2 truncate">{h.inst}{h.kind === 'Write-off' && <StatusBadge status="Written Off" />}</span><span className="text-muted-foreground truncate text-xs">{h.method}</span></span></TableCell>
                    <TableCell className=" text-right font-medium">{h.kind === 'Payment' ? <Money value={h.amount} currency={false} signed tone="positive" /> : <span className="text-muted-foreground"><Money value={h.amount} currency={false} tone="muted" /> w/o</span>}</TableCell>
                    <TableCell className="pr-4">
                      <Button size="icon" variant="ghost" className="size-7" aria-label={h.kind === 'Payment' ? 'Void receipt' : 'Reverse write-off'}
                        onClick={() => setUndoing(h)}>{h.kind === 'Payment' ? <Ban /> : <Undo2 />}</Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : <CardContent className="text-muted-foreground pb-6 text-sm">No payments recorded yet.</CardContent>}
        </Card>
      </div>

      <PaymentDialog
        open={!!payView}
        onOpenChange={(o) => !o && setPayFor(null)}
        title={`Record payment — ${payView?.inst.description ?? ''}`}
        description={payView ? `${payView.client.name} · ${pf.project.code}. Partial payments are allowed; the outstanding amount updates automatically.` : undefined}
        context={payView ? [
          { label: 'Amount payable', value: payView.net, note: payView.deduction ? `After ${formatLKR(payView.deduction)} delay deduction` : undefined },
          { kind: 'less', label: 'Already received', value: payView.paid },
          ...(payView.writtenOff ? [{ kind: 'less' as const, label: 'Written off', value: payView.writtenOff }] : []),
          { kind: 'total', label: 'Outstanding', value: payView.remaining },
        ] : undefined}
        outstanding={payView?.remaining ?? 0}
        onSubmit={(p) => (payView ? recordClientPayment({ installmentId: payView.inst.id, ...p }) : undefined)}
      />
      <WriteOffDialog view={woFor} open={!!woFor} onOpenChange={(o) => !o && setWoFor(undefined)} />
      <VoidDialog
        target={undoing && { label: `the ${formatLKR(undoing.amount)} ${undoing.kind === 'Payment' ? 'receipt' : 'write-off'} of ${formatDate(undoing.date)}` }}
        verb={undoing?.kind === 'Write-off' ? 'Reverse' : 'Void'}
        description={undoing?.kind === 'Write-off' ? 'The amount goes back on what the client owes. The write-off stays in the history, marked reversed.' : undefined}
        onClose={() => setUndoing(undefined)}
        onVoid={(reason) => (undoing!.kind === 'Payment' ? voidReceipt(undoing!.instId, undoing!.id, reason) : reverseWriteOff(undoing!.instId, undoing!.id, reason))}
      />
      <AlertDialog open={!!billFor} onOpenChange={(o) => !o && setBillFor(undefined)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Bill {billFor?.inst.description} now?</AlertDialogTitle>
            <AlertDialogDescription>
              {formatLKR(billFor?.net ?? 0)} is recorded as revenue today and becomes receivable from {pf.client.name} straight away; if it is still unpaid tomorrow it shows as overdue. Payments against it will not be counted as revenue again.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={() => {
              const view = billFor!
              billInstallment(view.inst.id, TODAY)
                .then(() => toast.success('Revenue recorded', { description: `${formatLKR(view.net)} billed to ${pf.client.name}.` }))
                .catch((e) => toast.error('Not billed', { description: errorMessage(e) }))
            }}>Record revenue</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <EditScheduleDialog open={editOpen} onOpenChange={setEditOpen} pf={pf} onSave={async (rows) => {
        await reviseSchedule(pf.project.id, rows.map((r) => ({ id: r.id, description: r.description.trim(), dueDate: r.dueDate, amount: Number(r.amount) })))
        toast.success('Payment schedule updated')
      }} />
    </div>
  )
}

function EditScheduleDialog({ open, onOpenChange, pf, onSave }: { open: boolean; onOpenChange: (o: boolean) => void; pf: ProjectFinance; onSave: (rows: ScheduleRow[]) => Promise<void> }) {
  const [rows, setRows] = useState<ScheduleRow[]>([])
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    if (open) {
      setTried(false)
      // variations are billed extras on top of the contract, not part of the plan being edited
      setRows(pf.installments.filter((i) => !i.inst.variation).map((i) => ({ key: newRowKey(), id: i.inst.id, description: i.inst.description, dueDate: i.inst.dueDate, amount: i.inst.amount, billed: i.inst.billed, locked: i.inst.billed || i.paid > 0 })))
    }
  }, [open, pf])
  const total = rows.reduce((t, r) => t + (Number(r.amount) || 0), 0)
  const variations = pf.installments.filter((i) => i.inst.variation).reduce((t, i) => t + i.inst.amount, 0)
  const target = pf.contractValue
  const valid = Math.abs(total - target) < 0.01 && rows.every((r) => r.description.trim() && r.dueDate && Number(r.amount) > 0)
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>Edit payment schedule</DialogTitle>
          <DialogDescription>Installments must add up to the contract value of {formatLKR(pf.contractValue)}{variations ? ` (the ${formatLKR(variations)} of billed variations is separate)` : ''}. Billed installments are locked.</DialogDescription>
        </DialogHeader>
        <ScheduleEditor rows={rows} onChange={setRows} contractValue={target} showErrors={tried} />
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={busy} onClick={async () => {
            setTried(true)
            if (!valid) return
            setBusy(true)
            try {
              await onSave(rows)
              onOpenChange(false)
            } catch (e) {
              toast.error('Schedule not saved', { description: errorMessage(e) })
            } finally {
              setBusy(false)
            }
          }}>Save schedule</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function DelaySection({ pf }: { pf: ProjectFinance }) {
  const { saveDelayTerms, applyDelayDeduction, reverseDelayDeduction } = useStore()
  const p = pf.project
  // saves, then toasts either way
  const run = async (save: () => Promise<void>, done: () => void, failed: string) => {
    try {
      await save()
      done()
    } catch (e) {
      toast.error(failed, { description: errorMessage(e) })
    }
  }
  const d = p.delay
  const [editOpen, setEditOpen] = useState(false)
  const [terms, setTerms] = useState<DelayTerms>(d ?? { enabled: true, contractDeadline: p.expectedCompletion, rule: 'per_day' })
  const [termsTried, setTermsTried] = useState(false)
  const [completion, setCompletion] = useState(d?.actualCompletion ?? p.actualCompletion ?? TODAY)
  const calc = useMemo(() => (d ? calcDelay(p, completion) : null), [p, d, completion])
  const [finalAmt, setFinalAmt] = useState<number | ''>('')
  const [note, setNote] = useState('')
  const [confirm, setConfirm] = useState(false)
  useEffect(() => { setFinalAmt(d?.appliedAmount ?? calc?.final ?? '') }, [calc?.final, d?.appliedAmount])
  const target = pf.installments.filter((i) => i.remaining > 0 || i.deduction).slice(-1)[0] ?? pf.installments[pf.installments.length - 1]

  const editDialog = (
    <Dialog open={editOpen} onOpenChange={setEditOpen}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader><DialogTitle>Delay deduction terms</DialogTitle><DialogDescription>Copy the rule exactly from the signed agreement.</DialogDescription></DialogHeader>
        <DelayTermsForm value={terms} onChange={setTerms} contractValue={p.contractValue} showErrors={termsTried} />
        <DialogFooter>
          {d?.enabled && <Button variant="ghost" className="text-destructive mr-auto" onClick={() => run(() => saveDelayTerms(p.id, terms, false), () => setEditOpen(false), 'Terms not removed')}>Remove terms</Button>}
          <Button variant="outline" onClick={() => setEditOpen(false)}>Cancel</Button>
          <Button onClick={() => {
            setTermsTried(true)
            const ok = terms.contractDeadline && ((terms.rule === 'per_day' && terms.ratePerDay! > 0) || (terms.rule === 'percent_per_week' && terms.percentPerWeek! > 0) || (terms.rule === 'fixed_amount' && terms.fixedAmount! > 0))
            if (!ok) return
            run(() => saveDelayTerms(p.id, terms, true), () => { setEditOpen(false); toast.success('Delay deduction terms saved') }, 'Terms not saved')
          }}>Save terms</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )

  if (!d?.enabled) {
    return (
      <Card>
        <CardHeader><CardTitle>Delay Deduction (Liquidated Damages)</CardTitle><CardDescription>No delay deduction is configured for this agreement.</CardDescription></CardHeader>
        <CardContent><Button variant="outline" size="sm" onClick={() => { setTerms({ enabled: true, contractDeadline: p.expectedCompletion, rule: 'per_day' }); setEditOpen(true) }}><Scale />Add delay deduction terms</Button></CardContent>
        {editDialog}
      </Card>
    )
  }
  const applied = d.appliedAmount != null
  const amt = typeof finalAmt === 'number' ? finalAmt : 0
  return (
    <Card>
      <CardHeader>
        <CardTitle>Delay Deduction (Liquidated Damages)</CardTitle>
        <CardDescription>Reduces the amount payable for late completion. Separate from retention money; the contract value is never changed.</CardDescription>
        <CardAction><Button variant="outline" size="sm" onClick={() => { setTerms(d); setEditOpen(true) }}><Pencil />Terms</Button></CardAction>
      </CardHeader>
      <CardContent className="grid gap-5">
        <dl className="bg-muted/50 grid gap-2 rounded-md p-3 text-sm sm:grid-cols-2">
          <div><dt className="text-muted-foreground text-xs">Contractual deadline</dt><dd className="font-medium">{formatDate(d.contractDeadline)}</dd></div>
          <div><dt className="text-muted-foreground text-xs">Agreed rule</dt><dd className="font-medium">{RULE_LABEL[d.rule]}: {d.rule === 'per_day' ? formatLKR(d.ratePerDay ?? 0, { decimals: 0 }) : d.rule === 'percent_per_week' ? `${d.percentPerWeek}%` : formatLKR(d.fixedAmount ?? 0, { decimals: 0 })}</dd></div>
          <div><dt className="text-muted-foreground text-xs">Grace period</dt><dd>{d.graceDays ? `${d.graceDays} days` : 'None'}</dd></div>
          <div><dt className="text-muted-foreground text-xs">Maximum deduction</dt><dd>{d.maxDeduction ? formatLKR(d.maxDeduction, { decimals: 0 }) : 'No cap'}</dd></div>
          {d.notes && <div className="text-muted-foreground text-xs sm:col-span-2">{d.notes}</div>}
        </dl>
        {!applied && (
          <Field label="Actual (or expected) completion date" hint={p.actualCompletion ? 'From the project record.' : 'Not completed yet — try a date to see what would apply.'}>
            <DatePicker value={completion} onChange={setCompletion} />
          </Field>
        )}
        {calc && (
          <FigureList rows={[
            { label: 'Original contract value', value: p.contractValue, note: 'Stays as agreed' },
            { label: 'Actual delay', value: <span className="tabular">{calc.daysLate} day{calc.daysLate === 1 ? '' : 's'}</span>, note: `Completion ${formatDate(applied ? d.actualCompletion : completion)} vs deadline ${formatDate(d.contractDeadline)}` },
            { label: 'Deduction under the agreed terms', value: calc.calculated, note: calc.explanation },
            ...(calc.capped ? [{ kind: 'sub' as const, label: 'Limited by the maximum deduction', value: calc.cap! }] : []),
            { kind: 'less', label: applied ? 'Final deduction applied' : 'Final deduction to apply', value: applied ? d.appliedAmount! : amt },
            { kind: 'highlight', label: 'Adjusted amount payable', value: p.contractValue - (applied ? d.appliedAmount! : amt) },
          ]} />
        )}
        {applied ? (
          <div className="bg-muted/50 grid gap-1 rounded-md p-3 text-sm">
            <span>Applied on {formatDate(d.appliedOn)} against <strong>{pf.installments.find((i) => i.inst.id === d.appliedToInstallmentId)?.inst.description}</strong>.</span>
            <Button variant="link" size="sm" className="h-auto justify-self-start px-0" onClick={() => run(() => reverseDelayDeduction(p.id), () => toast('Deduction removed', { description: 'The installment shows its full amount again.' }), 'Deduction not reversed')}>Reverse to review again</Button>
          </div>
        ) : (
          calc && calc.daysLate > 0 && (
            <div className="grid gap-4 border-t pt-4">
              <Field label="Final deduction applied" hint="Usually the calculated amount. Change it only if the client and company agreed otherwise — explain why below." error={amt > (target?.remaining ?? 0) + (target?.deduction ?? 0) ? `More than the ${formatLKR(target?.remaining ?? 0)} still payable on ${target?.inst.description}.` : undefined}>
                <AmountInput value={finalAmt} onChange={setFinalAmt} />
              </Field>
              <Field label="Explanation" optional><Textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} placeholder="e.g. Agreed with the client at handover meeting on …" /></Field>
              <Button className="justify-self-start" disabled={!(amt > 0) || amt > (target?.remaining ?? 0)} onClick={() => setConfirm(true)}><Scale />Apply deduction</Button>
            </div>
          )
        )}
      </CardContent>
      {editDialog}
      <AlertDialog open={confirm} onOpenChange={setConfirm}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Apply a delay deduction of {formatLKR(amt)}?</AlertDialogTitle>
            <AlertDialogDescription>
              The amount payable on "{target?.inst.description}" drops by {formatLKR(amt)}. The contract value stays {formatLKR(p.contractValue)}; the adjusted amount payable becomes {formatLKR(p.contractValue - amt)}.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={() => target && run(
              () => applyDelayDeduction(p.id, { completionDate: completion, installmentId: target.inst.id, amount: amt, notes: note || undefined }),
              () => toast.success('Delay deduction applied', { description: `${formatLKR(amt)} deducted from ${target.inst.description}.` }),
              'Deduction not applied',
            )}>Apply deduction</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  )
}
