import { useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Check, Clock, Plus, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { CellMain, Mono } from '@/components/app/data-table'
import { AmountInput, DatePicker, Field, SelectField } from '@/components/app/form'
import { useData, useStore } from '@/store/store'
import { formatDate, TODAY } from '@/lib/dates'
import { formatLKR } from '@/lib/format'

const NONE = '__none'

export function OvertimeView() {
  const d = useData()
  const { addOvertime, approveOvertime } = useStore()
  const [employeeId, setEmployeeId] = useState('')
  const [date, setDate] = useState(TODAY)
  const [projectId, setProjectId] = useState(NONE)
  const [hours, setHours] = useState<number | ''>('')
  const [rate, setRate] = useState<number | ''>('')
  const [notes, setNotes] = useState('')
  const [tried, setTried] = useState(false)
  const emp = d.empById.get(employeeId)
  useEffect(() => {
    if (!emp) return
    setRate(emp.overtimeRate)
    const a = d.s.attendance.find((x) => x.employeeId === emp.id && x.date === date)
    setProjectId(a?.projectId ?? NONE)
  }, [employeeId, date]) // eslint-disable-line react-hooks/exhaustive-deps
  const h = typeof hours === 'number' ? hours : 0
  const r = typeof rate === 'number' ? rate : 0
  const att = emp ? d.s.attendance.find((x) => x.employeeId === emp.id && x.date === date) : undefined
  const err = { emp: !employeeId ? 'Choose the worker.' : undefined, hours: !(h > 0 && h <= 8) ? 'Enter 0.5 to 8 hours.' : undefined, rate: !(r > 0) ? 'Enter the hourly rate.' : undefined }
  const list = useMemo(() => d.s.overtime.slice().sort((a, b) => Number(a.approved) - Number(b.approved) || b.date.localeCompare(a.date)), [d])

  function save() {
    setTried(true)
    if (Object.values(err).some(Boolean)) return
    addOvertime({ date, employeeId, projectId: projectId === NONE ? undefined : projectId, hours: h, rate: r, approved: false, recordedBy: 'Pradeep Jayasinghe', notes: notes || undefined })
    toast.success('Overtime recorded — awaiting approval', { description: `${emp!.name}: ${h} h × ${formatLKR(r)} = ${formatLKR(h * r)}` })
    setHours(''); setNotes(''); setTried(false)
  }

  return (
    <div className="grid items-start gap-6 xl:grid-cols-3">
      <Card>
        <CardHeader><CardTitle>Record overtime</CardTitle><CardDescription>Entered by the site supervisor. Only approved hours are paid.</CardDescription></CardHeader>
        <CardContent className="grid gap-4">
          <Field label="Worker" required error={tried ? err.emp : undefined}><SelectField value={employeeId} onChange={setEmployeeId} invalid={tried && !!err.emp} placeholder="Select worker" options={d.s.employees.filter((e) => e.status === 'Active').map((e) => ({ value: e.id, label: `${e.name} — ${e.role}` }))} /></Field>
          <Field label="Date" required hint={emp ? (att ? `Attendance: ${att.status}` : 'No attendance recorded on this date') : undefined}><DatePicker value={date} onChange={setDate} /></Field>
          <Field label="Related project"><SelectField value={projectId} onChange={setProjectId} options={[{ value: NONE, label: 'General / yard work' }, ...d.s.projects.filter((p) => p.type === 'Construction' && p.status !== 'Completed' && p.status !== 'Cancelled').map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }))]} /></Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Hours" required error={tried ? err.hours : undefined}><Input type="number" step="0.5" min={0} max={8} className="text-right" value={hours} onChange={(e) => setHours(e.target.value === '' ? '' : Number(e.target.value))} aria-invalid={(tried && !!err.hours) || undefined} /></Field>
            <Field label="Rate per hour" required error={tried ? err.rate : undefined}><AmountInput value={rate} onChange={setRate} /></Field>
          </div>
          <div className="bg-muted/60 flex items-center justify-between rounded-md px-3 py-2 text-sm"><span className="text-muted-foreground">Overtime amount</span><Money value={h * r} className="font-semibold" /></div>
          <Field label="Notes" optional><Input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="e.g. Slab concreting ran late" /></Field>
          <Button onClick={save}><Plus />Record overtime</Button>
          <p className="text-muted-foreground text-xs">Rates default to each worker's configured rate (Settings → wage defaults). Change it here for special arrangements.</p>
        </CardContent>
      </Card>
      <Card className="gap-4 pb-0 xl:col-span-2">
        <CardHeader><CardTitle>Overtime entries</CardTitle><CardDescription>{d.s.overtime.filter((o) => !o.approved).length} awaiting approval</CardDescription></CardHeader>
        <Table>
          <TableHeader><TableRow><TableHead className="pl-6">Date</TableHead><TableHead>Worker</TableHead><TableHead className="hidden md:table-cell">Project</TableHead><TableHead className="text-right">Hours</TableHead><TableHead className="hidden text-right sm:table-cell">Rate</TableHead><TableHead className="text-right">Amount (LKR)</TableHead><TableHead>Status</TableHead><TableHead className="pr-4"><span className="sr-only">Approve</span></TableHead></TableRow></TableHeader>
          <TableBody>
            {list.map((o) => {
              const e = d.empById.get(o.employeeId)!
              const p = o.projectId ? d.projectById.get(o.projectId) : undefined
              return (
                <TableRow key={o.id}>
                  <TableCell className="pl-6">{formatDate(o.date)}</TableCell>
                  <TableCell className="max-w-48"><CellMain title={e.name} sub={o.notes} /></TableCell>
                  <TableCell className="hidden md:table-cell">{p ? <Mono className="text-foreground">{p.code}</Mono> : <span className="text-muted-foreground">General</span>}</TableCell>
                  <TableCell className="tabular text-right">{o.hours}</TableCell>
                  <TableCell className="hidden text-right sm:table-cell"><Money value={o.rate} currency={false} decimals={0} tone="muted" /></TableCell>
                  <TableCell className="text-right font-medium"><Money value={o.hours * o.rate} currency={false} /></TableCell>
                  <TableCell><StatusBadge status={o.approved ? 'Approved' : 'Awaiting Approval'} /></TableCell>
                  <TableCell className="pr-4">
                    {o.approved ? <Button variant="ghost" size="sm" onClick={() => { approveOvertime(o.id, false); toast('Approval withdrawn', { description: 'Removed from payroll until approved again.' }) }}><X />Undo</Button>
                      : <Button variant="outline" size="sm" onClick={() => { approveOvertime(o.id, true); toast.success('Overtime approved', { description: `${o.hours} h for ${e.name} added to the weekly payroll.` }) }}><Check />Approve</Button>}
                  </TableCell>
                </TableRow>
              )
            })}
            {!list.length && <TableRow><TableCell colSpan={8} className="text-muted-foreground py-10 text-center"><Clock className="mx-auto mb-2 size-5" />No overtime recorded.</TableCell></TableRow>}
          </TableBody>
        </Table>
      </Card>
    </div>
  )
}
