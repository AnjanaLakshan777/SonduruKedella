import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { History, ShieldCheck } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { StatusBadge } from '@/components/app/status-badge'
import { EmptyState } from '@/components/app/empty-state'
import { ALL, FilterBar, FilterSelect } from '@/components/app/filters'
import { DatePicker } from '@/components/app/form'
import { CellMain, Pagination } from '@/components/app/data-table'
import { apiGet, apiGetAll, errorMessage } from '@/lib/api'
import { useAuth } from '@/store/auth'

interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
interface Change {
  id: number; occurredAt: string; userId?: number; username?: string; httpMethod?: string; path?: string; requestId?: string
  entityType: string; entityId?: string; changeType: 'CREATED' | 'UPDATED' | 'DELETED'; changes: Record<string, [string | null, string | null]>
}
interface SecurityEvent {
  id: number; occurredAt: string; eventType: string; actorUserId?: number; targetUserId?: number; username?: string; remoteAddress?: string; details?: string
}
interface UserRow { id: number; username: string; displayName: string }

const PAGE_SIZE = 50
const CHANGE_LABEL = { CREATED: 'Added', UPDATED: 'Changed', DELETED: 'Removed' } as const
const EVENT_LABEL: Record<string, string> = {
  LOGIN_SUCCEEDED: 'Signed in', LOGIN_FAILED: 'Sign-in failed', LOGIN_THROTTLED: 'Sign-in blocked (too many tries)', LOGOUT: 'Signed out',
  USER_CREATED: 'User added', USER_UPDATED: 'User details changed', ROLE_CHANGED: 'Role or permissions changed',
  USER_ACTIVATED: 'User activated', USER_DEACTIVATED: 'User deactivated', PASSWORD_CHANGED: 'Password changed',
  PASSWORD_RESET: 'Password reset', SUPER_ADMIN_BOOTSTRAPPED: 'First Super Admin created',
  CUSTOM_ROLE_CREATED: 'Role added (old)', CUSTOM_ROLE_UPDATED: 'Role changed (old)', CUSTOM_ROLE_DELETED: 'Role deleted (old)',
}
// "ClientPayment" -> "Client payment", "designCommissionPoolPct" -> "Design commission pool pct"
const words = (s: string) => {
  const w = s.replace(/([a-z0-9])([A-Z])/g, '$1 $2').toLowerCase()
  return w.charAt(0).toUpperCase() + w.slice(1)
}
const ENTITY_LABEL: Record<string, string> = { AppUser: 'User account' }
const entityLabel = (t: string) => ENTITY_LABEL[t] ?? words(t)
const when = (ts: string) => new Date(ts).toLocaleString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
const query = (params: Record<string, string | number | undefined>) =>
  Object.entries(params).filter(([, v]) => v !== undefined && v !== '').map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join('&')

