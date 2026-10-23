import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { useStore } from '@/store/store'
import { TODAY } from '@/lib/dates'
import { formatLKR } from '@/lib/format'
import { AmountInput, DatePicker, Field, SelectField } from './form'
import { FigureList, type FigureRow } from './figure-list'

export interface PaymentInput { date: string; amount: number; method: string; reference?: string; notes?: string }

/**
 * Records money moving against one record (installment, invoice, expense, commission…).
 * Partial payments are allowed; the remaining amount is recalculated from the records.
 */
export function PaymentDialog({
  open, onOpenChange, title, description, context, outstanding, verb = 'Record Payment', onSubmit, successTitle = 'Payment recorded', allowNotes = true, direction = 'in',
}: {
  open: boolean
  onOpenChange: (o: boolean) => void
  title: string
  description?: React.ReactNode
  context?: FigureRow[]
  outstanding: number
  verb?: string
  onSubmit: (p: PaymentInput) => void
  successTitle?: string
  allowNotes?: boolean
  direction?: 'in' | 'out'
}) {
  const methods = useStore((s) => s.settings.paymentMethods)
  const [date, setDate] = useState(TODAY)
  const [amount, setAmount] = useState<number | ''>('')
  const [method, setMethod] = useState('Bank Transfer')
  const [reference, setReference] = useState('')
  const [notes, setNotes] = useState('')
  const [tried, setTried] = useState(false)
  useEffect(() => { if (open) { setDate(TODAY); setAmount(outstanding > 0 ? outstanding : ''); setMethod('Bank Transfer'); setReference(''); setNotes(''); setTried(false) } }, [open, outstanding])

  const amountError = amount === '' || !(amount > 0) ? 'Enter the amount received.' : amount > outstanding + 0.001 ? `More than the remaining ${formatLKR(outstanding)}. Record only what is owed.` : undefined
  const after = typeof amount === 'number' ? Math.max(0, outstanding - amount) : outstanding
  function submit() {
    setTried(true)
    if (amountError || !method || !date) return
    onSubmit({ date, amount: amount as number, method, reference: reference || undefined, notes: notes || undefined })
    toast.success(successTitle, { description: `${formatLKR(amount as number)} ${direction === 'in' ? 'received' : 'paid'}. Remaining ${formatLKR(after)}.` })
    onOpenChange(false)
  }
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        {context && <FigureList rows={context} />}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={direction === 'in' ? 'Amount received' : 'Amount paid'} required error={tried ? amountError : undefined} hint={`Remaining ${formatLKR(outstanding)} — partial payments are fine.`}>
            <AmountInput value={amount} onChange={setAmount} invalid={tried && !!amountError} />
          </Field>
          <Field label="Payment date" required><DatePicker value={date} onChange={setDate} /></Field>
          <Field label="Payment method" required><SelectField value={method} onChange={setMethod} options={methods} /></Field>
          <Field label="Reference" optional><Input value={reference} onChange={(e) => setReference(e.target.value)} placeholder="Receipt, cheque or transfer no." /></Field>
          {allowNotes && <Field label="Note" optional className="sm:col-span-2"><Textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} /></Field>}
        </div>
        <div className="bg-muted/60 flex items-center justify-between rounded-md px-3 py-2 text-sm">
          <span className="text-muted-foreground">Remaining after this payment</span>
          <span className="tabular font-semibold">{formatLKR(after)}</span>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button onClick={submit}>{verb}{typeof amount === 'number' && amount > 0 ? ` · ${formatLKR(amount)}` : ''}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
