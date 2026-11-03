import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Ellipsis, KeyRound, Pencil, Plus, ShieldCheck, UserCog, UserRoundX, UserRoundCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
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
import { PasswordInput } from '@/components/app/password-input'
import { PAGE_ITEMS } from '@/components/app/nav'
import { apiGetAll, apiPost, apiPut, errorMessage } from '@/lib/api'
import { formatDate } from '@/lib/dates'
import { useAuth, type AppPage } from '@/store/auth'

type Role = 'SUPER_ADMIN' | 'ADMIN' | 'CUSTOM'
// roleName: Super Admin, Admin or the "Other" name; pages: what they can open (all of them for a Super Admin)
interface User { id: number; username: string; displayName: string; email: string; role: Role; roleName: string; pages: AppPage[]; enabled: boolean; createdAt: string; version: number }

const ALL_PAGES = PAGE_ITEMS.flatMap((it) => (it.page ? [it.page] : []))
const pageLabels = (pages: AppPage[]) => (pages.length === ALL_PAGES.length ? 'Every page' : PAGE_ITEMS.filter((it) => it.page && pages.includes(it.page)).map((it) => it.label).join(', '))
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
  const [accessFor, setAccessFor] = useState<User>()
  const [resetFor, setResetFor] = useState<User>()
  const [confirm, setConfirm] = useState<User>()

  const reload = useCallback(() => apiGetAll<User>('/users?sort=displayName').then(setUsers).catch((e) => setError(errorMessage(e))), [])
  useEffect(() => { if (me?.role === 'SUPER_ADMIN') reload() }, [me?.role, reload])

  if (me?.role !== 'SUPER_ADMIN') {
    return <EmptyState icon={ShieldCheck} title="Super Admins only" description="Ask a Super Admin to add or change user accounts." />
  }
  const activeSupers = (users ?? []).filter((u) => u.role === 'SUPER_ADMIN' && u.enabled).length
  // names already given to "Other" roles, offered again when typing one
  const roleNames = [...new Set((users ?? []).filter((u) => u.role === 'CUSTOM').map((u) => u.roleName))].sort()

  async function applyStatus() {
    if (!confirm) return
    const u = confirm
    try {
      await apiPut(`/users/${u.id}/status`, { enabled: !u.enabled, version: u.version })
      toast.success(u.enabled ? 'User deactivated' : 'User activated', { description: `${u.displayName} is signed out everywhere.` })
      await reload()
    } catch (e) {
      toast.error('Not changed', { description: errorMessage(e) })
    }
  }

  return (
    <>
      <PageHeader title="Users" description="Who can sign in, and what they can open. Super Admins see everything and manage users; for everyone else you tick the pages they can use."
        actions={<Button onClick={() => setForm({ open: true })}><Plus />Add user</Button>} />
      {error && <p className="text-destructive text-sm">{error}</p>}
      <Card className="gap-0 py-0">
        <Table>
          <TableHeader><TableRow><TableHead className="pl-6">Name</TableHead><TableHead className="hidden md:table-cell">Email</TableHead><TableHead>Role</TableHead><TableHead className="hidden xl:table-cell">Pages</TableHead><TableHead>Status</TableHead><TableHead className="hidden lg:table-cell">Added</TableHead><TableHead className="w-12 pr-4" /></TableRow></TableHeader>
          <TableBody>
            {(users ?? []).map((u) => {
              const self = u.id === me.id
              // the server keeps at least one active Super Admin; don't offer what it would refuse
              const lastSuper = u.role === 'SUPER_ADMIN' && u.enabled && activeSupers <= 1
              return (
                <TableRow key={u.id} className={u.enabled ? undefined : 'text-muted-foreground'}>
                  <TableCell className="pl-6"><CellMain title={<span>{u.displayName}{self && <span className="text-muted-foreground font-normal"> (you)</span>}</span>} sub={u.username} /></TableCell>
                  <TableCell className="hidden md:table-cell">{u.email}</TableCell>
                  <TableCell><StatusBadge status={u.roleName} title={pageLabels(u.pages)} /></TableCell>
                  <TableCell className="text-muted-foreground hidden max-w-72 truncate xl:table-cell" title={pageLabels(u.pages)}>{pageLabels(u.pages)}</TableCell>
                  <TableCell><StatusBadge status={u.enabled ? 'Active' : 'Inactive'} /></TableCell>
                  <TableCell className="hidden lg:table-cell">{formatDate(u.createdAt.slice(0, 10))}</TableCell>
                  <TableCell className="pr-4">
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${u.displayName}`}><Ellipsis /></Button></DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onSelect={() => setForm({ open: true, user: u })}><Pencil />Edit details</DropdownMenuItem>
                        <DropdownMenuItem disabled={self || lastSuper} onSelect={() => setAccessFor(u)}><UserCog />Role and permissions</DropdownMenuItem>
                        <DropdownMenuItem disabled={self} onSelect={() => setResetFor(u)}><KeyRound />Reset password</DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem disabled={self || lastSuper} className={u.enabled ? 'text-destructive' : undefined} onSelect={() => setConfirm(u)}>
                          {u.enabled ? <><UserRoundX />Deactivate</> : <><UserRoundCheck />Activate</>}
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              )
            })}
            {users && !users.length && <TableRow><TableCell colSpan={7} className="text-muted-foreground py-10 text-center">No users.</TableCell></TableRow>}
          </TableBody>
        </Table>
      </Card>
      <p className="text-muted-foreground text-xs">You can't change your own role or deactivate yourself, and there is always at least one active Super Admin. A new role, status or password signs them out everywhere; new pages apply on their next click.</p>

      <UserFormDialog state={form} roleNames={roleNames} onClose={() => setForm({ open: false })} onSaved={reload} />
      <AccessDialog user={accessFor} roleNames={roleNames} onClose={() => setAccessFor(undefined)} onSaved={reload} />
      <ResetPasswordDialog user={resetFor} onClose={() => setResetFor(undefined)} />
      <AlertDialog open={!!confirm} onOpenChange={(o) => !o && setConfirm(undefined)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{`${confirm?.enabled ? 'Deactivate' : 'Activate'} ${confirm?.displayName}?`}</AlertDialogTitle>
            <AlertDialogDescription>
              {confirm?.enabled ? 'They are signed out at once and can no longer sign in. Everything they recorded stays.' : 'They can sign in again with their current password.'}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={applyStatus}>Confirm</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}

/* ---------- role and permissions: Admin, Super Admin or Other (just a name), plus this user's pages ---------- */

interface Access { role: Role; name: string; pages: AppPage[] }
const accessOf = (u?: User): Access => (u
  ? { role: u.role, name: u.role === 'CUSTOM' ? u.roleName : '', pages: u.role === 'SUPER_ADMIN' ? ALL_PAGES : u.pages }
  : { role: 'ADMIN', name: '', pages: ALL_PAGES })

interface AccessErrors { name?: string; pages?: string }
const accessErrors = (a: Access): AccessErrors => ({
  name: a.role === 'CUSTOM' && !a.name.trim() ? 'Type the role name, e.g. Supervisor.' : undefined,
  pages: a.role !== 'SUPER_ADMIN' && !a.pages.length ? 'Tick at least one page.' : undefined,
})
const hasErrors = (e: AccessErrors) => !!(e.name || e.pages)

// what the API takes on create and on PUT /users/{id}/role
const accessBody = (a: Access) => ({
  role: a.role,
  roleName: a.role === 'CUSTOM' ? a.name.trim() : undefined,
  pages: a.role === 'SUPER_ADMIN' ? undefined : a.pages,
})

function AccessFields({ value, roleNames, errors, onChange }: { value: Access; roleNames: string[]; errors?: AccessErrors; onChange: (a: Access) => void }) {
  const toggle = (page: AppPage, on: boolean) => onChange({ ...value, pages: on ? [...value.pages, page] : value.pages.filter((p) => p !== page) })
  return (
    <>
      <Field label="Role" required className="sm:col-span-2">
        <RadioGroup value={value.role} onValueChange={(v) => onChange({ ...value, role: v as Role })} className="flex flex-wrap gap-6">
          <Label className="font-normal"><RadioGroupItem value="ADMIN" />Admin</Label>
          <Label className="font-normal"><RadioGroupItem value="SUPER_ADMIN" />Super Admin</Label>
          <Label className="font-normal"><RadioGroupItem value="CUSTOM" />Other</Label>
        </RadioGroup>
      </Field>
      {value.role === 'CUSTOM' && (
        <Field label="Role name" required className="sm:col-span-2" hint="Only a name, shown under theirs. The pages below decide what they can do."
          error={errors?.name}>
          <Input value={value.name} list="role-names" placeholder="e.g. Supervisor, Auditor" maxLength={50} onChange={(e) => onChange({ ...value, name: e.target.value })} />
          <datalist id="role-names">{roleNames.map((n) => <option key={n} value={n} />)}</datalist>
        </Field>
      )}
      {value.role === 'SUPER_ADMIN' ? (
        <p className="text-muted-foreground text-sm sm:col-span-2">A Super Admin opens every page, manages users and changes the commission policy.</p>
      ) : (
        <Field label="Pages they can open" required className="sm:col-span-2" error={errors?.pages}>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {PAGE_ITEMS.map((it) => it.page && (
              <label key={it.page} className="flex items-center gap-2 text-sm">
                <Checkbox checked={value.pages.includes(it.page)} onCheckedChange={(v) => toggle(it.page!, v === true)} />
                {it.label}
              </label>
            ))}
          </div>
          <div className="flex gap-2">
            <Button type="button" variant="ghost" size="sm" onClick={() => onChange({ ...value, pages: ALL_PAGES })}>Tick all</Button>
            <Button type="button" variant="ghost" size="sm" onClick={() => onChange({ ...value, pages: [] })}>Clear</Button>
          </div>
        </Field>
      )}
    </>
  )
}

function UserFormDialog({ state, roleNames, onClose, onSaved }: { state: { open: boolean; user?: User }; roleNames: string[]; onClose: () => void; onSaved: () => Promise<unknown> }) {
  const edit = state.user
  const [f, setF] = useState({ username: '', displayName: '', email: '', password: '' })
  const [access, setAccess] = useState<Access>(accessOf())
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    if (!state.open) return
    setTried(false)
    setAccess(accessOf())
    setF(edit ? { username: edit.username, displayName: edit.displayName, email: edit.email, password: '' } : { username: '', displayName: '', email: '', password: '' })
  }, [state, edit])
  const err = {
    username: !/^[a-zA-Z0-9][a-zA-Z0-9._-]{2,49}$/.test(f.username.trim()) ? '3 to 50 letters, digits, dots, dashes or underscores.' : undefined,
    displayName: !f.displayName.trim() ? 'Enter the person\'s name.' : undefined,
    email: !/^\S+@\S+\.\S+$/.test(f.email.trim()) ? 'Enter an email address, e.g. name@example.com.' : undefined,
    password: !edit ? passwordError(f.password) : undefined,
  }
  const accessErr = accessErrors(access)
  async function save() {
    setTried(true)
    if (Object.values(err).some(Boolean) || (!edit && hasErrors(accessErr))) return
    setBusy(true)
    try {
      if (edit) await apiPut(`/users/${edit.id}`, { username: f.username.trim(), displayName: f.displayName.trim(), email: f.email.trim(), version: edit.version })
      else await apiPost('/users', { username: f.username.trim(), displayName: f.displayName.trim(), email: f.email.trim(), password: f.password, ...accessBody(access) })
      const renamed = !!edit && f.username.trim().toLowerCase() !== edit.username
      toast.success(edit ? 'User updated' : 'User added', {
        description: renamed ? `${f.displayName.trim()} is signed out; they sign in with the new username.`
          : edit ? f.displayName : `Give ${f.displayName.trim()} their password in person; they can change it after signing in.`,
      })
      // renaming yourself ends your own session too
      if (renamed && edit.id === useAuth.getState().user?.id) { await useAuth.getState().check(); return }
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
      <DialogContent className="sm:max-w-xl">
        <DialogHeader><DialogTitle>{edit ? 'Edit user' : 'Add user'}</DialogTitle><DialogDescription>{edit ? 'A new username signs them out; they sign in again with it. Role and permissions, status and password have their own actions.' : 'They sign in with the username or the email.'}</DialogDescription></DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Name" required error={tried ? err.displayName : undefined}><Input value={f.displayName} onChange={(e) => setF({ ...f, displayName: e.target.value })} /></Field>
          <Field label="Username" required error={tried ? err.username : undefined}><Input value={f.username} autoComplete="off" onChange={(e) => setF({ ...f, username: e.target.value })} /></Field>
          <Field label="Email" required className="sm:col-span-2" error={tried ? err.email : undefined}><Input type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></Field>
          {!edit && (
            <>
              <Field label="Password" required className="sm:col-span-2" hint={PASSWORD_HINT} error={tried ? err.password : undefined}><PasswordInput autoComplete="new-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} /></Field>
              <AccessFields value={access} roleNames={roleNames} errors={tried ? accessErr : undefined} onChange={setAccess} />
            </>
          )}
        </div>
        <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={save} disabled={busy}>{edit ? 'Save changes' : 'Add user'}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function AccessDialog({ user, roleNames, onClose, onSaved }: { user?: User; roleNames: string[]; onClose: () => void; onSaved: () => Promise<unknown> }) {
  const [access, setAccess] = useState<Access>(accessOf())
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (user) { setAccess(accessOf(user)); setTried(false) } }, [user])
  const errors = accessErrors(access)
  async function save() {
    setTried(true)
    if (hasErrors(errors) || !user) return
    setBusy(true)
    try {
      await apiPut(`/users/${user.id}/role`, { ...accessBody(access), version: user.version })
      const newRole = access.role !== user.role || (access.role === 'CUSTOM' && access.name.trim().replace(/\s+/g, ' ') !== user.roleName)
      toast.success('Role and permissions saved', { description: newRole ? `${user.displayName} is signed out everywhere.` : `${user.displayName} sees the change on their next click.` })
      await onSaved()
      onClose()
    } catch (e) {
      toast.error('Not saved', { description: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }
  return (
    <Dialog open={!!user} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader><DialogTitle>{user?.displayName}'s role and permissions</DialogTitle><DialogDescription>Only for this user. A new role signs them out; new pages apply on their next click.</DialogDescription></DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2"><AccessFields value={access} roleNames={roleNames} errors={tried ? errors : undefined} onChange={setAccess} /></div>
        <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={save} disabled={busy}>Save</Button></DialogFooter>
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
        <Field label="New password" required hint={PASSWORD_HINT} error={tried ? err : undefined}><PasswordInput autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
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
