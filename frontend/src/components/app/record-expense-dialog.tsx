import { useEffect, useMemo, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Info, Paperclip, Plus, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { useStore, type ExpenseInput } from '@/store/store'
import type { Expense, ExpenseGroup, ExpenseScope } from '@/data/types'
import { TODAY } from '@/lib/dates'
import { formatBytes, formatLKR } from '@/lib/format'
import { errorMessage } from '@/lib/api'
import { AmountInput, DatePicker, Field, SelectField } from './form'
import { SupplierDialog } from './supplier-dialog'

const AUTO: Record<string, string> = {
  Labour: 'Labour costs come from attendance and overtime in Labour & Payroll — they are added to projects automatically.',
  Commission: 'Commissions are set on the project’s Commissions tab and calculated from the contract value.',
}

/** Context-sensitive expense form: fields change with the category. */
export function RecordExpenseDialog({ open, onOpenChange, projectId, edit }: { open: boolean; onOpenChange: (o: boolean) => void; projectId?: string; edit?: Expense }) {
  const { projects, suppliers, settings, addExpense, updateExpense, uploadExpenseAttachment } = useStore()
  const [scope, setScope] = useState<ExpenseScope>('Project')
  const [pid, setPid] = useState('')
  const [category, setCategory] = useState('')
  const [date, setDate] = useState(TODAY)
  const [description, setDescription] = useState('')
  const [payee, setPayee] = useState('')
  const [supplierId, setSupplierId] = useState('')
  const [amount, setAmount] = useState<number | ''>('')
  const [qty, setQty] = useState<number | ''>('')
  const [unit, setUnit] = useState('nos')
  const [unitPrice, setUnitPrice] = useState<number | ''>('')
  const [machine, setMachine] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [period, setPeriod] = useState('')
  const [invoiceNo, setInvoiceNo] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [pay, setPay] = useState<'unpaid' | 'full' | 'part'>('full')
  const [paid, setPaid] = useState<number | ''>('')
  const [method, setMethod] = useState('Cash')
  const [notes, setNotes] = useState('')
  // an attachment already saved (edit) or a newly picked file, which is uploaded after the expense is saved
  const [file, setFile] = useState<{ fileName: string; size: number; file?: File } | undefined>()
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  const [newSupplierOpen, setNewSupplierOpen] = useState(false)
  const [requestId, setRequestId] = useState(() => crypto.randomUUID())
  const fileRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!open) return
    setTried(false)
    setRequestId(crypto.randomUUID())
    if (edit) {
      setScope(edit.scope); setPid(edit.projectId ?? ''); setCategory(edit.category); setDate(edit.date); setDescription(edit.description); setPayee(edit.payee)
      setSupplierId(edit.supplierId ?? ''); setAmount(edit.amount); setInvoiceNo(edit.invoiceNo ?? ''); setDueDate(edit.dueDate ?? ''); setNotes(edit.notes ?? ''); setFile(edit.attachment)
      setMachine(edit.machineType ?? ''); setPeriod(edit.billingPeriod ?? ''); setFrom(edit.usageFrom ?? ''); setTo(edit.usageTo ?? '')
      setQty(edit.quantity ?? ''); setUnit(edit.unit ?? 'nos'); setUnitPrice(edit.unitPrice ?? '')
    } else {
      setScope(projectId ? 'Project' : 'Project'); setPid(projectId ?? ''); setCategory(''); setDate(TODAY); setDescription(''); setPayee(''); setSupplierId(''); setAmount('')
      setQty(''); setUnit('nos'); setUnitPrice(''); setMachine(''); setFrom(''); setTo(''); setPeriod(''); setInvoiceNo(''); setDueDate(''); setPay('full'); setPaid(''); setMethod('Cash'); setNotes(''); setFile(undefined)
    }
  }, [open, edit, projectId])

  const project = projects.find((p) => p.id === pid)
  const group: ExpenseGroup = scope === 'Company' ? 'Company' : (project?.type ?? 'Construction')
  const categories = settings.expenseCategories[group]
  useEffect(() => { if (category && !categories.includes(category)) setCategory('') }, [group]) // eslint-disable-line react-hooks/exhaustive-deps
  const isMaterials = category === 'Materials'
  const isMachinery = category === 'Machinery Rental'
  const isElectricity = category === 'Electricity'
  const computed = isMaterials && typeof qty === 'number' && typeof unitPrice === 'number' ? Math.round(qty * unitPrice * 100) / 100 : undefined
  const total = computed ?? (typeof amount === 'number' ? amount : 0)

  const errors = {
    project: scope === 'Project' && !pid ? 'Choose the project, or record it as a company-level expense.' : undefined,
    category: !category ? 'Choose a category.' : AUTO[category],
    description: !description.trim() ? 'Describe what was bought or paid for.' : undefined,
    payee: !payee.trim() && !supplierId ? 'Who was paid (or is owed)?' : undefined,
    amount: !(total > 0) ? 'Enter the amount.' : undefined,
    paid: pay === 'part' && !(typeof paid === 'number' && paid > 0 && paid < total) ? 'Enter a part payment smaller than the total.' : undefined,
    period: isMachinery && from && to && to < from ? 'End date must be on or after the start date.' : undefined,
  }
  const hasErr = Object.values(errors).some(Boolean)

  const electricityHint = useMemo(() => {
    if (!isElectricity) return undefined
    if (group === 'Design') return 'Design-work electricity (studio, rendering). Kept separate from site and office electricity.'
    if (group === 'Construction') return 'Site electricity for this construction project.'
    return undefined
  }, [isElectricity, group])

  async function save() {
    setTried(true)
    if (hasErr) return
    const sup = suppliers.find((s) => s.id === supplierId)
    const rec: ExpenseInput = {
      date, scope, projectId: scope === 'Project' ? pid : undefined, group, category, description: description.trim(), payee: sup?.name ?? payee.trim(), supplierId: supplierId || undefined,
      amount: total, dueDate: dueDate || undefined, invoiceNo: invoiceNo || undefined, notes: notes || undefined,
      ...(isMaterials && computed != null ? { quantity: qty as number, unit, unitPrice: unitPrice as number } : {}),
      ...(isMachinery ? { machineType: machine || undefined, usageFrom: from || undefined, usageTo: to || undefined } : {}),
      ...(isElectricity && period ? { billingPeriod: period } : {}),
    }
    setBusy(true)
    try {
      let warning: string | undefined
      let savedId: string
      if (edit) {
        warning = (await updateExpense(edit.id, rec)).warning
        savedId = edit.id
        toast.success('Expense updated', { description: `${rec.description} · ${formatLKR(total)}` })
      } else {
        const payAmt = pay === 'full' ? total : pay === 'part' ? (paid as number) : 0
        const added = await addExpense(rec, payAmt ? { requestId, date, amount: payAmt, method } : undefined)
        warning = added.warning
        savedId = added.id
        toast.success('Expense recorded', { description: `${formatLKR(total)} · ${scope === 'Project' ? project?.code : 'Company-level'} · ${pay === 'full' ? 'paid' : pay === 'part' ? `${formatLKR(payAmt)} paid` : 'unpaid'}` })
      }
      // saved anyway; the server only points out it may already be on a supplier invoice
      if (warning) toast.warning('Check for a duplicate', { description: warning })
      if (file?.file) {
        // the expense is in; if only the upload fails, say so and keep it
        await uploadExpenseAttachment(savedId, file.file).catch((e) => toast.warning('Expense saved, receipt not uploaded', { description: errorMessage(e) }))
      }
      onOpenChange(false)
    } catch (e) {
      toast.error('Expense not saved', { description: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{edit ? 'Edit expense' : 'Record Expense'}</DialogTitle>
          <DialogDescription>Every expense belongs either to one project or to the company. The expense and its payments are recorded separately.</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Classification" required className="sm:col-span-2">
            <RadioGroup value={scope} onValueChange={(v) => setScope(v as ExpenseScope)} className="grid gap-2 sm:grid-cols-2">
              {(['Project', 'Company'] as const).map((s) => (
                <Label key={s} className="has-[[data-state=checked]]:border-primary-text has-[[data-state=checked]]:bg-primary-soft/60 flex cursor-pointer items-start gap-2.5 rounded-md border p-3 font-normal">
                  <RadioGroupItem value={s} className="mt-0.5" />
                  <span className="grid gap-0.5"><span className="font-medium">{s === 'Project' ? 'Project expense' : 'Company-level expense'}</span><span className="text-muted-foreground text-xs">{s === 'Project' ? 'Counts in that project’s profitability' : 'Marketing, office and general costs — not assigned to any project'}</span></span>
                </Label>
              ))}
            </RadioGroup>
          </Field>
          {scope === 'Project' && (
            <Field label="Project" required error={tried ? errors.project : undefined}>
              <SelectField value={pid} onChange={setPid} invalid={tried && !!errors.project} placeholder="Select project" options={projects.filter((p) => p.status !== 'Cancelled').map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }))} />
            </Field>
          )}
          <Field label="Category" required error={tried && errors.category && !AUTO[category] ? errors.category : undefined} hint={scope === 'Project' && project ? `${group} categories` : undefined}>
            <SelectField value={category} onChange={setCategory} invalid={tried && !category} placeholder="Select category" options={categories} />
          </Field>
          {AUTO[category] && <p className="bg-muted text-muted-foreground flex gap-2 rounded-md p-3 text-sm sm:col-span-2"><Info className="mt-0.5 size-4 shrink-0" />{AUTO[category]}</p>}
          {electricityHint && <p className="bg-primary-soft text-primary-soft-foreground flex gap-2 rounded-md p-3 text-sm sm:col-span-2"><Info className="mt-0.5 size-4 shrink-0" />{electricityHint}</p>}

          <Field label={isMaterials ? 'Item' : 'Description'} required error={tried ? errors.description : undefined} className="sm:col-span-2">
            <Input value={description} onChange={(e) => setDescription(e.target.value)} aria-invalid={(tried && !!errors.description) || undefined} placeholder={isMaterials ? 'e.g. River sand' : isMachinery ? 'e.g. Excavator — septic pit' : 'What was it for?'} />
          </Field>
          {isMaterials ? (
            <>
              <Field label="Supplier" optional={!!payee} hint="Credit purchases with many lines are easier as a supplier invoice.">
                <div className="flex gap-2">
                  <SelectField value={supplierId} onChange={setSupplierId} placeholder="Select supplier" className="min-w-0 flex-1" options={suppliers.map((s) => ({ value: s.id, label: s.name }))} />
                  {/* a shop that isn't on the list yet: add it here and it's selected straight away */}
                  <Button type="button" variant="outline" onClick={() => setNewSupplierOpen(true)}><Plus />New</Button>
                </div>
              </Field>
              <Field label="Quantity" required>
                <div className="flex gap-2">
                  <Input type="number" min={0} className="text-right" value={qty} onChange={(e) => setQty(e.target.value === '' ? '' : Number(e.target.value))} />
                  <SelectField value={unit} onChange={setUnit} options={settings.units} className="w-28" />
                </div>
              </Field>
              <Field label="Unit price" required><AmountInput value={unitPrice} onChange={setUnitPrice} /></Field>
              <Field label="Total"><div className="bg-muted/60 tabular flex h-9 items-center justify-end rounded-md px-3 text-sm font-medium">{formatLKR(computed ?? 0)}</div></Field>
            </>
          ) : (
            <>
              <Field label={isMachinery ? 'Provider' : 'Paid to'} required error={tried ? errors.payee : undefined}><Input value={payee} onChange={(e) => setPayee(e.target.value)} aria-invalid={(tried && !!errors.payee) || undefined} /></Field>
              <Field label={isMachinery ? 'Rental amount' : 'Amount'} required error={tried ? errors.amount : undefined}><AmountInput value={amount} onChange={setAmount} invalid={tried && !!errors.amount} /></Field>
            </>
          )}
          {isMachinery && (
            <>
              <Field label="Machine type" optional><Input value={machine} onChange={(e) => setMachine(e.target.value)} placeholder="e.g. JCB 3CX backhoe" /></Field>
              <Field label="Usage period" optional error={tried ? errors.period : undefined}>
                <div className="grid grid-cols-2 gap-2"><DatePicker value={from} onChange={setFrom} placeholder="From" /><DatePicker value={to} onChange={setTo} placeholder="To" /></div>
              </Field>
            </>
          )}
          {isElectricity && <Field label="Billing period" optional><Input value={period} onChange={(e) => setPeriod(e.target.value)} placeholder="e.g. Sep 2026" /></Field>}
          <Field label={isMaterials ? 'Invoice date' : 'Date'} required><DatePicker value={date} onChange={setDate} /></Field>
          <Field label="Invoice / bill no." optional><Input value={invoiceNo} onChange={(e) => setInvoiceNo(e.target.value)} /></Field>
          <Field label="Payment due" optional hint="For credit purchases and unpaid bills."><DatePicker value={dueDate} onChange={setDueDate} placeholder="No due date" /></Field>
          <Field label="Receipt or invoice" optional hint="PDF, JPG or PNG — a phone photo of the bill is fine.">
            <input ref={fileRef} type="file" hidden accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" onChange={(e) => { const f = e.target.files?.[0]; if (f) setFile({ fileName: f.name, size: f.size, file: f }); e.target.value = '' }} />
            {file ? (
              <div className="flex h-9 items-center gap-2 rounded-md border px-3 text-sm"><Paperclip className="text-muted-foreground size-4" /><span className="flex-1 truncate">{file.fileName}</span><span className="text-muted-foreground text-xs">{formatBytes(file.size)}</span>{file.file && <button type="button" aria-label="Remove attachment" onClick={() => setFile(edit?.attachment)}><X className="size-4" /></button>}</div>
            ) : <Button variant="outline" onClick={() => fileRef.current?.click()}><Paperclip />Attach file</Button>}
            {file && !file.file && <Button variant="link" size="sm" className="h-auto justify-self-start px-0" onClick={() => fileRef.current?.click()}>Replace file</Button>}
          </Field>

          {!edit && (
            <Field label="Payment" required className="sm:col-span-2" error={tried ? errors.paid : undefined}>
              <div className="grid gap-3 rounded-md border p-3">
                <RadioGroup value={pay} onValueChange={(v) => setPay(v as typeof pay)} className="flex flex-wrap gap-x-6 gap-y-2">
                  {[['full', 'Paid in full'], ['part', 'Partly paid'], ['unpaid', 'Not paid yet']].map(([v, l]) => <Label key={v} className="font-normal"><RadioGroupItem value={v} />{l}</Label>)}
                </RadioGroup>
                {pay !== 'unpaid' && (
                  <div className="grid gap-3 sm:grid-cols-2">
                    {pay === 'part' && <AmountInput value={paid} onChange={setPaid} invalid={tried && !!errors.paid} />}
                    <SelectField value={method} onChange={setMethod} options={settings.paymentMethods} />
                  </div>
                )}
              </div>
            </Field>
          )}
          <Field label="Notes" optional className="sm:col-span-2"><Textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
        </div>
        <SupplierDialog open={newSupplierOpen} onOpenChange={setNewSupplierOpen} onCreated={setSupplierId} />
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button onClick={save} disabled={!!AUTO[category] || busy}>{edit ? 'Save changes' : `Record Expense${total ? ` · ${formatLKR(total)}` : ''}`}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
