import { Link, useParams } from 'react-router-dom'
import { FilePlus2, Mail, MapPin, Phone } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { KpiStrip } from '@/components/app/kpi-strip'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { InvoiceTable } from '@/components/app/invoice-table'
import { EmptyState } from '@/components/app/empty-state'
import { useData } from '@/store/store'
import { sum } from '@/lib/finance'
import { formatDate } from '@/lib/dates'

const M = (v: number) => <Money value={v} decimals={0} currency={false} />

export default function SupplierDetail() {
  const { id = '' } = useParams()
  const d = useData()
  const s = d.supplierById.get(id)
  useCrumbs([{ label: 'Suppliers', to: '/suppliers' }, { label: s?.name ?? 'Not found' }])
  if (!s) return <EmptyState title="Supplier not found" action={<Button asChild><Link to="/suppliers">Back to Suppliers</Link></Button>} />
  const inv = d.invoices.filter((v) => v.supplier.id === id)
  const open = inv.filter((v) => v.remaining > 0).sort((a, b) => a.invoice.dueDate.localeCompare(b.invoice.dueDate))
  const payments = inv.flatMap((v) => v.payments.map((p) => ({ p, v }))).sort((a, b) => b.p.date.localeCompare(a.p.date))
  return (
    <>
      <PageHeader title={s.name} description={`${s.supplies} · ${s.creditDays}-day credit`} actions={<Button asChild><Link to={`/suppliers/invoices/new?supplier=${s.id}`}><FilePlus2 />Record Invoice</Link></Button>} />
      <KpiStrip items={[
        { label: 'Purchases', unit: 'LKR', value: M(sum(inv, (v) => v.total)), basis: 'Recorded', caption: `${inv.length} invoices` },
        { label: 'Paid', unit: 'LKR', value: M(sum(inv, (v) => v.paid)), caption: `${payments.length} payments` },
        { label: 'Outstanding payable', unit: 'LKR', value: M(sum(open, (v) => v.remaining)), caption: `${open.length} invoices` },
        { label: 'Overdue', unit: 'LKR', value: M(sum(open.filter((v) => v.status === 'Overdue'), (v) => v.remaining)), tone: open.some((v) => v.status === 'Overdue') ? 'destructive' : 'default', caption: open[0] ? `Next due ${formatDate(open[0].invoice.dueDate)}` : 'Nothing due' },
      ]} />
      <div className="grid items-start gap-6 xl:grid-cols-3">
        <div className="grid gap-6 xl:col-span-2">
          {open.length > 0 && (
            <Card className="gap-4">
              <CardHeader><CardTitle>Upcoming due dates</CardTitle><CardDescription>Unpaid invoices, earliest first</CardDescription></CardHeader>
              <CardContent className="grid gap-2">
                {open.map((v) => (
                  <div key={v.invoice.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 border-b pb-2 text-sm last:border-0 last:pb-0">
                    <span className="w-28 font-medium">{formatDate(v.invoice.dueDate)}</span>
                    <span className="flex-1 font-mono text-[13px]">{v.invoice.invoiceNo}</span>
                    <Money value={v.remaining} className="font-medium" />
                    <StatusBadge status={v.status} />
                  </div>
                ))}
              </CardContent>
            </Card>
          )}
          <Card className="gap-4 pb-0">
            <CardHeader><CardTitle>Purchase history</CardTitle><CardDescription>Expand an invoice to see its items and payments</CardDescription></CardHeader>
            <InvoiceTable rows={inv} showSupplier={false} />
          </Card>
          <Card className="gap-4 pb-0">
            <CardHeader><CardTitle>Payments made</CardTitle></CardHeader>
            {payments.length ? (
              <Table><TableBody>
                {payments.map(({ p, v }) => (
                  <TableRow key={p.id}><TableCell className="pl-6">{formatDate(p.date)}</TableCell><TableCell className="font-mono text-[13px]">{v.invoice.invoiceNo}</TableCell><TableCell className="text-muted-foreground">{p.method}{p.reference ? ` · ${p.reference}` : ''}</TableCell><TableCell className="pr-6 text-right font-medium"><Money value={p.amount} /></TableCell></TableRow>
                ))}
              </TableBody></Table>
            ) : <CardContent className="text-muted-foreground pb-6 text-sm">No payments yet.</CardContent>}
          </Card>
        </div>
        <Card>
          <CardHeader><CardTitle>Contact</CardTitle></CardHeader>
          <CardContent className="grid gap-3 text-sm">
            <span className="font-medium">{s.contactPerson}</span>
            <a href={`tel:${s.phone.replace(/\s/g, '')}`} className="flex items-center gap-2"><Phone className="text-muted-foreground size-4" />{s.phone}</a>
            {s.email && <a href={`mailto:${s.email}`} className="flex items-center gap-2 break-all"><Mail className="text-muted-foreground size-4" />{s.email}</a>}
            <span className="flex items-start gap-2"><MapPin className="text-muted-foreground mt-0.5 size-4 shrink-0" />{s.address}</span>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
