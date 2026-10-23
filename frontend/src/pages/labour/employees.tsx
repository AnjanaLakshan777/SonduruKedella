import { useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Ellipsis, Pencil, Plus, UserRound } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { KpiStrip } from '@/components/app/kpi-strip'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { CellMain, Mono } from '@/components/app/data-table'
import { ALL, FilterBar, FilterSelect, SearchInput } from '@/components/app/filters'
import { AmountInput, DatePicker, Field, SelectField } from '@/components/app/form'
import { useData, useStore } from '@/store/store'
import { sum } from '@/lib/finance'
import { TODAY } from '@/lib/dates'
import type { Employee } from '@/data/types'
import { formatLKR } from '@/lib/format'

export function Employees() {
  const d = useData()
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('Active')
  const [edit, setEdit] = useState<{ open: boolean; e?: Employee }>({ open: false })
  const latest = useMemo(() => {
    const m = new Map<string, { projectId?: string; date: string }>()
    for (const a of d.s.attendance) { const c = m.get(a.employeeId); if (!c || a.date > c.date) m.set(a.employeeId, { projectId: a.projectId, date: a.date }) }
    return m
  }, [d])
  const monthDays = useMemo(() => {
    const m = new Map<string, number>()
    for (const a of d.s.attendance) if (a.date >= '2026-10-01') m.set(a.employeeId, (m.get(a.employeeId) ?? 0) + (a.status === 'Present' ? 1 : a.status === 'Half Day' ? 0.5 : 0))
    return m
  }, [d])
  const rows = d.s.employees.filter((e) => (status === ALL || e.status === status) && (!q || `${e.name} ${e.role}`.toLowerCase().includes(q.toLowerCase())))
  const activeEmps = d.s.employees.filter((e) => e.status === 'Active')
  const week = d.payroll[d.payroll.length - 1]
  return (
    <>
      <KpiStrip items={[
        { label: 'Active workers', value: activeEmps.length, caption: `${d.s.employees.length - activeEmps.length} inactive` },
        { label: 'Daily wage bill', unit: 'LKR', value: <Money value={sum(activeEmps, (e) => e.dailyRate)} decimals={0} currency={false} />, caption: 'If everyone works a full day' },
        { label: 'Wages earned this week', unit: 'LKR', value: <Money value={week?.gross ?? 0} decimals={0} currency={false} />, basis: 'Recorded', caption: 'Mon–today, before advances' },
        { label: 'Advances to recover', unit: 'LKR', value: <Money value={sum(d.advances, (a) => a.scheduled + a.remaining)} decimals={0} currency={false} />, caption: 'Deducted in this week’s payroll' },
      ]} />
      <div className="flex flex-wrap items-center justify-between gap-2">
        <FilterBar><SearchInput value={q} onChange={setQ} placeholder="Search name or role…" /><FilterSelect label="Statuses" value={status} onChange={setStatus} options={['Active', 'Inactive']} /></FilterBar>
        <Button onClick={() => setEdit({ open: true })}><Plus />Add Employee</Button>
      </div>
      <Card className="gap-0 py-0">
        <Table>
          <TableHeader><TableRow><TableHead className="pl-6">Employee</TableHead><TableHead className="hidden md:table-cell">Phone</TableHead><TableHead className="text-right">Daily wage (LKR)</TableHead><TableHead className="hidden text-right sm:table-cell">OT rate / hr (LKR)</TableHead><TableHead className="hidden lg:table-cell">Current work</TableHead><TableHead className="hidden text-right md:table-cell">Days in Oct</TableHead><TableHead>Status</TableHead><TableHead className="w-12 pr-4" /></TableRow></TableHeader>
          <TableBody>
            {rows.map((e) => {
              const l = latest.get(e.id)
              const p = l?.projectId ? d.projectById.get(l.projectId) : undefined
              return (
                <TableRow key={e.id}>
                  <TableCell className="pl-6"><CellMain title={e.name} sub={e.role} /></TableCell>
                  <TableCell className="hidden md:table-cell">{e.phone}</TableCell>
                  <TableCell className="text-right"><Money value={e.dailyRate} currency={false} /></TableCell>
                  <TableCell className="hidden text-right sm:table-cell"><Money value={e.overtimeRate} currency={false} /></TableCell>
                  <TableCell className="hidden max-w-60 lg:table-cell">{p && e.status === 'Active' ? <CellMain title={<Mono className="text-foreground">{p.code}</Mono>} sub={p.name} /> : <span className="text-muted-foreground">—</span>}</TableCell>
                  <TableCell className="tabular hidden text-right md:table-cell">{monthDays.get(e.id) ?? 0}</TableCell>
                  <TableCell><StatusBadge status={e.status} /></TableCell>
                  <TableCell className="pr-4">
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${e.name}`}><Ellipsis /></Button></DropdownMenuTrigger>
                      <DropdownMenuContent align="end"><DropdownMenuItem onSelect={() => setEdit({ open: true, e })}><Pencil />Edit details and rates</DropdownMenuItem></DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              )
            })}
            {!rows.length && <TableRow><TableCell colSpan={8} className="text-muted-foreground py-10 text-center"><UserRound className="mx-auto mb-2 size-5" />No employees match.</TableCell></TableRow>}
          </TableBody>
        </Table>
      </Card>
      <EmployeeDialog state={edit} onClose={() => setEdit({ open: false })} />
    </>
  )
}

function EmployeeDialog({ state, onClose }: { state: { open: boolean; e?: Employee }; onClose: () => void }) {
  const { addEmployee, updateEmployee, settings } = useStore()
  const [f, setF] = useState({ name: '', role: 'Labourer', phone: '', dailyRate: '' as number | '', overtimeRate: '' as number | '', status: 'Active' as Employee['status'], joinedOn: TODAY, address: '' })
  const [otTouched, setOtTouched] = useState(false)
  const [tried, setTried] = useState(false)
  useEffect(() => {
    if (!state.open) return
    setTried(false); setOtTouched(!!state.e)
    const e = state.e
    setF(e ? { name: e.name, role: e.role, phone: e.phone, dailyRate: e.dailyRate, overtimeRate: e.overtimeRate, status: e.status, joinedOn: e.joinedOn, address: e.address ?? '' } : { name: '', role: 'Labourer', phone: '', dailyRate: settings.wages.defaultDailyRate, overtimeRate: '', status: 'Active', joinedOn: TODAY, address: '' })
  }, [state, settings.wages.defaultDailyRate])
  const suggested = typeof f.dailyRate === 'number' ? Math.round(((f.dailyRate / settings.wages.hoursPerDay) * settings.wages.overtimeMultiplier) / 10) * 10 : 0
  useEffect(() => { if (!otTouched && suggested) setF((x) => ({ ...x, overtimeRate: suggested })) }, [suggested, otTouched])
  const err = { name: !f.name.trim() ? 'Enter the name.' : undefined, rate: !(Number(f.dailyRate) > 0) ? 'Enter the daily wage.' : undefined, ot: !(Number(f.overtimeRate) > 0) ? 'Enter the overtime rate.' : undefined }
  function save() {
    setTried(true)
    if (Object.values(err).some(Boolean)) return
    const rec = { ...f, name: f.name.trim(), dailyRate: Number(f.dailyRate), overtimeRate: Number(f.overtimeRate), address: f.address || undefined }
    if (state.e) updateEmployee(state.e.id, rec)
    else addEmployee(rec)
    toast.success(state.e ? 'Employee updated' : 'Employee added', { description: `${rec.name} · ${formatLKR(rec.dailyRate)} per day` })
    onClose()
  }
  return (
    <Dialog open={state.open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader><DialogTitle>{state.e ? 'Edit employee' : 'Add employee'}</DialogTitle><DialogDescription>Rate changes apply to attendance from now on; past pay runs keep their records.</DialogDescription></DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Full name" required error={tried ? err.name : undefined} className="sm:col-span-2"><Input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
          <Field label="Role"><Input value={f.role} onChange={(e) => setF({ ...f, role: e.target.value })} list="roles" /><datalist id="roles">{['Mason', 'Carpenter', 'Labourer', 'Bar bender', 'Electrician', 'Plumber', 'Site supervisor', 'Painter'].map((r) => <option key={r} value={r} />)}</datalist></Field>
          <Field label="Phone" optional><Input value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} /></Field>
          <Field label="Daily wage" required error={tried ? err.rate : undefined}><AmountInput value={f.dailyRate} onChange={(v) => setF({ ...f, dailyRate: v })} /></Field>
          <Field label="Overtime rate per hour" required error={tried ? err.ot : undefined} hint={`Suggested ${formatLKR(suggested, { decimals: 0 })} (daily ÷ ${settings.wages.hoursPerDay} h × ${settings.wages.overtimeMultiplier})`}><AmountInput value={f.overtimeRate} onChange={(v) => { setOtTouched(true); setF({ ...f, overtimeRate: v }) }} /></Field>
          <Field label="Joined on"><DatePicker value={f.joinedOn} onChange={(v) => setF({ ...f, joinedOn: v })} /></Field>
          <Field label="Employment status"><SelectField value={f.status} onChange={(v) => setF({ ...f, status: v as Employee['status'] })} options={['Active', 'Inactive']} /></Field>
        </div>
        <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={save}>{state.e ? 'Save changes' : 'Add employee'}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
