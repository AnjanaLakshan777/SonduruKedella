import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { useStore } from '@/store/store'
import { errorMessage } from '@/lib/api'
import { Field } from './form'

/**
 * "Add supplier". Used on the Suppliers page and from the expense form, where a purchase can come from a
 * shop that isn't on the list yet; onCreated gets the new supplier's id so the form can select it.
 */
export function SupplierDialog({ open, onOpenChange, onCreated, defaultName = '' }: {
  open: boolean
  onOpenChange: (o: boolean) => void
  onCreated?: (id: string) => void
  defaultName?: string
}) {
  const { addSupplier, settings } = useStore()
  const blank = { name: defaultName, contactPerson: '', phone: '', email: '', address: '', supplies: '', creditDays: settings.supplierCreditDays }
  const [f, setF] = useState(blank)
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (open) { setTried(false); setF(blank) } }, [open, settings.supplierCreditDays, defaultName]) // eslint-disable-line react-hooks/exhaustive-deps
  const err = { name: !f.name.trim() ? 'Enter the supplier name.' : undefined, phone: !f.phone.trim() ? 'Enter a phone number.' : undefined }
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader><DialogTitle>Add supplier</DialogTitle><DialogDescription>The credit period sets the default due date on new invoices.</DialogDescription></DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Supplier name" required error={tried ? err.name : undefined} className="sm:col-span-2"><Input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
          <Field label="Contact person" optional><Input value={f.contactPerson} onChange={(e) => setF({ ...f, contactPerson: e.target.value })} /></Field>
          <Field label="Phone" required error={tried ? err.phone : undefined}><Input value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} /></Field>
          <Field label="Email" optional><Input type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></Field>
          <Field label="Credit period"><div className="flex items-center gap-2"><Input type="number" min={0} className="w-24 text-right" value={f.creditDays} onChange={(e) => setF({ ...f, creditDays: Number(e.target.value) })} /><span className="text-muted-foreground text-sm">days</span></div></Field>
          <Field label="What they supply" optional className="sm:col-span-2"><Input value={f.supplies} onChange={(e) => setF({ ...f, supplies: e.target.value })} placeholder="e.g. Bricks, sand, cement" /></Field>
          <Field label="Address" optional className="sm:col-span-2"><Input value={f.address} onChange={(e) => setF({ ...f, address: e.target.value })} /></Field>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={busy} onClick={async () => {
            setTried(true)
            if (err.name || err.phone) return
            setBusy(true)
            try {
              const id = await addSupplier({ ...f, name: f.name.trim(), email: f.email || undefined })
              toast.success('Supplier added', { description: f.name })
              onCreated?.(id)
              onOpenChange(false)
            } catch (e) {
              toast.error('Supplier not added', { description: errorMessage(e) })
            } finally {
              setBusy(false)
            }
          }}>Add supplier</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
