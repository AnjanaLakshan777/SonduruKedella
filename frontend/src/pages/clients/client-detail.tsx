import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Mail, MapPin, Pencil, Phone, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { KpiStrip } from '@/components/app/kpi-strip'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { CellMain, Mono } from '@/components/app/data-table'
import { EmptyState } from '@/components/app/empty-state'
import { ReceivablesTable } from '@/components/app/receivables-table'
import { ClientDialog } from '@/components/app/client-dialog'
import { useData } from '@/store/store'
import { clientSummary } from '@/lib/finance'
import { formatDate } from '@/lib/dates'
import { formatLKR } from '@/lib/format'

const M = (v: number) => <Money value={v} decimals={0} currency={false} />

export default function ClientDetail() {
  const { id = '' } = useParams()
  const d = useData()
  const navigate = useNavigate()
  const client = d.clientById.get(id)
  useCrumbs([{ label: 'Clients', to: '/clients' }, { label: client?.name ?? 'Not found' }])
  const [edit, setEdit] = useState(false)
  if (!client) return <EmptyState title="Client not found" action={<Button asChild><Link to="/clients">Back to Clients</Link></Button>} />
  const s = clientSummary(d, id)
  const history = s.installments.flatMap((i) => [
    ...i.payments.map((p) => ({ id: p.id, date: p.date, kind: 'Payment received', ref: `${i.project.code} · ${i.inst.description}`, detail: `${p.method}${p.reference ? ` · ${p.reference}` : ''}`, amount: p.amount, wo: false, pid: i.project.id })),
    ...i.writeOffs.map((w) => ({ id: w.id, date: w.date, kind: 'Written off', ref: `${i.project.code} · ${i.inst.description}`, detail: w.reason, amount: w.amount, wo: true, pid: i.project.id })),
    ...(i.inst.billed ? [{ id: `b${i.inst.id}`, date: i.inst.billedOn ?? i.inst.dueDate, kind: 'Billed', ref: `${i.project.code} · ${i.inst.description}`, detail: 'Revenue recorded', amount: i.net, wo: false, pid: i.project.id }] : []),
  ]).sort((a, b) => b.date.localeCompare(a.date))
  const openRows = s.installments.filter((i) => i.inst.billed && i.remaining > 0)

  return (
    <>
      <PageHeader
        meta={<><Badge variant="outline">{client.kind}</Badge>{s.overdue > 0 && <StatusBadge status="Overdue" />}</>}
        title={client.name}
        description={`Client since ${formatDate(client.createdAt)} · ${s.projects.length} project${s.projects.length === 1 ? '' : 's'}`}
        actions={<><Button variant="outline" onClick={() => setEdit(true)}><Pencil />Edit</Button><Button asChild><Link to="/projects/new"><Plus />New project</Link></Button></>}
      />
      <KpiStrip items={[
        { label: 'Contract Value', unit: 'LKR', value: M(s.contractValue), basis: 'Agreed', caption: 'All projects' },
        { label: 'Revenue Recorded', unit: 'LKR', value: M(s.revenueRecorded - s.writtenOff), basis: 'Recorded', caption: s.writtenOff ? `After ${formatLKR(s.writtenOff, { decimals: 0, currency: false })} written off` : undefined },
        { label: 'Payments Received', unit: 'LKR', value: M(s.received), basis: 'Received' },
        { label: 'Owed Now', unit: 'LKR', value: M(s.receivable), basis: 'Recorded', caption: s.overdue ? <span className="text-destructive-soft-foreground font-medium">{s.overdue.toLocaleString('en-LK')} overdue</span> : 'Nothing overdue' },
        { label: 'Written Off', unit: 'LKR', value: M(s.writtenOff), caption: 'Bad debt — not received' },
      ]} />
      <div className="grid items-start gap-6 xl:grid-cols-3">
        <div className="grid gap-6 xl:col-span-2">
          <Card className="gap-4 pb-0">
            <CardHeader><CardTitle>Amounts owed</CardTitle><CardDescription>Billed installments still unpaid, across this client's projects</CardDescription></CardHeader>
            {openRows.length ? <ReceivablesTable rows={openRows} showClient={false} /> : <CardContent className="text-muted-foreground pb-6 text-sm">Nothing owed right now.</CardContent>}
          </Card>
          <Card className="gap-4 pb-0">
            <CardHeader><CardTitle>Related projects</CardTitle></CardHeader>
            <Table>
              <TableHeader><TableRow><TableHead className="pl-6">Project</TableHead><TableHead>Status</TableHead><TableHead className="text-right">Contract (LKR)</TableHead><TableHead className="text-right">Received (LKR)</TableHead><TableHead className="pr-6 text-right">Outstanding (LKR)</TableHead></TableRow></TableHeader>
              <TableBody>
                {s.projects.map((p) => (
                  <TableRow key={p.project.id} className="cursor-pointer" onClick={() => navigate(`/projects/${p.project.id}`)}>
                    <TableCell className="max-w-72 pl-6"><CellMain title={p.project.name} sub={<><Mono>{p.project.code}</Mono> · {p.project.type}</>} /></TableCell>
                    <TableCell><StatusBadge status={p.project.status} /></TableCell>
                    <TableCell className="text-right"><Money value={p.contractValue} currency={false} decimals={0} /></TableCell>
                    <TableCell className="text-right"><Money value={p.received} currency={false} decimals={0} /></TableCell>
                    <TableCell className="pr-6 text-right font-medium"><Money value={p.receivable} currency={false} decimals={0} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
          <Card className="gap-4 pb-0">
            <CardHeader><CardTitle>Transaction history</CardTitle><CardDescription>Billing, payments and write-offs, newest first</CardDescription></CardHeader>
            <Table>
              <TableBody>
                {history.map((h) => (
                  <TableRow key={h.id} className="cursor-pointer" onClick={() => navigate(`/projects/${h.pid}?tab=payments`)}>
                    <TableCell className="pl-6">{formatDate(h.date)}</TableCell>
                    <TableCell><CellMain title={h.kind} sub={h.ref} /></TableCell>
                    <TableCell className="text-muted-foreground hidden max-w-64 truncate md:table-cell">{h.detail}</TableCell>
                    <TableCell className="pr-6 text-right font-medium">{h.kind === 'Payment received' ? <Money value={h.amount} signed tone="positive" /> : <Money value={h.amount} tone={h.wo ? 'muted' : 'default'} />}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
        </div>
        <Card>
          <CardHeader><CardTitle>Contact</CardTitle></CardHeader>
          <CardContent className="grid gap-3 text-sm">
            <a href={`tel:${client.phone.replace(/\s/g, '')}`} className="flex items-center gap-2"><Phone className="text-muted-foreground size-4" />{client.phone}</a>
            {client.email && <a href={`mailto:${client.email}`} className="flex items-center gap-2 break-all"><Mail className="text-muted-foreground size-4 shrink-0" />{client.email}</a>}
            <span className="flex items-start gap-2"><MapPin className="text-muted-foreground mt-0.5 size-4 shrink-0" />{client.address}, {client.city}</span>
            {client.notes && <p className="bg-muted/60 rounded-md p-3">{client.notes}</p>}
          </CardContent>
        </Card>
      </div>
      <ClientDialog open={edit} onOpenChange={setEdit} edit={client} />
    </>
  )
}
