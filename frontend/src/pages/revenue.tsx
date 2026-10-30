import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { HandCoins, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { KpiStrip } from '@/components/app/kpi-strip'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { CellMain, Mono, Pagination, SortableHead, usePaged, useSort } from '@/components/app/data-table'
import { ALL, DateRangeFilter, FilterBar, FilterSelect, SearchInput, defaultRange, inRange } from '@/components/app/filters'
import { AmountInput, DatePicker, Field, SelectField } from '@/components/app/form'
import { EmptyState } from '@/components/app/empty-state'
import { PaymentDialog } from '@/components/app/payment-dialog'
import { useData, useStore } from '@/store/store'
import { sum } from '@/lib/finance'
import { addDays, formatDate, TODAY } from '@/lib/dates'
import { formatLKR } from '@/lib/format'
import { errorMessage } from '@/lib/api'

interface Row { id: string; date: string; category: string; projectId?: string; code?: string; project?: string; client: string; description: string; amount: number; received: number; outstanding: number; status: string; due?: string; otherId?: string }

export default function RevenuePage() {
  useCrumbs([{ label: 'Revenue' }])
  const d = useData()
  const navigate = useNavigate()
  const recordOtherIncomeReceipt = useStore((s) => s.recordOtherIncomeReceipt)
  const [q, setQ] = useState('')
  const [range, setRange] = useState(defaultRange())
  const [cat, setCat] = useState(ALL)
  const [proj, setProj] = useState(ALL)
  const [client, setClient] = useState(ALL)
  const [status, setStatus] = useState(ALL)
  const [addOpen, setAddOpen] = useState(false)
  const [receiptFor, setReceiptFor] = useState<Row | undefined>()

  const all: Row[] = useMemo(() => [
    ...d.installments.filter((i) => i.inst.billed).map((i) => ({
      id: i.inst.id, date: i.inst.billedOn ?? i.inst.dueDate, category: i.project.type, projectId: i.project.id, code: i.project.code, project: i.project.name, client: i.client.name,
      description: i.inst.description, amount: i.net, received: i.paid, outstanding: i.remaining, status: i.status, due: i.inst.dueDate,
    })),
    ...d.otherIncome.map((o) => ({ id: o.income.id, otherId: o.income.id, date: o.income.date, category: 'Other Income', client: o.income.payer ?? '—', description: o.income.description, amount: o.income.amount, received: o.received, outstanding: o.remaining, status: o.status, due: o.income.dueDate })),
  ], [d])
  const rows = all.filter((r) => inRange(r.date, range) && (cat === ALL || r.category === cat) && (proj === ALL || r.projectId === proj) && (client === ALL || r.client === client) && (status === ALL || r.status === status)
    && (!q || `${r.description} ${r.client} ${r.code ?? ''} ${r.project ?? ''}`.toLowerCase().includes(q.toLowerCase())))
  const { sorted, sort, toggle } = useSort(rows, { key: 'date', dir: 'desc' }, { date: (r) => r.date, amount: (r) => r.amount, outstanding: (r) => r.outstanding, client: (r) => r.client })
  const paged = usePaged(sorted, 12)
  const scheduled = d.installments.filter((i) => !i.inst.billed && i.project.status !== 'Cancelled').sort((a, b) => a.inst.dueDate.localeCompare(b.inst.dueDate))
  const active = q !== '' || cat !== ALL || proj !== ALL || client !== ALL || status !== ALL || range.preset !== 'fy'
  const receiptRow = receiptFor ? d.otherIncome.find((o) => o.income.id === receiptFor.otherId) : undefined

  return (
    <>
      <PageHeader title="Revenue" description="Income recorded for work done, and how much of it has been received. Each installment is counted once, however many payments it takes." actions={<Button onClick={() => setAddOpen(true)}><Plus />Add Revenue</Button>} />
      <KpiStrip items={[
        { label: 'Revenue Recorded', unit: 'LKR', value: <Money value={sum(rows, (r) => r.amount)} decimals={0} currency={false} />, basis: 'Recorded', caption: `${rows.length} records in view` },
        { label: 'Received', unit: 'LKR', value: <Money value={sum(rows, (r) => r.received)} decimals={0} currency={false} />, basis: 'Received', caption: 'Against these records' },
        { label: 'Outstanding', unit: 'LKR', value: <Money value={sum(rows, (r) => r.outstanding)} decimals={0} currency={false} />, caption: `${formatLKR(sum(rows.filter((r) => r.status === 'Overdue'), (r) => r.outstanding), { decimals: 0 })} overdue` },
        { label: 'Scheduled, not billed', unit: 'LKR', value: <Money value={sum(scheduled, (i) => i.net)} decimals={0} currency={false} />, basis: 'Expected', caption: 'Future installments — not revenue yet' },
      ]} />
      <Tabs defaultValue="recorded" className="gap-4">
        <TabsList variant="line"><TabsTrigger value="recorded">Revenue recorded</TabsTrigger><TabsTrigger value="scheduled">Scheduled installments</TabsTrigger></TabsList>
        <TabsContent value="recorded" className="grid gap-4">
          <FilterBar active={active} onClear={() => { setQ(''); setCat(ALL); setProj(ALL); setClient(ALL); setStatus(ALL); setRange(defaultRange()) }}>
            <SearchInput value={q} onChange={setQ} placeholder="Search description, client…" />
            <DateRangeFilter value={range} onChange={setRange} />
            <FilterSelect label="Categories" value={cat} onChange={setCat} options={d.s.settings.revenueCategories} />
            <FilterSelect label="Projects" value={proj} onChange={setProj} options={d.s.projects.map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }))} />
            <FilterSelect label="Clients" value={client} onChange={setClient} options={[...new Set(all.map((r) => r.client))].sort()} />
            <FilterSelect label="Payment statuses" value={status} onChange={setStatus} options={['Due', 'Partially Paid', 'Paid', 'Overdue', 'Written Off']} />
          </FilterBar>
          <Card className="gap-0 py-0">
            {rows.length ? (
              <>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <SortableHead label="Date" sortKey="date" sort={sort} onSort={toggle} className="pl-6" />
                      <TableHead>Description</TableHead>
                      <TableHead>Category</TableHead>
                      <SortableHead label="Client" sortKey="client" sort={sort} onSort={toggle} className="hidden lg:table-cell" />
                      <SortableHead label="Amount (LKR)" sortKey="amount" sort={sort} onSort={toggle} align="right" />
                      <TableHead className="hidden text-right md:table-cell">Received (LKR)</TableHead>
                      <SortableHead label="Outstanding (LKR)" sortKey="outstanding" sort={sort} onSort={toggle} align="right" className="hidden sm:table-cell" />
                      <TableHead className="pr-6">Payment</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {paged.rows.map((r) => (
                      <TableRow key={r.id} className="cursor-pointer" onClick={() => (r.projectId ? navigate(`/projects/${r.projectId}?tab=payments`) : r.outstanding > 0 && setReceiptFor(r))}>
                        <TableCell className="pl-6">{formatDate(r.date)}</TableCell>
                        <TableCell className="max-w-80"><CellMain title={r.description} sub={r.code ? <><Mono>{r.code}</Mono> · {r.project}</> : 'Not linked to a project'} /></TableCell>
                        <TableCell>{r.category}</TableCell>
                        <TableCell className="hidden max-w-48 truncate lg:table-cell">{r.client}</TableCell>
                        <TableCell className="text-right font-medium"><Money value={r.amount} currency={false} /></TableCell>
                        <TableCell className="hidden text-right md:table-cell"><Money value={r.received} currency={false} /></TableCell>
                        <TableCell className="hidden text-right sm:table-cell">{r.outstanding > 0 ? <Money value={r.outstanding} currency={false} /> : <span className="text-muted-foreground">—</span>}</TableCell>
                        <TableCell className="pr-6"><StatusBadge status={r.status} /></TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                  <TableFooter>
                    <TableRow>
                      <TableCell className="pl-6" colSpan={3}>{rows.length} records</TableCell>
                      <TableCell className="hidden lg:table-cell" />
                      <TableCell className="text-right"><Money value={sum(rows, (r) => r.amount)} currency={false} /></TableCell>
                      <TableCell className="hidden text-right md:table-cell"><Money value={sum(rows, (r) => r.received)} currency={false} /></TableCell>
                      <TableCell className="hidden text-right sm:table-cell"><Money value={sum(rows, (r) => r.outstanding)} currency={false} /></TableCell>
                      <TableCell />
                    </TableRow>
                  </TableFooter>
                </Table>
                <div className="border-t px-6 py-3"><Pagination {...paged} /></div>
              </>
            ) : <EmptyState icon={HandCoins} title="No revenue in this view" description="Change the date range or filters." />}
          </Card>
        </TabsContent>
        <TabsContent value="scheduled">
          <Card className="gap-0 py-0">
            <Table>
              <TableHeader><TableRow><TableHead className="pl-6">Due date</TableHead><TableHead>Installment</TableHead><TableHead className="hidden md:table-cell">Client</TableHead><TableHead className="text-right">Amount (LKR)</TableHead><TableHead className="pr-6">Status</TableHead></TableRow></TableHeader>
              <TableBody>
                {scheduled.map((i) => (
                  <TableRow key={i.inst.id} className="cursor-pointer" onClick={() => navigate(`/projects/${i.project.id}?tab=payments`)}>
                    <TableCell className="pl-6">{formatDate(i.inst.dueDate)}</TableCell>
                    <TableCell className="max-w-80"><CellMain title={i.inst.description} sub={<><Mono>{i.project.code}</Mono> · {i.project.name}</>} /></TableCell>
                    <TableCell className="hidden md:table-cell">{i.client.name}</TableCell>
                    <TableCell className="text-right"><Money value={i.net} currency={false} /></TableCell>
                    <TableCell className="pr-6"><StatusBadge status="Upcoming" /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
        </TabsContent>
      </Tabs>
      <AddRevenueDialog open={addOpen} onOpenChange={setAddOpen} />
      <PaymentDialog
        open={!!receiptRow} onOpenChange={(o) => !o && setReceiptFor(undefined)} title={`Record receipt — ${receiptRow?.income.description ?? ''}`}
        context={receiptRow ? [{ label: 'Amount', value: receiptRow.income.amount }, { kind: 'less', label: 'Received', value: receiptRow.received }, { kind: 'total', label: 'Outstanding', value: receiptRow.remaining }] : undefined}
        outstanding={receiptRow?.remaining ?? 0}
        onSubmit={(p) => receiptRow ? recordOtherIncomeReceipt({ incomeId: receiptRow.income.id, ...p }) : undefined}
      />
    </>
  )
}

function AddRevenueDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const d = useData()
  const { billInstallment, addVariation, addOtherIncome } = useStore()
  const [category, setCategory] = useState('Construction')
  const [mode, setMode] = useState<'bill' | 'variation'>('bill')
  const [projectId, setProjectId] = useState('')
  const [instId, setInstId] = useState('')
  const [description, setDescription] = useState('')
  const [payer, setPayer] = useState('')
  const [amount, setAmount] = useState<number | ''>('')
  const [date, setDate] = useState(TODAY)
  const [due, setDue] = useState(addDays(TODAY, 14))
  const [reference, setReference] = useState('')
  const [notes, setNotes] = useState('')
  const [receivedNow, setReceivedNow] = useState<number | ''>('')
  const [method, setMethod] = useState('Cash')
  const [busy, setBusy] = useState(false)
  const [requestId, setRequestId] = useState(() => crypto.randomUUID())
  const [tried, setTried] = useState(false)
  useEffect(() => { if (open) { setRequestId(crypto.randomUUID()); setCategory('Construction'); setMode('bill'); setProjectId(''); setInstId(''); setDescription(''); setPayer(''); setAmount(''); setDate(TODAY); setDue(addDays(TODAY, 14)); setReference(''); setNotes(''); setReceivedNow(''); setTried(false) } }, [open])
  const isProject = category !== 'Other Income'
  const projects = d.projects.filter((p) => p.project.type === category && p.project.status !== 'Cancelled')
  const pf = d.projectFinById.get(projectId)
  const unbilled = pf?.installments.filter((i) => !i.inst.billed) ?? []
  const inst = unbilled.find((i) => i.inst.id === instId)
  const amt = typeof amount === 'number' ? amount : 0
  const err = {
    project: isProject && !projectId ? 'Choose the project.' : undefined,
    inst: isProject && mode === 'bill' && !instId ? 'Choose the installment to bill.' : undefined,
    description: (!isProject || mode === 'variation') && !description.trim() ? 'Describe the income.' : undefined,
    amount: (!isProject || mode === 'variation') && !(amt > 0) ? 'Enter the amount.' : undefined,
    received: !isProject && typeof receivedNow === 'number' && receivedNow > amt ? 'Cannot exceed the amount.' : undefined,
  }
  async function save() {
    setTried(true)
    if (Object.values(err).some(Boolean)) return
    setBusy(true)
    try {
      if (isProject && mode === 'bill' && inst) {
        await billInstallment(inst.inst.id, date)
        toast.success('Revenue recorded', { description: `${formatLKR(inst.net)} billed to ${pf!.client.name} — ${inst.inst.description}. Record payments as they arrive.` })
      } else if (isProject && pf) {
        await addVariation(pf.project.id, { description: `Variation: ${description.trim()}`, amount: amt, dueDate: due!, billedOn: date })
        toast.success('Additional work billed', { description: `${formatLKR(amt)} added to ${pf.project.code} as a variation. The original contract value is unchanged.` })
      } else {
        await addOtherIncome(
          { date, description: description.trim(), payer: payer || undefined, amount: amt, dueDate: due, reference: reference || undefined, notes: notes || undefined },
          typeof receivedNow === 'number' && receivedNow > 0 ? { requestId, amount: receivedNow, method, date } : undefined,
        )
        toast.success('Other income recorded', { description: `${formatLKR(amt)} · ${description.trim()}` })
      }
      onOpenChange(false)
    } catch (e) {
      toast.error('Not saved', { description: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Add Revenue</DialogTitle>
          <DialogDescription>Record income once, when the work is billed. Money received later is recorded as payments against it — never as new revenue.</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Revenue category" required className="sm:col-span-2">
            <SelectField value={category} onChange={(v) => { setCategory(v); setProjectId(''); setInstId('') }} options={d.s.settings.revenueCategories} />
          </Field>
          {isProject ? (
            <>
              <Field label="Project" required error={tried ? err.project : undefined} className="sm:col-span-2">
                <SelectField value={projectId} onChange={(v) => { setProjectId(v); setInstId('') }} invalid={tried && !!err.project} placeholder={projects.length ? 'Select project' : `No ${category.toLowerCase()} projects`} options={projects.map((p) => ({ value: p.project.id, label: `${p.project.code} · ${p.project.name} (${p.client.name})` }))} />
              </Field>
              {pf && (
                <RadioGroup value={mode} onValueChange={(v) => setMode(v as typeof mode)} className="grid gap-2 sm:col-span-2 sm:grid-cols-2">
                  <Label className="has-[[data-state=checked]]:border-primary-text has-[[data-state=checked]]:bg-primary-soft/60 flex cursor-pointer items-start gap-2.5 rounded-md border p-3 font-normal"><RadioGroupItem value="bill" className="mt-0.5" /><span className="grid"><span className="font-medium">Bill a scheduled installment</span><span className="text-muted-foreground text-xs">{unbilled.length} not billed yet</span></span></Label>
                  <Label className="has-[[data-state=checked]]:border-primary-text has-[[data-state=checked]]:bg-primary-soft/60 flex cursor-pointer items-start gap-2.5 rounded-md border p-3 font-normal"><RadioGroupItem value="variation" className="mt-0.5" /><span className="grid"><span className="font-medium">Additional work</span><span className="text-muted-foreground text-xs">Agreed variation beyond the contract</span></span></Label>
                </RadioGroup>
              )}
              {pf && mode === 'bill' && (
                <Field label="Installment" required error={tried ? err.inst : undefined} className="sm:col-span-2">
                  <SelectField value={instId} onChange={setInstId} invalid={tried && !!err.inst} placeholder={unbilled.length ? 'Select installment' : 'Every installment is already billed'} options={unbilled.map((i) => ({ value: i.inst.id, label: `${i.inst.description} — ${formatLKR(i.net)} · due ${formatDate(i.inst.dueDate)}` }))} />
                </Field>
              )}
              {pf && mode === 'variation' && (
                <>
                  <Field label="Description" required error={tried ? err.description : undefined} className="sm:col-span-2"><Input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="e.g. Extra boundary wall, 40 ft" /></Field>
                  <Field label="Amount" required error={tried ? err.amount : undefined}><AmountInput value={amount} onChange={setAmount} invalid={tried && !!err.amount} /></Field>
                  <Field label="Payment due"><DatePicker value={due} onChange={setDue} /></Field>
                </>
              )}
            </>
          ) : (
            <>
              <Field label="Description" required error={tried ? err.description : undefined} className="sm:col-span-2"><Input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="e.g. Sale of scrap steel" /></Field>
              <Field label="Received from" optional><Input value={payer} onChange={(e) => setPayer(e.target.value)} /></Field>
              <Field label="Amount" required error={tried ? err.amount : undefined}><AmountInput value={amount} onChange={setAmount} invalid={tried && !!err.amount} /></Field>
              <Field label="Received now" optional error={tried ? err.received : undefined} hint="Leave empty if it will be paid later."><AmountInput value={receivedNow} onChange={setReceivedNow} /></Field>
              <Field label="Method"><SelectField value={method} onChange={setMethod} options={d.s.settings.paymentMethods} /></Field>
              <Field label="Payment due" optional><DatePicker value={due} onChange={setDue} /></Field>
              <Field label="Reference" optional><Input value={reference} onChange={(e) => setReference(e.target.value)} /></Field>
            </>
          )}
          <Field label="Revenue date" required><DatePicker value={date} onChange={setDate} /></Field>
          <Field label="Notes" optional className="sm:col-span-2"><Textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
        </div>
        <DialogFooter><Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button><Button onClick={save} disabled={busy}>Record revenue</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
