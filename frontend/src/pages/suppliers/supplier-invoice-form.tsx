import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { CircleAlert, Paperclip, Plus, Trash2, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { AmountInput, DatePicker, Field, SelectField } from '@/components/app/form'
import { FigureList } from '@/components/app/figure-list'
import { Money } from '@/components/app/money'
import { useStore } from '@/store/store'
import { addDays, TODAY } from '@/lib/dates'
import { formatBytes, formatLKR } from '@/lib/format'
import { errorMessage } from '@/lib/api'

interface Line { key: string; materialId: string; description: string; qty: number | ''; unit: string; unitPrice: number | '' }
let n = 0
const blank = (): Line => ({ key: `l${++n}`, materialId: '', description: '', qty: '', unit: 'nos', unitPrice: '' })

export default function SupplierInvoiceForm() {
  useCrumbs([{ label: 'Suppliers', to: '/suppliers' }, { label: 'Record invoice' }])
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { suppliers, projects, materials, supplierInvoices, settings, addSupplierInvoice } = useStore()
  const [supplierId, setSupplierId] = useState(params.get('supplier') ?? '')
  const [invoiceNo, setInvoiceNo] = useState('')
  const [date, setDate] = useState(TODAY)
  const [due, setDue] = useState('')
  const [dueTouched, setDueTouched] = useState(false)
  const [alloc, setAlloc] = useState<'stock' | 'project'>('project')
  const [projectId, setProjectId] = useState('')
  const [lines, setLines] = useState<Line[]>([blank()])
  const [paid, setPaid] = useState<number | ''>('')
  const [method, setMethod] = useState('Bank Transfer')
  const [reference, setReference] = useState('')
  const [notes, setNotes] = useState('')
  const [file, setFile] = useState<File | undefined>()
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  const [requestId] = useState(() => crypto.randomUUID())
  const fileRef = useRef<HTMLInputElement>(null)
  const supplier = suppliers.find((s) => s.id === supplierId)
  useEffect(() => { if (!dueTouched) setDue(addDays(date, supplier?.creditDays ?? settings.supplierCreditDays)) }, [date, supplier, dueTouched, settings.supplierCreditDays])

  const total = lines.reduce((t, l) => t + (Number(l.qty) || 0) * (Number(l.unitPrice) || 0), 0)
  const paidAmt = typeof paid === 'number' ? paid : 0
  const dup = supplierInvoices.some((i) => i.supplierId === supplierId && i.invoiceNo.trim().toLowerCase() === invoiceNo.trim().toLowerCase())
  const err = {
    supplier: !supplierId ? 'Choose the supplier.' : undefined,
    invoiceNo: !invoiceNo.trim() ? 'Enter the supplier’s invoice number.' : dup ? 'This supplier already has an invoice with this number.' : undefined,
    due: due && due < date ? 'Due date cannot be before the invoice date.' : undefined,
    project: alloc === 'project' && !projectId ? 'Choose the project the materials went to.' : undefined,
    lines: lines.some((l) => !l.description.trim() || !(Number(l.qty) > 0) || !(Number(l.unitPrice) > 0)) ? 'Every item needs a description, quantity and unit price.' : undefined,
    stock: alloc === 'stock' && lines.some((l) => !l.materialId) ? 'Items received into stock must be linked to an inventory material.' : undefined,
    paid: paidAmt > total ? 'Amount paid cannot exceed the invoice total.' : undefined,
  }
  const errs = Object.values(err).filter(Boolean) as string[]
  const set = (key: string, patch: Partial<Line>) => setLines(lines.map((l) => (l.key === key ? { ...l, ...patch } : l)))

  async function save() {
    setTried(true)
    if (errs.length) { toast.error('Check the invoice', { description: errs[0] }); return }
    setBusy(true)
    let saved = false
    try {
      await addSupplierInvoice({
        supplierId, invoiceNo: invoiceNo.trim(), date, dueDate: due, projectId: alloc === 'project' ? projectId : undefined, notes: notes || undefined,
        lines: lines.map((l, i) => ({ id: `l${i + 1}`, description: l.description.trim(), materialId: l.materialId || undefined, qty: Number(l.qty), unit: l.unit, unitPrice: Number(l.unitPrice) })),
      }, paidAmt > 0 ? { requestId, amount: paidAmt, method, date, reference: reference || undefined } : undefined, file, () => { saved = true })
      toast.success('Invoice recorded', { description: `${invoiceNo} · ${formatLKR(total)} · ${paidAmt >= total ? 'paid' : `${formatLKR(total - paidAmt)} payable by ${due}`}` })
      navigate(`/suppliers/${supplierId}`)
    } catch (e) {
      if (saved) {
        // the invoice is in; only the file didn't make it
        toast.warning('Invoice recorded, attachment not uploaded', { description: errorMessage(e) })
        navigate(`/suppliers/${supplierId}`)
      } else toast.error('Invoice not recorded', { description: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader title="Record supplier invoice" description="A purchase on credit stays payable until payments are recorded against it." />
      <div className="grid items-start gap-6 lg:grid-cols-[1fr_320px]">
        <div className="grid gap-6">
          <Card>
            <CardHeader><CardTitle>Invoice</CardTitle></CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2">
              <Field label="Supplier" required error={tried ? err.supplier : undefined}>
                <SelectField value={supplierId} onChange={setSupplierId} invalid={tried && !!err.supplier} placeholder="Select supplier" options={suppliers.map((s) => ({ value: s.id, label: s.name }))} />
              </Field>
              <Field label="Invoice number" required error={tried || dup ? err.invoiceNo : undefined}><Input value={invoiceNo} onChange={(e) => setInvoiceNo(e.target.value)} aria-invalid={((tried || dup) && !!err.invoiceNo) || undefined} placeholder="As printed on the invoice" /></Field>
              <Field label="Invoice date" required><DatePicker value={date} onChange={setDate} /></Field>
              <Field label="Due date" required error={err.due} hint={supplier ? `${supplier.creditDays}-day credit from ${supplier.name}` : 'Set from the supplier’s credit period'}><DatePicker value={due} onChange={(v) => { setDue(v); setDueTouched(true) }} invalid={!!err.due} /></Field>
              <Field label="Project allocation" required className="sm:col-span-2" error={tried ? err.project : undefined}>
                <RadioGroup value={alloc} onValueChange={(v) => setAlloc(v as typeof alloc)} className="grid gap-2 sm:grid-cols-2">
                  <Label className="has-[[data-state=checked]]:border-primary-text has-[[data-state=checked]]:bg-primary-soft/60 flex cursor-pointer items-start gap-2.5 rounded-md border p-3 font-normal"><RadioGroupItem value="project" className="mt-0.5" /><span className="grid"><span className="font-medium">Delivered to a project site</span><span className="text-muted-foreground text-xs">Cost goes straight to the project</span></span></Label>
                  <Label className="has-[[data-state=checked]]:border-primary-text has-[[data-state=checked]]:bg-primary-soft/60 flex cursor-pointer items-start gap-2.5 rounded-md border p-3 font-normal"><RadioGroupItem value="stock" className="mt-0.5" /><span className="grid"><span className="font-medium">Received into stock</span><span className="text-muted-foreground text-xs">Charged to projects only when issued</span></span></Label>
                </RadioGroup>
                {alloc === 'project' && <SelectField value={projectId} onChange={setProjectId} invalid={tried && !!err.project} placeholder="Select project" options={projects.filter((p) => p.type === 'Construction' && p.status !== 'Cancelled').map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }))} />}
              </Field>
            </CardContent>
          </Card>
          <Card className="gap-4">
            <CardHeader><CardTitle>Items</CardTitle><CardDescription>Bricks, sand, cement, steel and other supplies on this invoice.</CardDescription></CardHeader>
            <div className="overflow-x-auto">
              <Table>
                <TableHeader><TableRow><TableHead className="min-w-44 pl-6">Material</TableHead><TableHead className="min-w-48">Description</TableHead><TableHead className="w-24 text-right">Qty</TableHead><TableHead className="w-28">Unit</TableHead><TableHead className="w-44 text-right">Unit price</TableHead><TableHead className="text-right">Total (LKR)</TableHead><TableHead className="w-12" /></TableRow></TableHeader>
                <TableBody>
                  {lines.map((l) => (
                    <TableRow key={l.key} className="hover:bg-transparent">
                      <TableCell className="pl-6"><SelectField value={l.materialId} onChange={(v) => { const m = materials.find((x) => x.id === v); set(l.key, { materialId: v, description: l.description || m?.name || '', unit: m?.unit ?? l.unit, unitPrice: l.unitPrice || m?.unitCost || '' }) }} placeholder={alloc === 'stock' ? 'Select material' : 'Optional'} options={materials.map((m) => ({ value: m.id, label: `${m.sku} · ${m.name}` }))} invalid={tried && alloc === 'stock' && !l.materialId} /></TableCell>
                      <TableCell><Input value={l.description} onChange={(e) => set(l.key, { description: e.target.value })} placeholder="e.g. River sand" aria-label="Description" aria-invalid={(tried && !l.description.trim()) || undefined} /></TableCell>
                      <TableCell><Input type="number" min={0} className="text-right" value={l.qty} onChange={(e) => set(l.key, { qty: e.target.value === '' ? '' : Number(e.target.value) })} aria-label="Quantity" aria-invalid={(tried && !(Number(l.qty) > 0)) || undefined} /></TableCell>
                      <TableCell><SelectField value={l.unit} onChange={(v) => set(l.key, { unit: v })} options={settings.units} /></TableCell>
                      <TableCell><AmountInput value={l.unitPrice} onChange={(v) => set(l.key, { unitPrice: v })} invalid={tried && !(Number(l.unitPrice) > 0)} /></TableCell>
                      <TableCell className="text-right font-medium"><Money value={(Number(l.qty) || 0) * (Number(l.unitPrice) || 0)} currency={false} /></TableCell>
                      <TableCell className="pr-4"><Button variant="ghost" size="icon" className="size-8" disabled={lines.length === 1} aria-label="Remove item" onClick={() => setLines(lines.filter((x) => x.key !== l.key))}><Trash2 /></Button></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
                <TableFooter><TableRow><TableCell className="pl-6" colSpan={5}>Invoice total</TableCell><TableCell className="text-right"><Money value={total} currency={false} /></TableCell><TableCell /></TableRow></TableFooter>
              </Table>
            </div>
            <CardContent className="grid gap-2">
              <Button variant="outline" size="sm" className="justify-self-start" onClick={() => setLines([...lines, blank()])}><Plus />Add item</Button>
              {tried && (err.lines || err.stock) && <p className="text-destructive flex items-center gap-1.5 text-xs"><CircleAlert className="size-3.5" />{err.lines ?? err.stock}</p>}
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>Notes and document</CardTitle></CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2">
              <Field label="Notes" optional className="sm:col-span-2"><Textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
              <Field label="Invoice copy" optional hint="PDF or photo of the invoice, kept privately with the record.">
                <input ref={fileRef} type="file" hidden accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" onChange={(e) => { const f = e.target.files?.[0]; if (f) setFile(f); e.target.value = '' }} />
                {file ? <div className="flex h-9 items-center gap-2 rounded-md border px-3 text-sm"><Paperclip className="text-muted-foreground size-4" /><span className="flex-1 truncate">{file.name}</span><span className="text-muted-foreground text-xs">{formatBytes(file.size)}</span><button type="button" aria-label="Remove" onClick={() => setFile(undefined)}><X className="size-4" /></button></div>
                  : <Button variant="outline" className="justify-self-start" onClick={() => fileRef.current?.click()}><Paperclip />Attach invoice</Button>}
              </Field>
            </CardContent>
          </Card>
        </div>
        <aside className="grid gap-4 lg:sticky lg:top-20">
          <Card className="gap-4">
            <CardHeader><CardTitle>Payment</CardTitle><CardDescription>Leave at zero for a credit purchase.</CardDescription></CardHeader>
            <CardContent className="grid gap-4">
              <Field label="Amount paid now" error={err.paid}><AmountInput value={paid} onChange={setPaid} invalid={!!err.paid} /></Field>
              {paidAmt > 0 && <><Field label="Method"><SelectField value={method} onChange={setMethod} options={settings.paymentMethods} /></Field><Field label="Reference" optional><Input value={reference} onChange={(e) => setReference(e.target.value)} /></Field></>}
              <FigureList rows={[{ label: 'Total amount', value: total }, { kind: 'less', label: 'Amount paid', value: paidAmt }, { kind: 'highlight', label: 'Outstanding payable', value: Math.max(0, total - paidAmt) }]} />
            </CardContent>
          </Card>
          <div className="flex gap-2"><Button variant="outline" asChild className="flex-1"><Link to="/suppliers">Cancel</Link></Button><Button className="flex-1" onClick={save} disabled={busy}>Save invoice</Button></div>
        </aside>
      </div>
    </>
  )
}
