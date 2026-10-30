import { useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { CheckCheck, ChevronLeft, ChevronRight, Save } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Money } from '@/components/app/money'
import { CellMain } from '@/components/app/data-table'
import { DatePicker, SelectField } from '@/components/app/form'
import { useData, useStore } from '@/store/store'
import { errorMessage } from '@/lib/api'
import type { Attendance, AttendanceStatus } from '@/data/types'
import { addDays, dayName, formatDate, formatLongToday, TODAY, weekday, weekStart } from '@/lib/dates'
import { cn } from '@/lib/utils'

const STATES: AttendanceStatus[] = ['Present', 'Half Day', 'Absent']
const NONE = '__none'

/** Fast daily sheet: one row per worker, no per-worker form. */
export function AttendanceView() {
  const d = useData()
  const saveAttendance = useStore((s) => s.saveAttendance)
  const [busy, setBusy] = useState(false)
  const [date, setDate] = useState(TODAY)
  const [rows, setRows] = useState<Record<string, Attendance & { marked: boolean }>>({})
  const [dirty, setDirty] = useState(false)
  const active = d.s.employees.filter((e) => e.status === 'Active' && e.joinedOn <= date)

  useEffect(() => {
    const prevDay = (() => { let x = addDays(date, -1); while (weekday(x) === 0) x = addDays(x, -1); return x })()
    const next: typeof rows = {}
    for (const e of active) {
      const a = d.s.attendance.find((x) => x.date === date && x.employeeId === e.id)
      const prev = d.s.attendance.find((x) => x.date === prevDay && x.employeeId === e.id)
      next[e.id] = a ? { ...a, marked: true } : { date, employeeId: e.id, status: 'Present', projectId: prev?.projectId, marked: false }
    }
    setRows(next); setDirty(false)
  }, [date]) // eslint-disable-line react-hooks/exhaustive-deps

  const set = (id: string, patch: Partial<Attendance>) => { setRows((r) => ({ ...r, [id]: { ...r[id], ...patch, marked: true } })); setDirty(true) }
  const list = active.map((e) => ({ e, a: rows[e.id] })).filter((x) => x.a)
  const wage = (x: (typeof list)[number]) => (x.a.status === 'Present' ? x.e.dailyRate : x.a.status === 'Half Day' ? x.e.dailyRate / 2 : 0)
  const projects = d.s.projects.filter((p) => p.type === 'Construction' && (p.status === 'In Progress' || p.status === 'On Hold'))
  const isSunday = weekday(date) === 0
  const savedCount = list.filter((x) => d.s.attendance.some((a) => a.date === date && a.employeeId === x.e.id)).length

  async function save() {
    setBusy(true)
    try {
      await saveAttendance(date, list.map((x) => ({ date, employeeId: x.e.id, status: x.a.status, projectId: x.a.projectId, notes: x.a.notes || undefined })))
      setDirty(false)
      toast.success(`Attendance saved for ${formatDate(date)}`, { description: `${list.filter((x) => x.a.status === 'Present').length} present · ${list.filter((x) => x.a.status === 'Half Day').length} half day · ${list.filter((x) => x.a.status === 'Absent').length} absent` })
    } catch (e) {
      // e.g. a paid (frozen) week can't change
      toast.error('Attendance not saved', { description: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Tabs defaultValue="day" className="gap-4">
      <TabsList><TabsTrigger value="day">Daily sheet</TabsTrigger><TabsTrigger value="week">Week view</TabsTrigger></TabsList>
      <TabsContent value="day" className="grid gap-4">
        <Card className="gap-4 pb-0">
          <CardHeader>
            <CardTitle>Attendance — {dayName(date)}, {formatDate(date)}</CardTitle>
            <CardDescription>{savedCount ? `${savedCount} of ${list.length} saved for this day.` : 'Not recorded yet — everyone starts as Present on yesterday’s project.'} Wages = attendance × daily rate.</CardDescription>
            <CardAction className="flex flex-wrap items-center gap-2">
              <Button variant="outline" size="icon" className="size-8" aria-label="Previous day" onClick={() => setDate(addDays(date, -1))}><ChevronLeft /></Button>
              <DatePicker value={date} onChange={setDate} className="h-8 w-40" />
              <Button variant="outline" size="icon" className="size-8" aria-label="Next day" disabled={date >= TODAY} onClick={() => setDate(addDays(date, 1))}><ChevronRight /></Button>
            </CardAction>
          </CardHeader>
          {isSunday ? <CardContent className="text-muted-foreground pb-6 text-sm">Sunday — no work day. Choose another date.</CardContent> : date > TODAY ? <CardContent className="text-muted-foreground pb-6 text-sm">Attendance can't be recorded for future dates.</CardContent> : (
            <>
              <CardContent className="flex flex-wrap items-center gap-2">
                <Button variant="outline" size="sm" onClick={() => { const n = { ...rows }; for (const k of Object.keys(n)) n[k] = { ...n[k], status: 'Present', marked: true }; setRows(n); setDirty(true) }}><CheckCheck />Mark all present</Button>
                <span className="text-muted-foreground text-sm">Today is {formatLongToday()}</span>
              </CardContent>
              <Table>
                <TableHeader><TableRow><TableHead className="pl-6">Worker</TableHead><TableHead>Attendance</TableHead><TableHead className="min-w-56">Project / work</TableHead><TableHead className="hidden text-right md:table-cell">Daily rate</TableHead><TableHead className="text-right">Wage (LKR)</TableHead><TableHead className="hidden min-w-48 pr-6 lg:table-cell">Notes</TableHead></TableRow></TableHeader>
                <TableBody>
                  {list.map((x) => (
                    <TableRow key={x.e.id} className={cn(!x.a.marked && 'text-muted-foreground')}>
                      <TableCell className="min-w-44 pl-6"><CellMain title={x.e.name} sub={x.e.role} /></TableCell>
                      <TableCell>
                        <div role="radiogroup" aria-label={`Attendance for ${x.e.name}`} className="bg-muted inline-flex rounded-md p-0.5">
                          {STATES.map((s) => (
                            <button key={s} type="button" role="radio" aria-checked={x.a.status === s} onClick={() => set(x.e.id, { status: s })}
                              className={cn('focus-visible:ring-ring/50 rounded-[5px] px-2.5 py-1 text-xs font-medium whitespace-nowrap outline-none focus-visible:ring-[3px]', x.a.status === s ? (s === 'Present' ? 'bg-success-soft text-success shadow-sm' : s === 'Half Day' ? 'bg-warning-soft text-warning shadow-sm' : 'bg-card text-foreground shadow-sm') : 'text-muted-foreground hover:text-foreground')}>
                              {s}
                            </button>
                          ))}
                        </div>
                      </TableCell>
                      <TableCell><SelectField size="sm" value={x.a.projectId ?? NONE} onChange={(v) => set(x.e.id, { projectId: v === NONE ? undefined : v })} options={[{ value: NONE, label: 'General / yard work' }, ...projects.map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }))]} /></TableCell>
                      <TableCell className="hidden text-right md:table-cell"><Money value={x.e.dailyRate} currency={false} tone="muted" /></TableCell>
                      <TableCell className="text-right font-medium"><Money value={wage(x)} currency={false} /></TableCell>
                      <TableCell className="hidden pr-6 lg:table-cell"><Input className="h-8" value={x.a.notes ?? ''} onChange={(e) => set(x.e.id, { notes: e.target.value })} placeholder="Optional" aria-label={`Notes for ${x.e.name}`} /></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
                <TableFooter>
                  <TableRow>
                    <TableCell className="pl-6">{list.length} workers</TableCell>
                    <TableCell className="text-xs font-normal">{list.filter((x) => x.a.status === 'Present').length} present · {list.filter((x) => x.a.status === 'Half Day').length} half · {list.filter((x) => x.a.status === 'Absent').length} absent</TableCell>
                    <TableCell /><TableCell className="hidden md:table-cell" />
                    <TableCell className="text-right"><Money value={list.reduce((t, x) => t + wage(x), 0)} currency={false} /></TableCell>
                    <TableCell className="hidden lg:table-cell" />
                  </TableRow>
                </TableFooter>
              </Table>
              <CardContent className="flex items-center justify-end gap-3 pb-6">
                {dirty && <span className="text-warning text-sm">Unsaved changes</span>}
                <Button onClick={save} disabled={!list.length || busy}><Save />Save attendance</Button>
              </CardContent>
            </>
          )}
        </Card>
      </TabsContent>
      <TabsContent value="week"><WeekGrid anchor={date} onPick={setDate} /></TabsContent>
    </Tabs>
  )
}

function WeekGrid({ anchor, onPick }: { anchor: string; onPick: (d: string) => void }) {
  const d = useData()
  const [ws, setWs] = useState(weekStart(anchor))
  const days = Array.from({ length: 6 }, (_, i) => addDays(ws, i))
  const map = useMemo(() => new Map(d.s.attendance.filter((a) => a.date >= ws && a.date <= addDays(ws, 5)).map((a) => [`${a.date}:${a.employeeId}`, a])), [d, ws])
  const emps = d.s.employees.filter((e) => days.some((day) => map.has(`${day}:${e.id}`)) || e.status === 'Active')
  return (
    <Card className="gap-4 pb-0">
      <CardHeader>
        <CardTitle>Week of {formatDate(ws)}</CardTitle>
        <CardDescription>P present · ½ half day · A absent · blank not recorded. Click a day to open its sheet.</CardDescription>
        <CardAction className="flex gap-2"><Button variant="outline" size="icon" className="size-8" aria-label="Previous week" onClick={() => setWs(addDays(ws, -7))}><ChevronLeft /></Button><Button variant="outline" size="icon" className="size-8" aria-label="Next week" disabled={addDays(ws, 7) > TODAY} onClick={() => setWs(addDays(ws, 7))}><ChevronRight /></Button></CardAction>
      </CardHeader>
      <Table>
        <TableHeader><TableRow><TableHead className="pl-6">Worker</TableHead>{days.map((day) => <TableHead key={day} className="text-center"><button className="hover:text-foreground" onClick={() => onPick(day)}>{dayName(day)} {day.slice(8)}</button></TableHead>)}<TableHead className="pr-6 text-right">Days</TableHead></TableRow></TableHeader>
        <TableBody>
          {emps.map((e) => {
            let total = 0
            return (
              <TableRow key={e.id}>
                <TableCell className="pl-6"><CellMain title={e.name} sub={e.role} /></TableCell>
                {days.map((day) => {
                  const a = map.get(`${day}:${e.id}`)
                  if (a) total += a.status === 'Present' ? 1 : a.status === 'Half Day' ? 0.5 : 0
                  return (
                    <TableCell key={day} className="text-center">
                      {a ? <span title={`${a.status}${a.projectId ? ` · ${d.projectById.get(a.projectId)?.code}` : ''}`} className={cn('inline-grid size-7 place-items-center rounded-md text-xs font-semibold', a.status === 'Present' ? 'bg-success-soft text-success' : a.status === 'Half Day' ? 'bg-warning-soft text-warning' : 'bg-muted text-muted-foreground')}>{a.status === 'Present' ? 'P' : a.status === 'Half Day' ? '½' : 'A'}</span> : <span className="text-muted-foreground">·</span>}
                    </TableCell>
                  )
                })}
                <TableCell className="tabular pr-6 text-right font-medium">{total}</TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </Card>
  )
}
