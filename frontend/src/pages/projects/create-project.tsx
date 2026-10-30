import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { CircleAlert, UserPlus, Users } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { AmountInput, DatePicker, Field, SelectField } from '@/components/app/form'
import { ScheduleEditor, scheduleTemplate, type ScheduleRow } from '@/components/app/schedule-editor'
import { DelayTermsForm } from '@/components/app/delay-terms-form'
import { useStore } from '@/store/store'
import type { DelayTerms, ProjectStatus, ProjectType } from '@/data/types'
import { formatLKR } from '@/lib/format'
import { TODAY } from '@/lib/dates'
import { errorMessage } from '@/lib/api'

const TYPES: { value: ProjectType; label: string; hint: string }[] = [
  { value: 'Construction', label: 'Construction', hint: 'Agreed build with an installment schedule' },
  { value: 'Design', label: 'Design', hint: '3D design and house plans' },
  { value: 'Consultation', label: 'Consultation', hint: 'Advice or reports for other projects' },
]

export default function CreateProject() {
  useCrumbs([{ label: 'Projects', to: '/projects' }, { label: 'Create Project' }])
  const navigate = useNavigate()
  const { clients, projects, settings, addProject } = useStore()
  const [busy, setBusy] = useState(false)
  const [name, setName] = useState('')
  const [type, setType] = useState<ProjectType>('Construction')
  const [clientMode, setClientMode] = useState<'existing' | 'new'>('existing')
  const [clientId, setClientId] = useState('')
  const [nc, setNc] = useState({ name: '', phone: '', city: 'Anuradhapura', address: '', email: '' })
  const [location, setLocation] = useState('')
  const [description, setDescription] = useState('')
  const [value, setValue] = useState<number | ''>('')
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [status, setStatus] = useState<ProjectStatus>('Planning')
  const [notes, setNotes] = useState('')
  const [rows, setRows] = useState<ScheduleRow[]>(() => scheduleTemplate('Construction', 0, '', ''))
  const [touchedSchedule, setTouchedSchedule] = useState(false)
  const [delayOn, setDelayOn] = useState(false)
  const [delay, setDelay] = useState<DelayTerms>({ enabled: true, contractDeadline: '', rule: 'per_day' })
  const [tried, setTried] = useState(false)

  const contract = typeof value === 'number' ? value : 0
  // keep the template in step with type/value/dates until the owner edits the schedule
  useEffect(() => { if (!touchedSchedule) setRows(scheduleTemplate(type, contract, start, end)) }, [type, contract, start, end, touchedSchedule])
  useEffect(() => { if (!delay.contractDeadline && end) setDelay((dl) => ({ ...dl, contractDeadline: end })) }, [end, delay.contractDeadline])

  // same rule as the server: PRJ-<start year>-<next number in that year>. Only a preview; the server assigns it.
  const nextCode = useMemo(() => {
    const prefix = `PRJ-${(start || TODAY).slice(0, 4)}-`
    const last = Math.max(0, ...projects.filter((p) => p.code.startsWith(prefix)).map((p) => Number(p.code.slice(prefix.length)) || 0))
    return `${prefix}${String(last + 1).padStart(3, '0')}`
  }, [projects, start])
  const scheduleTotal = rows.reduce((t, r) => t + (typeof r.amount === 'number' ? r.amount : 0), 0)
  const errors = {
    name: !name.trim() ? 'Enter a project name.' : undefined,
    client: clientMode === 'existing' ? (!clientId ? 'Choose a client or create a new one.' : undefined) : !nc.name.trim() ? 'Enter the client’s name.' : !nc.phone.trim() ? 'Enter a phone number.' : undefined,
    value: type !== 'Consultation' && !(contract > 0) ? 'Enter the agreed contract value.' : undefined,
    start: !start ? 'Choose the start date.' : undefined,
    end: !end ? 'Choose the expected completion date.' : start && end <= start ? `Must be after the start date.` : undefined,
    schedule: contract > 0 && (Math.abs(scheduleTotal - contract) > 0.01 || rows.some((r) => !r.description.trim() || !r.dueDate || !(Number(r.amount) > 0))) ? 'Complete every installment so the schedule adds up to the contract value.' : undefined,
    delay: type === 'Construction' && delayOn && (!delay.contractDeadline || (delay.rule === 'per_day' && !(delay.ratePerDay! > 0)) || (delay.rule === 'percent_per_week' && !(delay.percentPerWeek! > 0)) || (delay.rule === 'fixed_amount' && !(delay.fixedAmount! > 0))) ? 'Complete the delay deduction terms or switch them off.' : undefined,
  }
  const errorList = Object.values(errors).filter(Boolean) as string[]

  async function save() {
    setTried(true)
    if (errorList.length) {
      toast.error('Some details need attention', { description: errorList[0] })
      return
    }
    setBusy(true)
    try {
      const id = await addProject({
        name: name.trim(), type, location: location.trim() || nc.city || '', description: description.trim(),
        ...(clientMode === 'existing'
          ? { clientId }
          : { newClient: { name: nc.name.trim(), phone: nc.phone.trim(), city: nc.city.trim(), address: nc.address.trim(), email: nc.email || undefined, kind: 'Individual' } }),
        contractValue: contract, startDate: start, expectedCompletion: end, status, notes: notes || undefined,
        delay: type === 'Construction' && delayOn ? { ...delay, enabled: true } : undefined,
        schedule: contract > 0 ? rows.map((r) => ({ description: r.description.trim(), dueDate: r.dueDate, amount: Number(r.amount) })) : [],
      })
      // the server gives the real code; the one in the header was only a preview
      const saved = useStore.getState().projects.find((p) => p.id === id)
      toast.success(`${saved?.code ?? 'Project'} created`, { description: `${name.trim()} · ${formatLKR(contract)} agreed. Upload the agreement PDF from the project page.` })
      navigate(`/projects/${id}`)
    } catch (e) {
      toast.error('Project not created', { description: errorMessage(e) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader title="Create Project" description={`New project ${nextCode}. Fields marked * are required.`} />
      <div className="grid items-start gap-6 lg:grid-cols-[1fr_300px]">
        <div className="grid gap-6">
          <Card>
            <CardHeader><CardTitle>Project</CardTitle><CardDescription>What the job is and who it is for.</CardDescription></CardHeader>
            <CardContent className="grid gap-5">
              <Field label="Project name" required error={tried ? errors.name : undefined}>
                <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Nuwarawewa Lakeside Villa" aria-invalid={(tried && !!errors.name) || undefined} />
              </Field>
              <Field label="Project type" required>
                <RadioGroup value={type} onValueChange={(v) => { setType(v as ProjectType); setTouchedSchedule(false) }} className="grid gap-2 sm:grid-cols-3">
                  {TYPES.map((t) => (
                    <Label key={t.value} className="has-[[data-state=checked]]:border-primary-text has-[[data-state=checked]]:bg-primary-soft/60 flex cursor-pointer items-start gap-2.5 rounded-md border p-3 font-normal">
                      <RadioGroupItem value={t.value} className="mt-0.5" />
                      <span className="grid gap-0.5"><span className="font-medium">{t.label}</span><span className="text-muted-foreground text-xs leading-snug">{t.hint}</span></span>
                    </Label>
                  ))}
                </RadioGroup>
              </Field>
              <div className="grid gap-3">
                <div className="flex items-center justify-between gap-2">
                  <Label>Client<span className="text-destructive -ml-1" aria-hidden>*</span></Label>
                  <Button variant="link" size="sm" className="h-auto px-0" onClick={() => setClientMode(clientMode === 'existing' ? 'new' : 'existing')}>
                    {clientMode === 'existing' ? <><UserPlus />Create new client</> : <><Users />Choose existing client</>}
                  </Button>
                </div>
                {clientMode === 'existing' ? (
                  <Field label={<span className="sr-only">Existing client</span>} error={tried ? errors.client : undefined}>
                    <SelectField value={clientId} onChange={setClientId} placeholder="Select a client" invalid={tried && !!errors.client} options={clients.map((c) => ({ value: c.id, label: `${c.name} — ${c.city}` }))} />
                  </Field>
                ) : (
                  <div className="bg-muted/40 grid gap-4 rounded-lg border p-4 sm:grid-cols-2">
                    <Field label="Client name" required error={tried && !nc.name.trim() ? 'Enter the client’s name.' : undefined}><Input value={nc.name} onChange={(e) => setNc({ ...nc, name: e.target.value })} aria-invalid={(tried && !nc.name.trim()) || undefined} /></Field>
                    <Field label="Phone" required error={tried && nc.name.trim() && !nc.phone.trim() ? 'Enter a phone number.' : undefined}><Input value={nc.phone} onChange={(e) => setNc({ ...nc, phone: e.target.value })} placeholder="07X XXX XXXX" inputMode="tel" /></Field>
                    <Field label="Address" optional><Input value={nc.address} onChange={(e) => setNc({ ...nc, address: e.target.value })} /></Field>
                    <Field label="Town / city"><Input value={nc.city} onChange={(e) => setNc({ ...nc, city: e.target.value })} /></Field>
                    <Field label="Email" optional className="sm:col-span-2"><Input type="email" value={nc.email} onChange={(e) => setNc({ ...nc, email: e.target.value })} /></Field>
                  </div>
                )}
              </div>
              <div className="grid gap-5 sm:grid-cols-2">
                <Field label="Site location" optional><Input value={location} onChange={(e) => setLocation(e.target.value)} placeholder="e.g. Mihintale Road, Anuradhapura" /></Field>
                <Field label="Project status" required><SelectField value={status} onChange={(v) => setStatus(v as ProjectStatus)} options={settings.projectStatuses.filter((s) => s !== 'Completed' && s !== 'Cancelled')} /></Field>
              </div>
              <Field label="Project description" optional><Textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Size, floors, scope of work…" /></Field>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>Contract and dates</CardTitle><CardDescription>The agreed value is fixed; delay deductions are recorded separately and never change it.</CardDescription></CardHeader>
            <CardContent className="grid gap-5 sm:grid-cols-3">
              <Field label={type === 'Consultation' ? 'Agreed fee' : 'Agreed contract value'} required={type !== 'Consultation'} optional={type === 'Consultation'} error={tried ? errors.value : undefined} hint={type === 'Consultation' ? 'Leave empty if the fee is billed later.' : 'Commissions are calculated on this full value.'}>
                <AmountInput value={value} onChange={setValue} invalid={tried && !!errors.value} />
              </Field>
              <Field label="Start date" required error={tried ? errors.start : undefined}><DatePicker value={start} onChange={setStart} invalid={tried && !!errors.start} /></Field>
              <Field label="Expected completion" required error={tried ? errors.end : undefined}><DatePicker value={end} onChange={setEnd} invalid={tried && !!errors.end} /></Field>
            </CardContent>
          </Card>

          {contract > 0 && (
            <Card>
              <CardHeader>
                <CardTitle>Client payment schedule</CardTitle>
                <CardDescription>
                  {type === 'Construction' ? 'Installments the client pays as work progresses. Each becomes revenue only when it is billed.' : 'How the fee is split. Edit it to match the agreement.'}
                </CardDescription>
              </CardHeader>
              <CardContent className="grid gap-3">
                <ScheduleEditor rows={rows} onChange={(r) => { setRows(r); setTouchedSchedule(true) }} contractValue={contract} showErrors={tried} />
                {tried && errors.schedule && <p className="text-destructive flex items-center gap-1.5 text-xs"><CircleAlert className="size-3.5" />{errors.schedule}</p>}
              </CardContent>
            </Card>
          )}

          {type === 'Construction' && (
            <Card>
              <CardHeader>
                <CardTitle>Delay Deduction (Liquidated Damages)</CardTitle>
                <CardDescription>Only if the agreement reduces the amount payable when the work finishes late. Not retention money.</CardDescription>
              </CardHeader>
              <CardContent className="grid gap-5">
                <label className="flex items-center gap-2.5 text-sm font-medium"><Checkbox checked={delayOn} onCheckedChange={(v) => setDelayOn(v === true)} />The agreement includes a delay deduction</label>
                {delayOn && <DelayTermsForm value={delay} onChange={setDelay} contractValue={contract} showErrors={tried} />}
              </CardContent>
            </Card>
          )}

          <Card>
            <CardContent><Field label="Notes" optional><Textarea value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Anything the team should know — approvals, access, client preferences." /></Field></CardContent>
          </Card>
        </div>

        <aside className="grid gap-4 lg:sticky lg:top-20">
          <Card className="gap-4">
            <CardHeader><CardTitle>Summary</CardTitle></CardHeader>
            <CardContent className="grid gap-3 text-sm">
              <div className="flex justify-between gap-3"><span className="text-muted-foreground">Project ID</span><span className="font-mono text-xs font-medium">{nextCode}</span></div>
              <div className="flex justify-between gap-3"><span className="text-muted-foreground">Type</span><span>{type}</span></div>
              <div className="flex justify-between gap-3"><span className="text-muted-foreground">Contract value</span><span className="tabular font-medium">{contract ? formatLKR(contract) : '—'}</span></div>
              <div className="flex justify-between gap-3"><span className="text-muted-foreground">Installments</span><span>{contract ? rows.length : '—'}</span></div>
              {type === 'Design' && contract > 0 && (
                <div className="bg-muted/60 rounded-md p-3 text-xs leading-relaxed">
                  Design policy: company {settings.commission.designCompanyShare}% · commission pool {settings.commission.designCommissionPool}% ({formatLKR((contract * settings.commission.designCommissionPool) / 100)}). Set recipients on the project's Commissions tab.
                </div>
              )}
              {tried && errorList.length > 0 && (
                <ul className="text-destructive grid gap-1 text-xs">{errorList.map((e) => <li key={e} className="flex gap-1.5"><CircleAlert className="mt-px size-3.5 shrink-0" />{e}</li>)}</ul>
              )}
            </CardContent>
          </Card>
          <div className="flex gap-2">
            <Button variant="outline" asChild className="flex-1"><Link to="/projects">Cancel</Link></Button>
            <Button className="flex-1" onClick={save} disabled={busy}>Create Project</Button>
          </div>
        </aside>
      </div>
    </>
  )
}
