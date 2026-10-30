import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { errorMessage } from '@/lib/api'
import { Field } from './form'

/**
 * Voids, reverses or cancels one record. Nothing is deleted: the record stays in the history with the
 * reason and simply stops counting. verb/description let it read right for each kind of record.
 */
export function VoidDialog({ target, onClose, onVoid, verb = 'Void', description, placeholder = 'e.g. Recorded against the wrong project' }: {
  target?: { label: string }
  onClose: () => void
  onVoid: (reason: string) => Promise<void>
  verb?: string
  description?: string
  placeholder?: string
}) {
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (target) setReason('') }, [target])
  return (
    <Dialog open={!!target} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{verb} {target?.label}?</DialogTitle>
          <DialogDescription>{description ?? 'It stays in the history, marked void, and stops counting. Record the right one afterwards if needed.'}</DialogDescription>
        </DialogHeader>
        <Field label="Reason" required><Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder={placeholder} /></Field>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button variant="destructive" disabled={busy || !reason.trim()} onClick={async () => {
            setBusy(true)
            try {
              await onVoid(reason.trim())
              toast.success(`${verb} done`)
              onClose()
            } catch (e) {
              toast.error(`Not ${verb.toLowerCase()}ed`, { description: errorMessage(e) })
            } finally {
              setBusy(false)
            }
          }}>{verb}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
