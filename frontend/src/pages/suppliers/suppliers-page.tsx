import { useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { FilePlus2, Plus, Truck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { KpiStrip } from '@/components/app/kpi-strip'
import { Money } from '@/components/app/money'
import { CellMain } from '@/components/app/data-table'
import { ALL, FilterBar, FilterSelect, SearchInput } from '@/components/app/filters'
import { InvoiceTable } from '@/components/app/invoice-table'
import { SupplierDialog } from '@/components/app/supplier-dialog'
import { EmptyState } from '@/components/app/empty-state'
import { useData } from '@/store/store'
import { sum } from '@/lib/finance'
import { formatDate } from '@/lib/dates'

export default function SuppliersPage() {
  const d = useData()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') ?? 'directory'
  useCrumbs([{ label: 'Suppliers', to: '/suppliers' }, ...(tab === 'payables' ? [{ label: 'Invoices & payables' }] : [])])
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('open')
  const [supplier, setSupplier] = useState(ALL)
  const [addOpen, setAddOpen] = useState(false)

  const sups = useMemo(() => d.s.suppliers.map((s) => {
    const inv = d.invoices.filter((v) => v.supplier.id === s.id)
    const open = inv.filter((v) => v.remaining > 0).sort((a, b) => a.invoice.dueDate.localeCompare(b.invoice.dueDate))
    return { s, purchases: sum(inv, (v) => v.total), outstanding: sum(open, (v) => v.remaining), overdue: sum(open.filter((v) => v.status === 'Overdue'), (v) => v.remaining), next: open[0] }
  }).filter((x) => !q || `${x.s.name} ${x.s.supplies}`.toLowerCase().includes(q.toLowerCase())), [d, q])
  const inv = d.invoices.filter((v) => (status === 'open' ? v.remaining > 0 : status === ALL || v.status === status) && (supplier === ALL || v.supplier.id === supplier) && (!q || `${v.invoice.invoiceNo} ${v.supplier.name}`.toLowerCase().includes(q.toLowerCase())))
  const t = d.totals

  return (
    <>
      <PageHeader
        title="Suppliers"
        description="Material suppliers, what was bought on credit, and what is still owed. A purchase is never marked paid until a payment is recorded."
        actions={<><Button variant="outline" onClick={() => setAddOpen(true)}><Plus />Add Supplier</Button><Button asChild><Link to="/suppliers/invoices/new"><FilePlus2 />Record Invoice</Link></Button></>}
      />
      <KpiStrip items={[
        { label: 'Supplier payables', unit: 'LKR', value: <Money value={t.supplierPayables} decimals={0} currency={false} />, basis: 'Recorded', caption: `${d.invoices.filter((v) => v.remaining > 0).length} invoices unpaid` },
        { label: 'Overdue', unit: 'LKR', value: <Money value={sum(d.invoices.filter((v) => v.status === 'Overdue'), (v) => v.remaining)} decimals={0} currency={false} />, caption: 'Past the credit period' },
        { label: 'Due in the next 7 days', unit: 'LKR', value: <Money value={sum(d.invoices.filter((v) => v.remaining > 0 && v.daysToDue >= 0 && v.daysToDue <= 7), (v) => v.remaining)} decimals={0} currency={false} />, caption: 'Plan these payments' },
        { label: 'Purchases this year', unit: 'LKR', value: <Money value={sum(d.invoices, (v) => v.total)} decimals={0} currency={false} />, caption: `${d.invoices.length} invoices` },
      ]} />
      <Tabs value={tab} onValueChange={(v) => setParams(v === 'directory' ? {} : { tab: v }, { replace: true })} className="gap-4">
        <TabsList variant="line"><TabsTrigger value="directory">Supplier directory</TabsTrigger><TabsTrigger value="payables">Invoices & payables</TabsTrigger></TabsList>
        <TabsContent value="directory" className="grid gap-4">
          <SearchInput value={q} onChange={setQ} placeholder="Search supplier or material…" />
          <Card className="gap-0 py-0">
            {sups.length ? (
              <Table>
                <TableHeader><TableRow><TableHead className="pl-6">Supplier</TableHead><TableHead className="hidden lg:table-cell">Contact</TableHead><TableHead className="hidden md:table-cell">Credit</TableHead><TableHead className="hidden text-right sm:table-cell">Purchases (LKR)</TableHead><TableHead className="text-right">Outstanding (LKR)</TableHead><TableHead className="pr-6">Next payment due</TableHead></TableRow></TableHeader>
                <TableBody>
                  {sups.map((x) => (
                    <TableRow key={x.s.id} className="cursor-pointer" onClick={() => navigate(`/suppliers/${x.s.id}`)}>
                      <TableCell className="max-w-72 pl-6"><CellMain title={x.s.name} sub={x.s.supplies} /></TableCell>
                      <TableCell className="hidden lg:table-cell"><CellMain title={x.s.contactPerson} sub={x.s.phone} /></TableCell>
                      <TableCell className="hidden md:table-cell">{x.s.creditDays} days</TableCell>
                      <TableCell className="hidden text-right sm:table-cell"><Money value={x.purchases} currency={false} decimals={0} /></TableCell>
                      <TableCell className="text-right font-medium">{x.outstanding ? <Money value={x.outstanding} currency={false} decimals={0} tone={x.overdue ? 'negative' : 'default'} /> : <span className="text-muted-foreground font-normal">—</span>}</TableCell>
                      <TableCell className="pr-6">{x.next ? <CellMain title={formatDate(x.next.invoice.dueDate)} sub={`${x.next.invoice.invoiceNo}${x.next.status === 'Overdue' ? ' · overdue' : ''}`} /> : <span className="text-muted-foreground">—</span>}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : <EmptyState icon={Truck} title="No suppliers found" />}
          </Card>
        </TabsContent>
        <TabsContent value="payables" className="grid gap-4">
          <FilterBar>
            <SearchInput value={q} onChange={setQ} placeholder="Search invoice no. or supplier…" />
            <FilterSelect label="Statuses" value={status} onChange={setStatus} options={[{ value: 'open', label: 'Unpaid (all)' }, 'Overdue', 'Partially Paid', 'Unpaid', 'Paid']} />
            <FilterSelect label="Suppliers" value={supplier} onChange={setSupplier} options={d.s.suppliers.map((s) => ({ value: s.id, label: s.name }))} />
          </FilterBar>
          <Card className="gap-0 py-0">{inv.length ? <InvoiceTable rows={inv} /> : <EmptyState title="No invoices match" />}</Card>
        </TabsContent>
      </Tabs>
      <SupplierDialog open={addOpen} onOpenChange={setAddOpen} />
    </>
  )
}
