import { useState } from 'react'
import { toast } from 'sonner'
import { Lock, Plus, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { AmountInput, Field, SelectField } from '@/components/app/form'
import { StatusBadge } from '@/components/app/status-badge'
import { useStore } from '@/store/store'
import { errorMessage } from '@/lib/api'
import type { ExpenseGroup, Settings } from '@/data/types'
import { formatLKR } from '@/lib/format'
import { cn } from '@/lib/utils'

const SECTIONS = [
  ['company', 'Company information'], ['categories', 'Revenue & expense categories'], ['commission', 'Commission policy'],
  ['wages', 'Wages & overtime'], ['units', 'Units of measurement'], ['statuses', 'Project statuses'], ['payments', 'Payment methods'],
] as const

export default function SettingsPage() {
  useCrumbs([{ label: 'Settings' }])
  const settings = useStore((s) => s.settings)
  const [active, setActive] = useState<string>('company')
  return (
    <>
      <PageHeader title="Settings" description="The rules the forms and calculations use. Change them here instead of in each form." />
      <div className="grid items-start gap-6 lg:grid-cols-[220px_1fr]">
        <nav aria-label="Settings sections" className="flex gap-1 overflow-x-auto lg:sticky lg:top-20 lg:grid">
          {SECTIONS.map(([id, label]) => (
            <a key={id} href={`#s-${id}`} onClick={(e) => { e.preventDefault(); setActive(id); document.getElementById(`s-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }}
              className={cn('rounded-md px-3 py-2 text-sm whitespace-nowrap', active === id ? 'bg-accent font-medium' : 'text-muted-foreground hover:text-foreground')}>{label}</a>
          ))}
        </nav>
        <div className="grid gap-6">
          <CompanySection s={settings} />
          <CategoriesSection s={settings} />
          <CommissionSection s={settings} />
          <WagesSection s={settings} />
          <ListSection id="units" title="Units of measurement" description="Used on materials, supplier invoices and stock movements." values={settings.units} onSave={(units) => ({ units })} placeholder="e.g. sq ft" />
          <Card id="s-statuses" className="scroll-mt-20">
            <CardHeader><CardTitle>Project statuses</CardTitle><CardDescription>Fixed in this version because reports and alerts rely on them.</CardDescription></CardHeader>
            <CardContent className="flex flex-wrap gap-2">{settings.projectStatuses.map((s) => <StatusBadge key={s} status={s} />)}<span className="text-muted-foreground flex items-center gap-1.5 text-xs"><Lock className="size-3.5" />Fixed — the backend works with these five.</span></CardContent>
          </Card>
          <ListSection id="payments" title="Payment methods" description="Recorded with each payment as information only — no cash or bank balances are kept." values={settings.paymentMethods} onSave={(paymentMethods) => ({ paymentMethods })} placeholder="e.g. Cheque deposit" />
        </div>
      </div>
    </>
  )
}

function useSave() {
  const update = useStore((s) => s.updateSettings)
  return (patch: Partial<Settings>, what: string) =>
    update(patch)
      .then(() => toast.success(`${what} saved`))
      .catch((e) => toast.error(`${what} not saved`, { description: errorMessage(e) }))
}

function CompanySection({ s }: { s: Settings }) {
  const save = useSave()
  const [c, setC] = useState(s.company)
  const [credit, setCredit] = useState(s.supplierCreditDays)
  const fields: [keyof Settings['company'], string][] = [['name', 'Company name'], ['tagline', 'Tagline'], ['owner', 'Owner'], ['registration', 'Business registration no.'], ['phone', 'Phone'], ['email', 'Email'], ['address', 'Address']]
  return (
    <Card id="s-company" className="scroll-mt-20">
      <CardHeader><CardTitle>Company information</CardTitle><CardDescription>Shown on reports and exports.</CardDescription></CardHeader>
      <CardContent className="grid gap-4 sm:grid-cols-2">
        {fields.map(([k, l]) => <Field key={k} label={l} className={k === 'address' ? 'sm:col-span-2' : undefined}><Input value={c[k]} onChange={(e) => setC({ ...c, [k]: e.target.value })} /></Field>)}
        <Field label="Default supplier credit period" hint="Used for the due date on new supplier invoices."><div className="flex items-center gap-2"><Input type="number" min={0} className="w-24 text-right" value={credit} onChange={(e) => setCredit(Number(e.target.value))} /><span className="text-muted-foreground text-sm">days</span></div></Field>
        <Field label="Financial year starts"><Input value="01 April" readOnly /></Field>
      </CardContent>
      <CardFooter className="justify-end"><Button onClick={() => save({ company: c, supplierCreditDays: credit }, 'Company information')}>Save</Button></CardFooter>
    </Card>
  )
}

function Chips({ values, onChange, placeholder }: { values: string[]; onChange: (v: string[]) => void; placeholder?: string }) {
  const [v, setV] = useState('')
  const add = () => { const t = v.trim(); if (!t) return; if (values.some((x) => x.toLowerCase() === t.toLowerCase())) { toast.error(`"${t}" is already in the list`); return } onChange([...values, t]); setV('') }
  return (
    <div className="grid gap-2">
      <div className="flex flex-wrap gap-1.5">
        {values.map((x) => <Badge key={x} variant="secondary" className="gap-1 py-1 pr-1 text-[13px] font-normal">{x}<button type="button" aria-label={`Remove ${x}`} className="hover:bg-background rounded-sm p-0.5" onClick={() => onChange(values.filter((y) => y !== x))}><X className="size-3" /></button></Badge>)}
      </div>
      <div className="flex max-w-sm gap-2"><Input className="h-8" value={v} onChange={(e) => setV(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), add())} placeholder={placeholder} /><Button size="sm" variant="outline" onClick={add}><Plus />Add</Button></div>
    </div>
  )
}

function CategoriesSection({ s }: { s: Settings }) {
  const save = useSave()
  const [exp, setExp] = useState(s.expenseCategories)
  const locked = ['Materials', 'Labour', 'Commission']
  return (
    <Card id="s-categories" className="scroll-mt-20">
      <CardHeader><CardTitle>Revenue & expense categories</CardTitle><CardDescription>Revenue categories are fixed: they match the project types, plus Other Income. Expense categories are grouped by work type so each form shows only what applies. Materials, Labour and Commission are generated by other modules and stay fixed.</CardDescription></CardHeader>
      <CardContent className="grid gap-6">
        <Field label="Revenue categories" hint="Fixed — they follow the project types.">
          <div className="flex flex-wrap gap-1.5">{s.revenueCategories.map((c) => <Badge key={c} variant="secondary">{c}</Badge>)}</div>
        </Field>
        {(Object.keys(exp) as ExpenseGroup[]).map((g) => (
          <Field key={g} label={`${g === 'Company' ? 'Company-level / other' : g} expense categories`}>
            <Chips values={exp[g]} onChange={(v) => setExp({ ...exp, [g]: [...new Set([...exp[g].filter((x) => locked.includes(x)), ...v])] })} placeholder="New category" />
          </Field>
        ))}
      </CardContent>
      <CardFooter className="justify-end"><Button onClick={() => save({ expenseCategories: exp }, 'Categories')}>Save categories</Button></CardFooter>
    </Card>
  )
}

function CommissionSection({ s }: { s: Settings }) {
  const save = useSave()
  const [pool, setPool] = useState<number | ''>(s.commission.designCommissionPool)
  const [cons, setCons] = useState<number | ''>(s.commission.constructionDefaultRate ?? '')
  const p = Number(pool) || 0
  const bad = !(p > 0 && p < 100)
  return (
    <Card id="s-commission" className="scroll-mt-20">
      <CardHeader><CardTitle>Commission policy</CardTitle><CardDescription>Commission is always the agreed contract value × each recipient's %. These defaults trigger warnings — they never change recipients' shares automatically.</CardDescription></CardHeader>
      <CardContent className="grid gap-4 sm:grid-cols-2">
        <Field label="Design: commission pool" error={bad ? 'Enter a percentage between 0 and 100.' : undefined} hint={`Company share ${100 - p}% · e.g. on LKR 450,000: commission ${formatLKR((450000 * p) / 100, { decimals: 0 })}`}>
          <div className="flex items-center gap-2"><Input type="number" min={0} max={100} className="w-24 text-right" value={pool} onChange={(e) => setPool(e.target.value === '' ? '' : Number(e.target.value))} /><span className="text-muted-foreground text-sm">% of contract value</span></div>
        </Field>
        <Field label="Construction: warning threshold" optional hint="No fixed construction rate is assumed. Set a total to be warned above, or leave empty.">
          <div className="flex items-center gap-2"><Input type="number" min={0} max={100} step="0.5" className="w-24 text-right" value={cons} onChange={(e) => setCons(e.target.value === '' ? '' : Number(e.target.value))} /><span className="text-muted-foreground text-sm">%</span></div>
        </Field>
        <p className="text-muted-foreground text-xs sm:col-span-2">Consultation projects do not carry commission.</p>
      </CardContent>
      <CardFooter className="justify-end"><Button disabled={bad} onClick={() => save({ commission: { designCommissionPool: p, designCompanyShare: 100 - p, constructionDefaultRate: cons === '' ? undefined : Number(cons) } }, 'Commission policy')}>Save policy</Button></CardFooter>
    </Card>
  )
}

function WagesSection({ s }: { s: Settings }) {
  const save = useSave()
  const [w, setW] = useState(s.wages)
  return (
    <Card id="s-wages" className="scroll-mt-20">
      <CardHeader><CardTitle>Wages & overtime</CardTitle><CardDescription>Defaults for new employees. Each worker's own daily wage and overtime rate can differ (Labour & Payroll → Employees).</CardDescription></CardHeader>
      <CardContent className="grid gap-4 sm:grid-cols-2">
        <Field label="Default daily wage"><AmountInput value={w.defaultDailyRate} onChange={(v) => setW({ ...w, defaultDailyRate: Number(v) || 0 })} /></Field>
        <Field label="Normal hours per day"><Input type="number" min={1} max={12} className="w-24 text-right" value={w.hoursPerDay} onChange={(e) => setW({ ...w, hoursPerDay: Number(e.target.value) || 8 })} /></Field>
        <Field label="Overtime multiplier" hint={`Suggested OT rate = daily wage ÷ ${w.hoursPerDay} × ${w.overtimeMultiplier} (e.g. ${formatLKR(Math.round((w.defaultDailyRate / w.hoursPerDay) * w.overtimeMultiplier), { decimals: 0 })} / hour)`}>
          <Input type="number" step="0.25" min={1} className="w-24 text-right" value={w.overtimeMultiplier} onChange={(e) => setW({ ...w, overtimeMultiplier: Number(e.target.value) || 1 })} />
        </Field>
        <Field label="Weekly payday"><SelectField value={w.payday} onChange={(v) => setW({ ...w, payday: v })} options={['Friday', 'Saturday']} /></Field>
      </CardContent>
      <CardFooter className="justify-end"><Button onClick={() => save({ wages: w }, 'Wage defaults')}>Save defaults</Button></CardFooter>
    </Card>
  )
}

function ListSection({ id, title, description, values, onSave, placeholder }: { id: string; title: string; description: string; values: string[]; onSave: (v: string[]) => Partial<Settings>; placeholder: string }) {
  const save = useSave()
  const [v, setV] = useState(values)
  return (
    <Card id={`s-${id}`} className="scroll-mt-20">
      <CardHeader><CardTitle>{title}</CardTitle><CardDescription>{description}</CardDescription></CardHeader>
      <CardContent><Chips values={v} onChange={setV} placeholder={placeholder} /></CardContent>
      <CardFooter className="justify-end"><Button onClick={() => { if (!v.length) return toast.error('Keep at least one'); save(onSave(v), title) }}>Save</Button></CardFooter>
    </Card>
  )
}
