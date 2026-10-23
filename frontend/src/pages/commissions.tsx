import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, CircleAlert, HandCoins } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { KpiStrip } from '@/components/app/kpi-strip'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { CellMain, Mono, SortableHead, useSort } from '@/components/app/data-table'
import { ALL, FilterBar, FilterSelect } from '@/components/app/filters'
import { PaymentDialog } from '@/components/app/payment-dialog'
import { EmptyState } from '@/components/app/empty-state'
import { useData, useStore } from '@/store/store'
import { sum, type CommissionView } from '@/lib/finance'
import { formatDate } from '@/lib/dates'
import { formatLKR } from '@/lib/format'

export default function CommissionsPage() {
  useCrumbs([{ label: 'Commissions' }])
  const d = useData()
  const navigate = useNavigate()
  const recordCommissionPayment = useStore((s) => s.recordCommissionPayment)
  const [recipient, setRecipient] = useState(ALL)
  const [type, setType] = useState(ALL)
  const [status, setStatus] = useState(ALL)
  const [payFor, setPayFor] = useState<CommissionView | undefined>()
  const rows = d.commissions.filter((c) => (recipient === ALL || c.arr.recipient === recipient) && (type === ALL || c.project.type === type) && (status === ALL || c.status === status))
  const { sorted, sort, toggle } = useSort(rows, { key: 'remaining', dir: 'desc' }, { recipient: (c) => c.arr.recipient, amount: (c) => c.amount, remaining: (c) => c.remaining, project: (c) => c.project.code })
  const confirmed = d.commissions.filter((c) => c.arr.status === 'Confirmed')
  const conflicts = useMemo(() => [...new Set(d.commissions.map((c) => c.project.id))].map((pid) => ({ pid, check: d.policyCheck(pid) })).filter((x) => x.check.conflict), [d])
  const pendingProjects = [...new Set(d.commissions.filter((c) => c.arr.status === 'Pending').map((c) => c.project.id))]
  const payments = d.commissions.flatMap((c) => c.payments.map((p) => ({ c, p }))).sort((a, b) => b.p.date.localeCompare(a.p.date))
  const recipients = useMemo(() => {
    const m = new Map<string, { amount: number; paid: number; remaining: number; n: number }>()
    for (const c of confirmed) { const x = m.get(c.arr.recipient) ?? { amount: 0, paid: 0, remaining: 0, n: 0 }; x.amount += c.amount; x.paid += c.paid; x.remaining += c.remaining; x.n++; m.set(c.arr.recipient, x) }
    return [...m.entries()].sort((a, b) => b[1].remaining - a[1].remaining)
  }, [confirmed])

  return (
    <>
      <PageHeader title="Commissions" description="Commission on Design and Construction projects = agreed contract value × each recipient's percentage. Set recipients on each project's Commissions tab." />
      <KpiStrip items={[
        { label: 'Commission committed', unit: 'LKR', value: <Money value={sum(confirmed, (c) => c.amount)} decimals={0} currency={false} />, basis: 'Agreed', caption: `${confirmed.length} confirmed arrangements` },
        { label: 'Paid', unit: 'LKR', value: <Money value={sum(d.commissions, (c) => c.paid)} decimals={0} currency={false} />, basis: 'Recorded', caption: `${payments.length} payments` },
        { label: 'Outstanding payable', unit: 'LKR', value: <Money value={sum(confirmed, (c) => c.remaining)} decimals={0} currency={false} />, caption: `${recipients.filter(([, x]) => x.remaining > 0).length} recipients owed` },
        { label: 'Awaiting confirmation', value: pendingProjects.length, caption: pendingProjects.length ? 'Not counted as a cost yet' : 'None' },
      ]} />
      {conflicts.map(({ pid, check }) => {
        const p = d.projectById.get(pid)!
        return (
          <div key={pid} role="alert" className="bg-warning-soft text-warning flex flex-wrap items-start gap-3 rounded-lg border border-current/20 px-4 py-3 text-sm">
            <CircleAlert className="mt-0.5 size-4 shrink-0" />
            <div className="flex-1"><div className="font-medium">{p.code} {p.name}: {check.message}</div><div>Nothing has been changed automatically. Review the shares before confirming.</div></div>
            <Button size="sm" variant="outline" className="bg-card text-foreground" asChild><Link to={`/projects/${pid}?tab=commissions`}>Review<ArrowRight /></Link></Button>
          </div>
        )
      })}
      <Tabs defaultValue="list" className="gap-4">
        <TabsList variant="line"><TabsTrigger value="list">Commission directory</TabsTrigger><TabsTrigger value="recipients">By recipient</TabsTrigger><TabsTrigger value="payments">Payments</TabsTrigger></TabsList>
        <TabsContent value="list" className="grid gap-4">
          <FilterBar active={recipient !== ALL || type !== ALL || status !== ALL} onClear={() => { setRecipient(ALL); setType(ALL); setStatus(ALL) }}>
            <FilterSelect label="Recipients" value={recipient} onChange={setRecipient} options={[...new Set(d.commissions.map((c) => c.arr.recipient))].sort()} />
            <FilterSelect label="Project types" value={type} onChange={setType} options={['Design', 'Construction']} />
            <FilterSelect label="Statuses" value={status} onChange={setStatus} options={['Pending', 'Confirmed', 'Partially Paid', 'Paid']} />
          </FilterBar>
          <Card className="gap-0 py-0">
            {sorted.length ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <SortableHead label="Recipient" sortKey="recipient" sort={sort} onSort={toggle} className="pl-6" />
                    <SortableHead label="Project" sortKey="project" sort={sort} onSort={toggle} />
                    <TableHead className="hidden md:table-cell">Type</TableHead>
                    <TableHead className="hidden text-right lg:table-cell">Contract (LKR)</TableHead>
                    <TableHead className="text-right">Rate</TableHead>
                    <SortableHead label="Commission (LKR)" sortKey="amount" sort={sort} onSort={toggle} align="right" />
                    <TableHead className="hidden text-right sm:table-cell">Paid (LKR)</TableHead>
                    <SortableHead label="Remaining (LKR)" sortKey="remaining" sort={sort} onSort={toggle} align="right" />
                    <TableHead>Status</TableHead>
                    <TableHead className="pr-4"><span className="sr-only">Actions</span></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sorted.map((c) => (
                    <TableRow key={c.arr.id} className="cursor-pointer" onClick={() => navigate(`/projects/${c.project.id}?tab=commissions`)}>
                      <TableCell className="pl-6"><CellMain title={c.arr.recipient} sub={c.arr.role} /></TableCell>
                      <TableCell className="max-w-56"><CellMain title={<Mono className="text-foreground">{c.project.code}</Mono>} sub={c.project.name} /></TableCell>
                      <TableCell className="hidden md:table-cell">{c.project.type}</TableCell>
                      <TableCell className="hidden text-right lg:table-cell"><Money value={c.project.contractValue} currency={false} decimals={0} /></TableCell>
                      <TableCell className="tabular text-right">{c.arr.rate}%</TableCell>
                      <TableCell className="text-right"><Money value={c.amount} currency={false} /></TableCell>
                      <TableCell className="hidden text-right sm:table-cell">{c.paid ? <Money value={c.paid} currency={false} /> : '—'}</TableCell>
                      <TableCell className="text-right font-medium">{c.arr.status === 'Pending' ? <span className="text-muted-foreground font-normal">not confirmed</span> : <Money value={c.remaining} currency={false} />}</TableCell>
                      <TableCell><StatusBadge status={c.status} /></TableCell>
                      <TableCell className="pr-4" onClick={(e) => e.stopPropagation()}><Button size="sm" variant="outline" disabled={c.arr.status !== 'Confirmed' || c.remaining <= 0} onClick={() => setPayFor(c)}><HandCoins />Pay</Button></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
                <TableFooter><TableRow><TableCell className="pl-6" colSpan={2}>{sorted.length} arrangements</TableCell><TableCell className="hidden md:table-cell" /><TableCell className="hidden lg:table-cell" /><TableCell /><TableCell className="text-right"><Money value={sum(sorted, (c) => c.amount)} currency={false} /></TableCell><TableCell className="hidden text-right sm:table-cell"><Money value={sum(sorted, (c) => c.paid)} currency={false} /></TableCell><TableCell className="text-right"><Money value={sum(sorted.filter((c) => c.arr.status === 'Confirmed'), (c) => c.remaining)} currency={false} /></TableCell><TableCell colSpan={2} /></TableRow></TableFooter>
              </Table>
            ) : <EmptyState title="No commissions match" />}
          </Card>
        </TabsContent>
        <TabsContent value="recipients">
          <Card className="gap-0 py-0">
            <Table>
              <TableHeader><TableRow><TableHead className="pl-6">Recipient</TableHead><TableHead className="text-right">Arrangements</TableHead><TableHead className="text-right">Committed (LKR)</TableHead><TableHead className="text-right">Paid (LKR)</TableHead><TableHead className="pr-6 text-right">Owed (LKR)</TableHead></TableRow></TableHeader>
              <TableBody>{recipients.map(([name, x]) => <TableRow key={name}><TableCell className="pl-6 font-medium">{name}</TableCell><TableCell className="tabular text-right">{x.n}</TableCell><TableCell className="text-right"><Money value={x.amount} currency={false} /></TableCell><TableCell className="text-right"><Money value={x.paid} currency={false} /></TableCell><TableCell className="pr-6 text-right font-medium"><Money value={x.remaining} currency={false} /></TableCell></TableRow>)}</TableBody>
            </Table>
          </Card>
        </TabsContent>
        <TabsContent value="payments">
          <Card className="gap-0 py-0">
            <Table>
              <TableHeader><TableRow><TableHead className="pl-6">Date</TableHead><TableHead>Recipient</TableHead><TableHead>Project</TableHead><TableHead className="hidden md:table-cell">Method</TableHead><TableHead className="pr-6 text-right">Amount (LKR)</TableHead></TableRow></TableHeader>
              <TableBody>{payments.map(({ c, p }) => <TableRow key={p.id}><TableCell className="pl-6">{formatDate(p.date)}</TableCell><TableCell>{c.arr.recipient}</TableCell><TableCell><Mono className="text-foreground">{c.project.code}</Mono></TableCell><TableCell className="text-muted-foreground hidden md:table-cell">{p.method}{p.reference ? ` · ${p.reference}` : ''}</TableCell><TableCell className="pr-6 text-right font-medium"><Money value={p.amount} currency={false} /></TableCell></TableRow>)}</TableBody>
            </Table>
          </Card>
        </TabsContent>
      </Tabs>
      <PaymentDialog
        open={!!payFor} onOpenChange={(o) => !o && setPayFor(undefined)} direction="out" successTitle="Commission payment recorded"
        title={`Pay commission — ${payFor?.arr.recipient ?? ''}`} description={payFor ? `${payFor.project.code} · ${payFor.arr.rate}% of ${formatLKR(payFor.project.contractValue)}` : undefined}
        context={payFor ? [{ label: 'Commission', value: payFor.amount, note: `${formatLKR(payFor.project.contractValue, { decimals: 0 })} × ${payFor.arr.rate}%` }, { kind: 'less', label: 'Already paid', value: payFor.paid }, { kind: 'total', label: 'Remaining', value: payFor.remaining }] : undefined}
        outstanding={payFor?.remaining ?? 0}
        onSubmit={(x) => payFor && recordCommissionPayment({ arrangementId: payFor.arr.id, date: x.date, amount: x.amount, method: x.method, reference: x.reference })}
      />
    </>
  )
}
