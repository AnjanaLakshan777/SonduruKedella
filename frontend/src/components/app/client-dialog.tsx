import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { useStore } from '@/store/store'
import type { Client } from '@/data/types'
import { errorMessage } from '@/lib/api'
import { Field, SelectField } from './form'

export function ClientDialog({ open, onOpenChange, edit }: { open: boolean; onOpenChange: (o: boolean) => void; edit?: Client }) {
  const { addClient, updateClient } = useStore()
  const [c, setC] = useState({ name: '', kind: 'Individual' as Client['kind'], phone: '', email: '', address: '', city: 'Anuradhapura', notes: '' })
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (open) { setTried(false); setC(edit ? { name: edit.name, kind: edit.kind, phone: edit.phone, email: edit.email ?? '', address: edit.address, city: edit.city, notes: edit.notes ?? '' } : { name: '', kind: 'Individual', phone: '', email: '', address: '', city: 'Anuradhapura', notes: '' }) } }, [open, edit])
  const err = { name: !c.name.trim() ? 'Enter the client’s name.' : undefined, phone: !/^[0-9 +]{9,15}$/.test(c.phone.trim()) ? 'Enter a phone number, e.g. 071 234 5678.' : undefined, email: c.email && !/^\S+@\S+\.\S+$/.test(c.email) ? 'Check the email address.' : undefined }
  async function save() {
    setTried(true)
    if (Object.values(err).some(Boolean)) return
    const rec = { ...c, name: c.name.trim(), phone: c.phone.trim(), email: c.email || undefined, notes: c.notes || undefined }
    setBusy(true)
    try {
      if (edit) await updateClient(edit.id, rec)
      else await addClient(rec)
      toast.success(edit ? 'Client updated' : 'Client added', { description: rec.name })
      onOpenChange(false)
    } catch (e) {
      toast.error(edit ? 'Client not updated' : 'Client not added', { description: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader><DialogTitle>{edit ? 'Edit client' : 'Add client'}</DialogTitle><DialogDescription>Contact details used on projects and receivables.</DialogDescription></DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Name" required error={tried ? err.name : undefined} className="sm:col-span-2"><Input value={c.name} onChange={(e) => setC({ ...c, name: e.target.value })} aria-invalid={(tried && !!err.name) || undefined} /></Field>
          <Field label="Client type"><SelectField value={c.kind} onChange={(v) => setC({ ...c, kind: v as Client['kind'] })} options={['Individual', 'Business']} /></Field>
          <Field label="Phone" required error={tried ? err.phone : undefined}><Input value={c.phone} inputMode="tel" onChange={(e) => setC({ ...c, phone: e.target.value })} aria-invalid={(tried && !!err.phone) || undefined} /></Field>
          <Field label="Email" optional error={tried ? err.email : undefined}><Input type="email" value={c.email} onChange={(e) => setC({ ...c, email: e.target.value })} /></Field>
          <Field label="Town / city"><Input value={c.city} onChange={(e) => setC({ ...c, city: e.target.value })} /></Field>
          <Field label="Address" optional className="sm:col-span-2"><Input value={c.address} onChange={(e) => setC({ ...c, address: e.target.value })} /></Field>
          <Field label="Notes" optional className="sm:col-span-2"><Textarea rows={2} value={c.notes} onChange={(e) => setC({ ...c, notes: e.target.value })} /></Field>
        </div>
        <DialogFooter><Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button><Button onClick={save} disabled={busy}>{edit ? 'Save changes' : 'Add client'}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
