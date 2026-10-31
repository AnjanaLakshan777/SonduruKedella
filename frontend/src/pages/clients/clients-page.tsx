import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Plus, Users } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { KpiStrip } from '@/components/app/kpi-strip'
import { Money } from '@/components/app/money'
import { CellMain, SortableHead, useSort } from '@/components/app/data-table'
import { ALL, FilterBar, FilterSelect, SearchInput } from '@/components/app/filters'
import { EmptyState } from '@/components/app/empty-state'
import { ReceivablesTable, ageingBuckets } from '@/components/app/receivables-table'
import { ClientDialog } from '@/components/app/client-dialog'
import { useData } from '@/store/store'
import { clientSummary, sum } from '@/lib/finance'
import { formatLKR } from '@/lib/format'

export default function ClientsPage() {
  const d = useData()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') ?? 'directory'
  useCrumbs([{ label: 'Clients', to: '/clients' }, ...(tab === 'receivables' ? [{ label: 'Receivables' }] : [])])
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('open')
  const [addOpen, setAddOpen] = useState(false)

  const clients = useMemo(() => d.s.clients.map((c) => ({ c, ...clientSummary(d, c.id) })), [d])
  const shown = clients.filter((x) => !q || `${x.c.name} ${x.c.city} ${x.c.phone}`.toLowerCase().includes(q.toLowerCase()))
  const { sorted, sort, toggle } = useSort(shown, { key: 'receivable', dir: 'desc' }, { name: (x) => x.c.name, contract: (x) => x.contractValue, received: (x) => x.received, receivable: (x) => x.receivable })

  const recRows = useMemo(() => d.installments.filter((i) => {
    if (!i.inst.billed) return false
    if (q && !`${i.client.name} ${i.project.name} ${i.project.code}`.toLowerCase().includes(q.toLowerCase())) return false
    if (status === 'open') return i.remaining > 0
    if (status === ALL) return true
    return i.status === status
  }), [d, q, status])
  const billed = d.installments.filter((i) => i.inst.billed)
  const ageing = ageingBuckets(billed)

  return (
    <>
      <PageHeader title="Clients" description="Who you work for, what they have agreed, paid and still owe." actions={<Button onClick={() => setAddOpen(true)}><Plus />Add Client</Button>} />
      <Tabs value={tab} onValueChange={(t) => setParams(t === 'directory' ? {} : { tab: t }, { replace: true })} className="gap-6">
        <TabsList variant="line">
          <TabsTrigger value="directory">Client directory</TabsTrigger>
          <TabsTrigger value="receivables">Receivables</TabsTrigger>
        </TabsList>
        <TabsContent value="directory" className="grid gap-4">
          <SearchInput value={q} onChange={setQ} placeholder="Search name, town, phone…" />
          <Card className="gap-0 py-0">
            {sorted.length ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <SortableHead label="Client" sortKey="name" sort={sort} onSort={toggle} className="pl-6" />
                    <TableHead className="hidden md:table-cell">Contact</TableHead>
                    <TableHead className="hidden lg:table-cell">Projects</TableHead>
                    <SortableHead label="Contract Value (LKR)" sortKey="contract" sort={sort} onSort={toggle} align="right" className="hidden sm:table-cell" />
                    <SortableHead label="Received (LKR)" sortKey="received" sort={sort} onSort={toggle} align="right" className="hidden lg:table-cell" />
                    <SortableHead label="Outstanding (LKR)" sortKey="receivable" sort={sort} onSort={toggle} align="right" className="pr-6" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sorted.map((x) => (
                    <TableRow key={x.c.id} className="cursor-pointer" onClick={() => navigate(`/clients/${x.c.id}`)}>
                      <TableCell className="pl-6"><CellMain title={x.c.name} sub={`${x.c.kind} · ${x.c.city}`} /></TableCell>
                      <TableCell className="hidden md:table-cell"><CellMain title={x.c.phone} sub={x.c.email ?? '—'} /></TableCell>
                      <TableCell className="hidden max-w-64 lg:table-cell"><span className="text-muted-foreground block truncate text-[13px]">{x.projects.map((p) => p.project.code).join(', ') || '—'}</span></TableCell>
                      <TableCell className="hidden text-right sm:table-cell"><Money value={x.contractValue} currency={false} decimals={0} /></TableCell>
                      <TableCell className="hidden text-right lg:table-cell"><Money value={x.received} currency={false} decimals={0} /></TableCell>
                      <TableCell className="pr-6 text-right font-medium">
                        {x.receivable > 0 ? <Money value={x.receivable} currency={false} decimals={0} /> : <span className="text-muted-foreground font-normal">—</span>}
                        {x.overdue > 0 && <div className="text-destructive-soft-foreground text-xs font-normal">{formatLKR(x.overdue, { decimals: 0, currency: false })} overdue</div>}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : <EmptyState icon={Users} title="No clients found" description="Try another name, or add the client." />}
          </Card>
        </TabsContent>
        <TabsContent value="receivables" className="grid gap-4">
          <KpiStrip items={[
            { label: 'Outstanding', unit: 'LKR', value: <Money value={sum(billed, (i) => i.remaining)} decimals={0} currency={false} />, basis: 'Recorded', caption: `${billed.filter((i) => i.remaining > 0).length} installments still owed` },
            { label: 'Overdue', unit: 'LKR', value: <Money value={sum(billed.filter((i) => i.status === 'Overdue'), (i) => i.remaining)} decimals={0} currency={false} />, tone: 'destructive', caption: 'Billed and still unpaid' },
            { label: 'Partially paid', unit: 'LKR', value: <Money value={sum(billed.filter((i) => i.paid > 0 && i.remaining > 0), (i) => i.remaining)} decimals={0} currency={false} />, caption: `${billed.filter((i) => i.paid > 0 && i.remaining > 0).length} installments part-paid` },
            { label: 'Written off (bad debt)', unit: 'LKR', value: <Money value={d.totals.writtenOff} decimals={0} currency={false} />, caption: 'Not money received' },
          ]} />
          <div className="bg-card grid overflow-hidden rounded-xl border sm:grid-cols-5" aria-label="Ageing">
            {ageing.map((a) => (
              <div key={a.label} className="-mr-px -mb-px border-r border-b px-4 py-3">
                <div className="text-muted-foreground text-xs font-medium">{a.label}</div>
                <Money value={a.amount} decimals={0} className="font-semibold" />
                <div className="text-muted-foreground text-xs">{a.count} item{a.count === 1 ? '' : 's'}</div>
              </div>
            ))}
          </div>
          <FilterBar>
            <SearchInput value={q} onChange={setQ} placeholder="Search client or project…" />
            <FilterSelect label="Statuses" value={status} onChange={setStatus} options={[{ value: 'open', label: 'Outstanding (all unpaid)' }, 'Overdue', 'Partially Paid', 'Due', 'Paid', 'Written Off']} />
          </FilterBar>
          <Card className="gap-0 py-0">
            {recRows.length ? <ReceivablesTable rows={recRows} /> : <EmptyState title="Nothing matches" description="No receivables with this status." />}
          </Card>
        </TabsContent>
      </Tabs>
      <ClientDialog open={addOpen} onOpenChange={setAddOpen} />
    </>
  )
}
