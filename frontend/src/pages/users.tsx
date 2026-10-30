import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Ellipsis, KeyRound, Pencil, Plus, ShieldCheck, UserCog, UserRoundX, UserRoundCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from '@/components/ui/alert-dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { StatusBadge } from '@/components/app/status-badge'
import { CellMain } from '@/components/app/data-table'
import { EmptyState } from '@/components/app/empty-state'
import { Field } from '@/components/app/form'
import { apiGetAll, apiPost, apiPut, errorMessage } from '@/lib/api'
import { formatDate } from '@/lib/dates'
import { useAuth } from '@/store/auth'

type Role = 'SUPER_ADMIN' | 'ADMIN'
interface User { id: number; username: string; displayName: string; email: string; role: Role; enabled: boolean; createdAt: string; version: number }

const ROLE_LABEL: Record<Role, string> = { SUPER_ADMIN: 'Super Admin', ADMIN: 'Admin' }
// same rule as the server (PasswordPolicy); it checks again anyway
const PASSWORD_HINT = 'At least 12 characters.'
const passwordError = (p: string) => (p.length < 12 ? PASSWORD_HINT : undefined)

/** Super Admin only (the server enforces it too). Users are never deleted, only deactivated. */
export default function UsersPage() {
  useCrumbs([{ label: 'Users' }])
  const me = useAuth((s) => s.user)
  const [users, setUsers] = useState<User[]>()
  const [error, setError] = useState<string>()
  const [form, setForm] = useState<{ open: boolean; user?: User }>({ open: false })
  const [resetFor, setResetFor] = useState<User>()
  const [confirm, setConfirm] = useState<{ user: User; kind: 'role' | 'status' }>()

  const reload = useCallback(() => apiGetAll<User>('/users?sort=displayName').then(setUsers).catch((e) => setError(errorMessage(e))), [])
  useEffect(() => { if (me?.role === 'SUPER_ADMIN') reload() }, [me?.role, reload])

  if (me?.role !== 'SUPER_ADMIN') {
    return <EmptyState icon={ShieldCheck} title="Super Admins only" description="Ask a Super Admin to add or change user accounts." />
  }
  const activeSupers = (users ?? []).filter((u) => u.role === 'SUPER_ADMIN' && u.enabled).length

  async function applyConfirm() {
    if (!confirm) return
    const { user: u, kind } = confirm
    try {
      if (kind === 'role') await apiPut(`/users/${u.id}/role`, { role: u.role === 'ADMIN' ? 'SUPER_ADMIN' : 'ADMIN', version: u.version })
      else await apiPut(`/users/${u.id}/status`, { enabled: !u.enabled, version: u.version })
      toast.success(kind === 'role' ? 'Role changed' : u.enabled ? 'User deactivated' : 'User activated', { description: `${u.displayName} is signed out everywhere.` })
      await reload()
    } catch (e) {
      toast.error('Not changed', { description: errorMessage(e) })
    }
  }

  return (
    <>
      <PageHeader title="Users" description="Who can sign in, and what they can do. Super Admins also manage users and the commission policy; Admins do the day-to-day work."
        actions={<Button onClick={() => setForm({ open: true })}><Plus />Add user</Button>} />
      {error && <p className="text-destructive text-sm">{error}</p>}
      <Card className="gap-0 py-0">
        <Table>
          <TableHeader><TableRow><TableHead className="pl-6">Name</TableHead><TableHead className="hidden md:table-cell">Email</TableHead><TableHead>Role</TableHead><TableHead>Status</TableHead><TableHead className="hidden lg:table-cell">Added</TableHead><TableHead className="w-12 pr-4" /></TableRow></TableHeader>
          <TableBody>
            {(users ?? []).map((u) => {
              const self = u.id === me.id
              // the server keeps at least one active Super Admin; don't offer what it would refuse
              const lastSuper = u.role === 'SUPER_ADMIN' && u.enabled && activeSupers <= 1
              return (
                <TableRow key={u.id} className={u.enabled ? undefined : 'text-muted-foreground'}>
                  <TableCell className="pl-6"><CellMain title={<span>{u.displayName}{self && <span className="text-muted-foreground font-normal"> (you)</span>}</span>} sub={u.username} /></TableCell>
                  <TableCell className="hidden md:table-cell">{u.email}</TableCell>
                  <TableCell><StatusBadge status={ROLE_LABEL[u.role]} /></TableCell>
                  <TableCell><StatusBadge status={u.enabled ? 'Active' : 'Inactive'} /></TableCell>
                  <TableCell className="hidden lg:table-cell">{formatDate(u.createdAt.slice(0, 10))}</TableCell>
                  <TableCell className="pr-4">
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${u.displayName}`}><Ellipsis /></Button></DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onSelect={() => setForm({ open: true, user: u })}><Pencil />Edit name and email</DropdownMenuItem>
                        <DropdownMenuItem disabled={self || lastSuper} onSelect={() => setConfirm({ user: u, kind: 'role' })}><UserCog />{u.role === 'ADMIN' ? 'Make Super Admin' : 'Make Admin'}</DropdownMenuItem>
                        <DropdownMenuItem disabled={self} onSelect={() => setResetFor(u)}><KeyRound />Reset password</DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem disabled={self || lastSuper} className={u.enabled ? 'text-destructive' : undefined} onSelect={() => setConfirm({ user: u, kind: 'status' })}>
                          {u.enabled ? <><UserRoundX />Deactivate</> : <><UserRoundCheck />Activate</>}
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              )
            })}
            {users && !users.length && <TableRow><TableCell colSpan={6} className="text-muted-foreground py-10 text-center">No users.</TableCell></TableRow>}
          </TableBody>
        </Table>
      </Card>
      <p className="text-muted-foreground text-xs">You can't change your own role or deactivate yourself, and there is always at least one active Super Admin. Changing someone's role, status or password signs them out everywhere.</p>

      <UserFormDialog state={form} onClose={() => setForm({ open: false })} onSaved={reload} />
      <ResetPasswordDialog user={resetFor} onClose={() => setResetFor(undefined)} />
      <AlertDialog open={!!confirm} onOpenChange={(o) => !o && setConfirm(undefined)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {confirm?.kind === 'role'
                ? `Make ${confirm.user.displayName} ${confirm.user.role === 'ADMIN' ? 'a Super Admin' : 'an Admin'}?`
                : `${confirm?.user.enabled ? 'Deactivate' : 'Activate'} ${confirm?.user.displayName}?`}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {confirm?.kind === 'role'
                ? confirm.user.role === 'ADMIN' ? 'Super Admins can also manage users and change the commission policy.' : 'They will no longer manage users or the commission policy.'
                : confirm?.user.enabled ? 'They are signed out at once and can no longer sign in. Everything they recorded stays.' : 'They can sign in again with their current password.'}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={applyConfirm}>Confirm</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}

function UserFormDialog({ state, onClose, onSaved }: { state: { open: boolean; user?: User }; onClose: () => void; onSaved: () => Promise<unknown> }) {
  const edit = state.user
  const [f, setF] = useState({ username: '', displayName: '', email: '', password: '', role: 'ADMIN' as Role })
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    if (!state.open) return
    setTried(false)
    setF(edit ? { username: edit.username, displayName: edit.displayName, email: edit.email, password: '', role: edit.role } : { username: '', displayName: '', email: '', password: '', role: 'ADMIN' })
  }, [state, edit])
  const err = {
    username: !edit && !/^[a-zA-Z0-9][a-zA-Z0-9._-]{2,49}$/.test(f.username.trim()) ? '3 to 50 letters, digits, dots, dashes or underscores.' : undefined,
    displayName: !f.displayName.trim() ? 'Enter the person\'s name.' : undefined,
    email: !/^\S+@\S+\.\S+$/.test(f.email.trim()) ? 'Enter an email address, e.g. name@example.com.' : undefined,
    password: !edit ? passwordError(f.password) : undefined,
  }
  async function save() {
    setTried(true)
    if (Object.values(err).some(Boolean)) return
    setBusy(true)
    try {
      if (edit) await apiPut(`/users/${edit.id}`, { displayName: f.displayName.trim(), email: f.email.trim(), version: edit.version })
      else await apiPost('/users', { username: f.username.trim(), displayName: f.displayName.trim(), email: f.email.trim(), password: f.password, role: f.role })
      toast.success(edit ? 'User updated' : 'User added', { description: edit ? f.displayName : `Give ${f.displayName.trim()} their password in person; they can change it after signing in.` })
      await onSaved()
      onClose()
    } catch (e) {
      toast.error(edit ? 'User not updated' : 'User not added', { description: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }
  return (
    <Dialog open={state.open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader><DialogTitle>{edit ? 'Edit user' : 'Add user'}</DialogTitle><DialogDescription>{edit ? 'The username can\'t change. Role, status and password have their own actions.' : 'They sign in with the username or the email.'}</DialogDescription></DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Name" required error={tried ? err.displayName : undefined}><Input value={f.displayName} onChange={(e) => setF({ ...f, displayName: e.target.value })} /></Field>
          <Field label="Username" required={!edit} error={tried ? err.username : undefined}><Input value={f.username} disabled={!!edit} autoComplete="off" onChange={(e) => setF({ ...f, username: e.target.value })} /></Field>
          <Field label="Email" required className="sm:col-span-2" error={tried ? err.email : undefined}><Input type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></Field>
          {!edit && (
            <>
              <Field label="Password" required className="sm:col-span-2" hint={PASSWORD_HINT} error={tried ? err.password : undefined}><Input type="password" autoComplete="new-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} /></Field>
              <Field label="Role" required className="sm:col-span-2">
                <RadioGroup value={f.role} onValueChange={(v) => setF({ ...f, role: v as Role })} className="flex gap-6">
                  <Label className="font-normal"><RadioGroupItem value="ADMIN" />Admin</Label>
                  <Label className="font-normal"><RadioGroupItem value="SUPER_ADMIN" />Super Admin</Label>
                </RadioGroup>
              </Field>
            </>
          )}
        </div>
        <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={save} disabled={busy}>{edit ? 'Save changes' : 'Add user'}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function ResetPasswordDialog({ user, onClose }: { user?: User; onClose: () => void }) {
  const [password, setPassword] = useState('')
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (user) { setPassword(''); setTried(false) } }, [user])
  const err = passwordError(password)
  return (
    <Dialog open={!!user} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader><DialogTitle>Reset {user?.displayName}'s password</DialogTitle><DialogDescription>They are signed out everywhere. Give them the new password in person; they can change it after signing in.</DialogDescription></DialogHeader>
        <Field label="New password" required hint={PASSWORD_HINT} error={tried ? err : undefined}><Input type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button disabled={busy} onClick={async () => {
            setTried(true)
            if (err || !user) return
            setBusy(true)
            try {
              await apiPut(`/users/${user.id}/password`, { newPassword: password })
              toast.success('Password reset', { description: `${user.displayName} is signed out everywhere.` })
              onClose()
            } catch (e) {
              toast.error('Password not reset', { description: errorMessage(e) })
            } finally {
              setBusy(false)
            }
          }}>Reset password</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
