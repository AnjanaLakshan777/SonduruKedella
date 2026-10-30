import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { apiPut, errorMessage } from '@/lib/api'
import { Field } from './form'

/** Your own password. This session stays signed in; your other sessions end. */
export function ChangePasswordDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [again, setAgain] = useState('')
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (open) { setCurrent(''); setNext(''); setAgain(''); setTried(false) } }, [open])
  const err = {
    current: !current ? 'Enter your current password.' : undefined,
    next: next.length < 12 ? 'At least 12 characters.' : next === current ? 'Choose a different password.' : undefined,
    again: again !== next ? 'The two new passwords don\'t match.' : undefined,
  }
  async function save() {
    setTried(true)
    if (Object.values(err).some(Boolean)) return
    setBusy(true)
    try {
      await apiPut('/auth/me/password', { currentPassword: current, newPassword: next })
      toast.success('Password changed', { description: 'You stay signed in here; other sessions were signed out.' })
      onOpenChange(false)
    } catch (e) {
      // a wrong current password counts toward the sign-in limit, the server says when to try again
      toast.error('Password not changed', { description: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader><DialogTitle>Change password</DialogTitle><DialogDescription>Use at least 12 characters, and not one you use anywhere else.</DialogDescription></DialogHeader>
        <div className="grid gap-4">
          <Field label="Current password" required error={tried ? err.current : undefined}><Input type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} /></Field>
          <Field label="New password" required error={tried ? err.next : undefined}><Input type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} /></Field>
          <Field label="New password again" required error={tried ? err.again : undefined}><Input type="password" autoComplete="new-password" value={again} onChange={(e) => setAgain(e.target.value)} /></Field>
        </div>
        <DialogFooter><Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button><Button onClick={save} disabled={busy}>Change password</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