/** Who changed what, and every sign-in. Super Admin only (the server enforces it too). */
export default function AuditPage() {
  useCrumbs([{ label: 'Audit history' }])
  const me = useAuth((s) => s.user)
  const { tab = 'changes' } = useParams()
  const navigate = useNavigate()
  const [users, setUsers] = useState<UserRow[]>([])
  const [types, setTypes] = useState<string[]>([])
  const [userId, setUserId] = useState(ALL)
  const [type, setType] = useState(ALL)
  const [record, setRecord] = useState<{ type: string; id: string }>()
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(1)
  const [changes, setChanges] = useState<Page<Change>>()
  const [events, setEvents] = useState<Page<SecurityEvent>>()
  const [error, setError] = useState<string>()
  const isSuper = me?.role === 'SUPER_ADMIN'

  useEffect(() => {
    if (!isSuper) return
    apiGetAll<UserRow>('/users?sort=displayName').then(setUsers).catch(() => {})
    apiGet<string[]>('/audit/entity-types').then(setTypes).catch(() => {})
  }, [isSuper])
  const nameOf = useMemo(() => new Map(users.map((u) => [u.id, u.displayName])), [users])
  const who = (id?: number, fallback?: string) => (id != null ? nameOf.get(id) ?? fallback ?? `User #${id}` : fallback ?? 'System')

  const load = useCallback(() => {
    if (!isSuper) return
    setError(undefined)
    const common = { userId: userId === ALL ? undefined : userId, from: from || undefined, to: to || undefined, page: page - 1, size: PAGE_SIZE }
    if (tab === 'security') {
      apiGet<Page<SecurityEvent>>(`/audit/security?${query(common)}`).then(setEvents).catch((e) => setError(errorMessage(e)))
    } else {
      const entity = record ? { entityType: record.type, entityId: record.id } : { entityType: type === ALL ? undefined : type }
      apiGet<Page<Change>>(`/audit/changes?${query({ ...common, ...entity })}`).then(setChanges).catch((e) => setError(errorMessage(e)))
    }
  }, [isSuper, tab, userId, type, record, from, to, page])
  useEffect(() => { load() }, [load])
  // a new filter starts again at the first page
  useEffect(() => { setPage(1) }, [tab, userId, type, record, from, to])

  if (!isSuper) return <EmptyState icon={ShieldCheck} title="Super Admins only" description="Only a Super Admin can see the audit history." />

  const filtered = userId !== ALL || type !== ALL || !!record || !!from || !!to
  const clear = () => { setUserId(ALL); setType(ALL); setRecord(undefined); setFrom(''); setTo('') }

  return (
    <>
      <PageHeader title="Audit history" description="Every record added, changed or removed, with who did it and when, plus every sign-in and account change. Nothing here can be edited or deleted." />
      <Tabs value={tab} onValueChange={(t) => navigate(t === 'changes' ? '/audit' : `/audit/${t}`)}>
        <TabsList variant="line" aria-label="Audit sections">
          <TabsTrigger value="changes">Changes</TabsTrigger>
          <TabsTrigger value="security">Sign-ins & accounts</TabsTrigger>
        </TabsList>
      </Tabs>
      <FilterBar active={filtered} onClear={clear}>
        <FilterSelect label="Users" value={userId} onChange={setUserId} options={users.map((u) => ({ value: String(u.id), label: u.displayName }))} />
        {tab === 'changes' && !record && <FilterSelect label="Records" value={type} onChange={setType} options={types.map((t) => ({ value: t, label: entityLabel(t) }))} />}
        {tab === 'changes' && record && <StatusBadge status={`${entityLabel(record.type)} #${record.id}`} title="Showing the history of one record" />}
        <DatePicker value={from} onChange={setFrom} placeholder="From" className="h-8 w-40" />
        <DatePicker value={to} onChange={setTo} placeholder="To" className="h-8 w-40" />
      </FilterBar>
      {error && <p className="text-destructive text-sm">{error}</p>}

      {tab === 'security' ? (
        <Card className="gap-0 py-0">
          <Table>
            <TableHeader><TableRow><TableHead className="pl-6">When</TableHead><TableHead>Event</TableHead><TableHead>Done by</TableHead><TableHead>Account</TableHead><TableHead className="hidden lg:table-cell">Details</TableHead><TableHead className="hidden xl:table-cell pr-6">Address</TableHead></TableRow></TableHeader>
            <TableBody>
              {(events?.content ?? []).map((e) => (
                <TableRow key={e.id}>
                  <TableCell className="pl-6 whitespace-nowrap">{when(e.occurredAt)}</TableCell>
                  <TableCell><StatusBadge status={EVENT_LABEL[e.eventType] ?? words(e.eventType.toLowerCase())} /></TableCell>
                  <TableCell>{e.actorUserId != null ? who(e.actorUserId) : <span className="text-muted-foreground">—</span>}</TableCell>
                  <TableCell>{e.targetUserId != null ? who(e.targetUserId, e.username) : e.username ?? <span className="text-muted-foreground">—</span>}</TableCell>
                  <TableCell className="text-muted-foreground hidden max-w-96 truncate lg:table-cell" title={e.details}>{e.details ?? '—'}</TableCell>
                  <TableCell className="text-muted-foreground hidden pr-6 xl:table-cell">{e.remoteAddress ?? '—'}</TableCell>
                </TableRow>
              ))}
              {events && !events.content.length && <TableRow><TableCell colSpan={6} className="text-muted-foreground py-10 text-center">Nothing for these filters.</TableCell></TableRow>}
            </TableBody>
          </Table>
          {events && events.totalElements > 0 && <Pagination className="border-t px-6 py-3" page={page} pageSize={PAGE_SIZE} total={events.totalElements} setPage={setPage} />}
        </Card>
      ) : (
        <Card className="gap-0 py-0">
          <Table>
            <TableHeader><TableRow><TableHead className="pl-6">When</TableHead><TableHead>Who</TableHead><TableHead>Record</TableHead><TableHead>Change</TableHead><TableHead className="pr-6">What changed</TableHead></TableRow></TableHeader>
            <TableBody>
              {(changes?.content ?? []).map((c) => {
                const fields = Object.entries(c.changes)
                return (
                  <TableRow key={c.id} className="align-top">
                    <TableCell className="pl-6 whitespace-nowrap">{when(c.occurredAt)}</TableCell>
                    <TableCell><CellMain title={who(c.userId, c.username)} sub={c.username} /></TableCell>
                    <TableCell>
                      <button type="button" className="hover:underline text-left underline-offset-4" title="Show this record's whole history"
                        onClick={() => c.entityId && setRecord({ type: c.entityType, id: c.entityId })}>
                        <CellMain title={entityLabel(c.entityType)} sub={c.entityId ? `#${c.entityId}` : undefined} />
                      </button>
                    </TableCell>
                    <TableCell><StatusBadge status={CHANGE_LABEL[c.changeType]} /></TableCell>
                    <TableCell className="pr-6">
                      {fields.length ? (
                        <ul className="grid gap-0.5 text-xs">
                          {fields.map(([field, [before, after]]) => (
                            <li key={field} className="min-w-0 truncate max-w-[36rem]" title={`${words(field)}: ${before ?? '—'} → ${after ?? '—'}`}>
                              <span className="text-muted-foreground">{words(field)}:</span>{' '}
                              {c.changeType === 'UPDATED' ? <><span className="line-through decoration-muted-foreground/60">{before ?? '—'}</span> → <span className="font-medium">{after ?? '—'}</span></>
                                : <span>{(c.changeType === 'DELETED' ? before : after) ?? '—'}</span>}
                            </li>
                          ))}
                        </ul>
                      ) : <span className="text-muted-foreground text-xs">—</span>}
                    </TableCell>
                  </TableRow>
                )
              })}
              {changes && !changes.content.length && (
                <TableRow><TableCell colSpan={5} className="py-10"><EmptyState icon={History} title="Nothing for these filters" description="Changes are recorded from the day the audit history was turned on." /></TableCell></TableRow>
              )}
            </TableBody>
          </Table>
          {changes && changes.totalElements > 0 && <Pagination className="border-t px-6 py-3" page={page} pageSize={PAGE_SIZE} total={changes.totalElements} setPage={setPage} />}
        </Card>
      )}
    </>
  )
}
