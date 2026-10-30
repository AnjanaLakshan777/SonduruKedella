import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { TriangleAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { Checkbox } from '@/components/ui/checkbox'
import { useStore } from '@/store/store'
import type { InstallmentView } from '@/lib/finance'
import { TODAY } from '@/lib/dates'
import { formatLKR } from '@/lib/format'
import { errorMessage } from '@/lib/api'
import { AmountInput, DatePicker, Field } from './form'
import { FigureList } from './figure-list'

/** Bad debt write-off: asks for amount, effective date and reason, and states that it is not money received. */
export function WriteOffDialog({ view, open, onOpenChange }: { view?: InstallmentView; open: boolean; onOpenChange: (o: boolean) => void }) {
  const writeOff = useStore((s) => s.writeOff)
  const [amount, setAmount] = useState<number | ''>('')
  const [date, setDate] = useState(TODAY)
  const [reason, setReason] = useState('')
  const [ack, setAck] = useState(false)
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (open && view) { setAmount(view.remaining); setDate(TODAY); setReason(''); setAck(false); setTried(false) } }, [open, view])
  if (!view) return null
  const amt = typeof amount === 'number' ? amount : 0
  const amountError = !(amt > 0) ? 'Enter the amount to write off.' : amt > view.remaining + 0.001 ? `Cannot exceed the remaining ${formatLKR(view.remaining)}.` : undefined
  const reasonError = reason.trim().length < 10 ? 'Give the reason the amount cannot be collected (at least a sentence).' : undefined
  async function submit() {
    setTried(true)
    if (amountError || reasonError || !ack) return
    setBusy(true)
    try {
      await writeOff({ installmentId: view!.inst.id, date, amount: amt, reason: reason.trim() })
      toast.success('Bad debt written off', { description: `${formatLKR(amt)} for ${view!.client.name}. It is recorded as a write-off, not as a payment.` })
      onOpenChange(false)
    } catch (e) {
      toast.error('Not written off', { description: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Write off {formatLKR(amt)} as bad debt?</DialogTitle>
          <DialogDescription>
            {view.client.name} · {view.project.name} ({view.project.code}) · {view.inst.description}. A write-off is not a payment: it will not appear as money received.
          </DialogDescription>
        </DialogHeader>
        <FigureList rows={[
          { label: 'Amount due', value: view.net, note: view.deduction ? `After delay deduction of ${formatLKR(view.deduction)}` : undefined },
          { kind: 'less', label: 'Payments received', value: view.paid },
          ...(view.writtenOff ? [{ kind: 'less' as const, label: 'Already written off', value: view.writtenOff }] : []),
          { kind: 'less', label: 'This write-off', value: amt },
          { kind: 'total', label: 'Remaining receivable', value: Math.max(0, view.remaining - amt) },
        ]} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Amount to write off" required error={tried ? amountError : undefined}><AmountInput value={amount} onChange={setAmount} invalid={tried && !!amountError} /></Field>
          <Field label="Effective date" required><DatePicker value={date} onChange={setDate} /></Field>
          <Field label="Reason" required className="sm:col-span-2" error={tried ? reasonError : undefined}>
            <Textarea value={reason} onChange={(e) => setReason(e.target.value)} aria-invalid={(tried && !!reasonError) || undefined} placeholder="e.g. Client unreachable since August; agreed with the accountant that the balance is uncollectible." />
          </Field>
        </div>
        <label className="bg-warning-soft text-warning flex items-start gap-3 rounded-md p-3 text-sm">
          <Checkbox checked={ack} onCheckedChange={(v) => setAck(v === true)} className="mt-0.5 bg-card" aria-invalid={(tried && !ack) || undefined} />
          <span className="grid gap-0.5">
            <span className="flex items-center gap-1.5 font-medium"><TriangleAlert className="size-4" />I understand this is a formal write-off</span>
            <span>The amount leaves receivables and reduces the project's result. It can only be reversed by recording a correcting entry.</span>
          </span>
        </label>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button variant="destructive" onClick={submit} disabled={busy}>Write off {formatLKR(amt)}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
