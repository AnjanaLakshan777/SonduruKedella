import { useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { CircleAlert, HandCoins, Lock, Pencil, Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from '@/components/ui/alert-dialog'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { FigureList } from '@/components/app/figure-list'
import { PaymentDialog } from '@/components/app/payment-dialog'
import { useData, useStore } from '@/store/store'
import { sum, type CommissionView, type ProjectFinance } from '@/lib/finance'
import { formatDate } from '@/lib/dates'
import { formatLKR } from '@/lib/format'

interface Row { key: string; id?: string; recipient: string; role: string; rate: number | ''; status: 'Pending' | 'Confirmed'; confirmedOn?: string }
let k = 0

/** Commission = agreed contract value × recipient %. Shares are never capped or redistributed automatically. */
export function ProjectCommissions({ pf }: { pf: ProjectFinance }) {
  const d = useData()
  const { saveCommissions, recordCommissionPayment } = useStore()
  const p = pf.project
  const views = useMemo(() => d.commissions.filter((c) => c.project.id === p.id), [d, p.id])
  const confirmed = views.length > 0 && views.every((v) => v.arr.status === 'Confirmed')
  const [editing, setEditing] = useState(!confirmed)
  const [rows, setRows] = useState<Row[]>([])
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [payFor, setPayFor] = useState<CommissionView | undefined>()
  const [tried, setTried] = useState(false)
  const policy = d.s.settings.commission
  useEffect(() => {
    setRows(views.map((v) => ({ key: `k${++k}`, id: v.arr.id, recipient: v.arr.recipient, role: v.arr.role, rate: v.arr.rate, status: v.arr.status, confirmedOn: v.arr.confirmedOn })))
    setEditing(!(views.length > 0 && views.every((v) => v.arr.status === 'Confirmed')))
  }, [views])

  const rates = rows.map((r) => (typeof r.rate === 'number' ? r.rate : 0))
  const check = d.policyCheck(p.id, rates)
  const totalAmt = (p.contractValue * check.totalRate) / 100
  const rowErr = (r: Row) => !r.recipient.trim() ? 'Name required' : !(typeof r.rate === 'number' && r.rate > 0 && r.rate < 100) ? 'Enter a % between 0 and 100' : undefined
  const invalid = rows.some(rowErr)
  const set = (key: string, patch: Partial<Row>) => setRows(rows.map((r) => (r.key === key ? { ...r, ...patch } : r)))
  const paidAny = views.some((v) => v.paid > 0)

  function persist(confirm: boolean) {
    saveCommissions(p.id, rows.map((r) => ({ id: r.id, projectId: p.id, recipient: r.recipient.trim(), role: r.role.trim() || 'Commission', rate: Number(r.rate), status: r.status, confirmedOn: r.confirmedOn })), confirm)
    toast.success(confirm ? 'Commission arrangement confirmed' : 'Saved as draft', { description: `${check.totalRate}% of ${formatLKR(p.contractValue)} = ${formatLKR(totalAmt)}` })
    setEditing(!confirm)
  }

  return (
    <div className="grid items-start gap-6 xl:grid-cols-3">
      <div className="grid gap-6 xl:col-span-2">
        {check.conflict && (
          <div role="alert" className="bg-warning-soft text-warning flex items-start gap-3 rounded-lg border border-current/20 px-4 py-3 text-sm">
            <CircleAlert className="mt-0.5 size-4 shrink-0" />
            <div><div className="font-medium">{check.message}</div><div>Nothing has been changed. Adjust a recipient's percentage, or confirm this as an exception for the project.</div></div>
          </div>
        )}
        <Card className="gap-4 pb-0">
          <CardHeader>
            <CardTitle>Commission recipients</CardTitle>
            <CardDescription>Each amount = agreed contract value ({formatLKR(p.contractValue, { decimals: 0 })}) × that recipient's percentage — not the money received so far.</CardDescription>
            <CardAction>
              {!editing && <Button variant="outline" size="sm" onClick={() => setEditing(true)}><Pencil />Revise</Button>}
            </CardAction>
          </CardHeader>
          {editing ? (
            <>
              <Table>
                <TableHeader><TableRow><TableHead className="pl-6">Recipient</TableHead><TableHead>Role</TableHead><TableHead className="w-28 text-right">Rate %</TableHead><TableHead className="text-right">Commission (LKR)</TableHead><TableHead className="w-12" /></TableRow></TableHeader>
                <TableBody>
                  {rows.map((r) => (
                    <TableRow key={r.key} className="hover:bg-transparent">
                      <TableCell className="pl-6"><Input value={r.recipient} onChange={(e) => set(r.key, { recipient: e.target.value })} placeholder="Full name" aria-label="Recipient" aria-invalid={(tried && !r.recipient.trim()) || undefined} /></TableCell>
                      <TableCell><Input value={r.role} onChange={(e) => set(r.key, { role: e.target.value })} placeholder="e.g. 3D designer" aria-label="Role" /></TableCell>
                      <TableCell><Input type="number" step="0.5" min={0} className="text-right" value={r.rate} onChange={(e) => set(r.key, { rate: e.target.value === '' ? '' : Number(e.target.value) })} aria-label="Rate percent" aria-invalid={(tried && !!rowErr(r)) || undefined} /></TableCell>
                      <TableCell className="text-right font-medium"><Money value={(p.contractValue * (Number(r.rate) || 0)) / 100} currency={false} /></TableCell>
                      <TableCell className="pr-4"><Button variant="ghost" size="icon" className="size-8" aria-label="Remove recipient" disabled={views.find((v) => v.arr.id === r.id)?.paid ? true : false} onClick={() => setRows(rows.filter((x) => x.key !== r.key))}><Trash2 /></Button></TableCell>
                    </TableRow>
                  ))}
                  {!rows.length && <TableRow className="hover:bg-transparent"><TableCell colSpan={5} className="text-muted-foreground pl-6">No recipients yet.</TableCell></TableRow>}
                </TableBody>
                <TableFooter><TableRow><TableCell className="pl-6" colSpan={2}>Total commission commitment</TableCell><TableCell className="tabular text-right">{check.totalRate}%</TableCell><TableCell className="text-right"><Money value={totalAmt} currency={false} /></TableCell><TableCell /></TableRow></TableFooter>
              </Table>
              <CardContent className="flex flex-wrap items-center gap-2 pb-6">
                <Button variant="outline" size="sm" onClick={() => setRows([...rows, { key: `k${++k}`, recipient: '', role: '', rate: '', status: 'Pending' }])}><Plus />Add recipient</Button>
                <div className="ml-auto flex gap-2">
                  {confirmed && <Button variant="ghost" onClick={() => { setEditing(false); setRows(views.map((v) => ({ key: `k${++k}`, id: v.arr.id, recipient: v.arr.recipient, role: v.arr.role, rate: v.arr.rate, status: v.arr.status, confirmedOn: v.arr.confirmedOn }))) }}>Cancel</Button>}
                  <Button variant="outline" disabled={!rows.length} onClick={() => { setTried(true); if (!invalid) persist(false) }}>Save draft</Button>
                  <Button disabled={!rows.length} onClick={() => { setTried(true); if (!invalid) setConfirmOpen(true) }}>Review & confirm</Button>
                </div>
                {tried && invalid && <p className="text-destructive w-full text-xs">Every recipient needs a name and a percentage.</p>}
              </CardContent>
            </>
          ) : (
            <Table>
              <TableHeader><TableRow><TableHead className="pl-6">Recipient</TableHead><TableHead className="text-right">Rate</TableHead><TableHead className="text-right">Commission (LKR)</TableHead><TableHead className="text-right">Paid (LKR)</TableHead><TableHead className="text-right">Remaining (LKR)</TableHead><TableHead>Status</TableHead><TableHead className="pr-4" /></TableRow></TableHeader>
              <TableBody>
                {views.map((v) => (
                  <TableRow key={v.arr.id}>
                    <TableCell className="pl-6"><span className="grid"><span className="font-medium">{v.arr.recipient}</span><span className="text-muted-foreground text-xs">{v.arr.role} · confirmed {formatDate(v.arr.confirmedOn)}</span></span></TableCell>
                    <TableCell className="tabular text-right">{v.arr.rate}%</TableCell>
                    <TableCell className="text-right"><Money value={v.amount} currency={false} /></TableCell>
                    <TableCell className="text-right"><Money value={v.paid} currency={false} /></TableCell>
                    <TableCell className="text-right font-medium"><Money value={v.remaining} currency={false} /></TableCell>
                    <TableCell><StatusBadge status={v.status} /></TableCell>
                    <TableCell className="pr-4"><Button size="sm" variant="outline" disabled={v.remaining <= 0} onClick={() => setPayFor(v)}><HandCoins />Pay</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
              <TableFooter><TableRow><TableCell className="pl-6">Total</TableCell><TableCell className="tabular text-right">{check.totalRate}%</TableCell><TableCell className="text-right"><Money value={sum(views, (v) => v.amount)} currency={false} /></TableCell><TableCell className="text-right"><Money value={sum(views, (v) => v.paid)} currency={false} /></TableCell><TableCell className="text-right"><Money value={sum(views, (v) => v.remaining)} currency={false} /></TableCell><TableCell colSpan={2} /></TableRow></TableFooter>
            </Table>
          )}
        </Card>
        {views.some((v) => v.payments.length) && (
          <Card className="gap-4 pb-0">
            <CardHeader><CardTitle>Commission payments</CardTitle></CardHeader>
            <Table>
              <TableBody>
                {views.flatMap((v) => v.payments.map((pay) => ({ v, pay }))).sort((a, b) => b.pay.date.localeCompare(a.pay.date)).map(({ v, pay }) => (
                  <TableRow key={pay.id}><TableCell className="pl-6">{formatDate(pay.date)}</TableCell><TableCell>{v.arr.recipient}</TableCell><TableCell className="text-muted-foreground">{pay.method}{pay.reference ? ` · ${pay.reference}` : ''}</TableCell><TableCell className="pr-6 text-right font-medium"><Money value={pay.amount} /></TableCell></TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
        )}
      </div>
      <Card>
        <CardHeader><CardTitle>Policy</CardTitle><CardDescription>{p.type === 'Design' ? 'Design projects' : 'Construction projects'}</CardDescription></CardHeader>
        <CardContent className="grid gap-4 text-sm">
          {p.type === 'Design' ? (
            <FigureList rows={[
              { label: 'Contract value', value: p.contractValue },
              { label: `Company share (${policy.designCompanyShare}%)`, value: (p.contractValue * policy.designCompanyShare) / 100 },
              { label: `Commission pool (${policy.designCommissionPool}%)`, value: (p.contractValue * policy.designCommissionPool) / 100 },
              { kind: 'sub', label: `Allocated (${check.totalRate}%)`, value: totalAmt, tone: check.conflict ? 'negative' : 'default' },
            ]} />
          ) : (
            <p className="text-muted-foreground">No fixed rate is assumed for construction. Enter each recipient's agreed percentage{policy.constructionDefaultRate ? `; the configured guide is ${policy.constructionDefaultRate}%` : ''}.</p>
          )}
          <p className="text-muted-foreground flex gap-2 text-xs"><Lock className="mt-0.5 size-3.5 shrink-0" />Once confirmed, the amounts count as a project cost and appear in Commissions for payment.{paidAny ? ' Recipients already paid cannot be removed.' : ''}</p>
        </CardContent>
      </Card>

      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Confirm commission of {formatLKR(totalAmt)}?</AlertDialogTitle>
            <AlertDialogDescription>Check each calculation. After confirming, these amounts become payable and are included in the project's costs.</AlertDialogDescription>
          </AlertDialogHeader>
          <FigureList rows={[
            ...rows.map((r) => ({ label: `${r.recipient || '—'} (${r.role || 'Commission'})`, value: (p.contractValue * Number(r.rate)) / 100, note: `${formatLKR(p.contractValue, { decimals: 0 })} × ${r.rate}%` })),
            { kind: 'total' as const, label: `Total (${check.totalRate}%)`, value: totalAmt },
          ]} />
          {check.conflict && <p className="bg-warning-soft text-warning rounded-md p-3 text-sm">{check.message} Confirming records this as an exception for {p.code}.</p>}
          <AlertDialogFooter>
            <AlertDialogCancel>Go back</AlertDialogCancel>
            <AlertDialogAction onClick={() => persist(true)}>Confirm arrangement</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <PaymentDialog
        open={!!payFor} onOpenChange={(o) => !o && setPayFor(undefined)} direction="out" verb="Record Payment" successTitle="Commission payment recorded"
        title={`Pay commission — ${payFor?.arr.recipient ?? ''}`} description={payFor ? `${p.code} · ${payFor.arr.rate}% of ${formatLKR(p.contractValue)}` : undefined}
        context={payFor ? [{ label: 'Commission', value: payFor.amount }, { kind: 'less', label: 'Already paid', value: payFor.paid }, { kind: 'total', label: 'Remaining', value: payFor.remaining }] : undefined}
        outstanding={payFor?.remaining ?? 0}
        onSubmit={(x) => payFor && recordCommissionPayment({ arrangementId: payFor.arr.id, date: x.date, amount: x.amount, method: x.method, reference: x.reference })}
      />
    </div>
  )
}
