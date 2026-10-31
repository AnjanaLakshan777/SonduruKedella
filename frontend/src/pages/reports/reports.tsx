import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { Copy, Download, FileSpreadsheet, FileText, Info } from 'lucide-react'
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { KpiStrip } from '@/components/app/kpi-strip'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { CellMain } from '@/components/app/data-table'
import { ALL, DateRangeFilter, FilterBar, FilterSelect, defaultRange, inRange } from '@/components/app/filters'
import { axisProps, ChartTooltip, Legend, moneyAxis } from '@/components/app/chart'
import { ageingBuckets } from '@/components/app/receivables-table'
import { BasisToggle, useBasis } from '@/components/app/basis-toggle'
import { useData, useStore } from '@/store/store'
import { projectFigures, sum } from '@/lib/finance'
import { formatDate, formatRange, monthEnd, monthKey, monthLabel, monthsBetween, monthStart, TODAY } from '@/lib/dates'
import { formatLKR } from '@/lib/format'
import { cn } from '@/lib/utils'
import { downloadPdf, downloadXlsx } from '@/lib/export'

// ---------------------------------------------------------------- shared
type Csv = (string | number)[][]
function downloadCsv(name: string, rows: Csv) {
  const text = rows.map((r) => r.map((c) => (typeof c === 'number' ? c.toFixed(2) : `"${String(c).replace(/"/g, '""')}"`)).join(',')).join('\r\n')
  try {
    const url = URL.createObjectURL(new Blob(['﻿' + text], { type: 'text/csv;charset=utf-8' }))
    const a = document.createElement('a')
    a.href = url
    a.download = `${name}.csv`
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 2000)
    toast.success('CSV export started', { description: `${name}.csv · ${rows.length - 1} rows. If nothing was saved, your browser blocked downloads here — use Copy as CSV.` })
  } catch {
    toast.error('Export failed', { description: 'Your browser blocked the download.' })
  }
}
function toCsvText(rows: Csv) {
  return rows.map((r) => r.map((c) => (typeof c === 'number' ? c.toFixed(2) : `"${String(c).replace(/"/g, '""')}"`)).join(',')).join('\r\n')
}
function copyCsv(rows: Csv) {
  navigator.clipboard.writeText(toCsvText(rows)).then(
    () => toast.success('Copied as CSV', { description: `${rows.length - 1} rows — paste into Excel or Google Sheets.` }),
    () => toast.error('Copy was blocked', { description: 'Your browser did not allow clipboard access.' }),
  )
}
// "income-report" -> "Income report"
const titleOf = (name: string) => name.charAt(0).toUpperCase() + name.slice(1).replace(/-/g, ' ')

function ExportMenu({ name, rows, subtitle }: { name: string; rows: () => Csv; subtitle?: string }) {
  const company = useStore((s) => s.settings.company.name) || 'Sonduru Kedella'
  const run = (kind: string, make: () => Promise<void>) =>
    make()
      .then(() => toast.success(`${kind} downloaded`, { description: `${name}.${kind === 'Excel' ? 'xlsx' : 'pdf'}` }))
      .catch((e) => toast.error(`${kind} export failed`, { description: e instanceof Error ? e.message : String(e) }))
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild><Button variant="outline" size="sm"><Download />Export</Button></DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onSelect={() => run('PDF', () => downloadPdf(name, titleOf(name), rows(), company, subtitle))}><FileText />PDF</DropdownMenuItem>
        <DropdownMenuItem onSelect={() => run('Excel', () => downloadXlsx(name, titleOf(name), rows(), company))}><FileSpreadsheet />Excel (.xlsx)</DropdownMenuItem>
        <DropdownMenuItem onSelect={() => downloadCsv(name, rows())}><Download />Download CSV</DropdownMenuItem>
        <DropdownMenuItem onSelect={() => copyCsv(rows())}><Copy />Copy as CSV</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
function Basis({ children }: { children: React.ReactNode }) {
  return <div className="bg-muted/60 text-muted-foreground flex items-start gap-2.5 rounded-lg px-4 py-3 text-[13px]"><Info className="mt-0.5 size-4 shrink-0" /><div>{children}</div></div>
}
const M0 = (v: number) => <Money value={v} decimals={0} currency={false} />

// ---------------------------------------------------------------- income
export function IncomeReport() {
  const d = useData()
  const [range, setRange] = useState(defaultRange())
  const [cat, setCat] = useState(ALL)
  const rows = useMemo(() => [
    ...d.installments.filter((i) => i.inst.billed).map((i) => ({ date: i.inst.billedOn ?? i.inst.dueDate, category: i.project.type as string, project: i.project, amount: i.net - i.writtenOff, received: i.paid, remaining: i.remaining })),
    ...d.otherIncome.map((o) => ({ date: o.income.date, category: 'Other Income', project: undefined, amount: o.income.amount, received: o.received, remaining: o.remaining })),
  ].filter((r) => inRange(r.date, range) && (cat === ALL || r.category === cat)), [d, range, cat])
  const received = useMemo(() => d.s.clientPayments.filter((p) => inRange(p.date, range)), [d, range])
  const byCat = d.s.settings.revenueCategories.map((c) => ({ c, v: sum(rows.filter((r) => r.category === c), (r) => r.amount), rec: sum(rows.filter((r) => r.category === c), (r) => r.received) }))
  const byProject = d.projects.map((p) => ({ p, v: sum(rows.filter((r) => r.project?.id === p.project.id), (r) => r.amount), rec: sum(rows.filter((r) => r.project?.id === p.project.id), (r) => r.received) })).filter((x) => x.v > 0).sort((a, b) => b.v - a.v)
  const months = monthsBetween(range.from.slice(0, 7), (range.to < TODAY ? range.to : TODAY).slice(0, 7)).map((k) => ({
    label: monthLabel(k), recorded: sum(rows.filter((r) => monthKey(r.date) === k), (r) => r.amount), received: sum(received.filter((p) => monthKey(p.date) === k), (p) => p.amount) + sum(d.s.otherIncomeReceipts.filter((p) => monthKey(p.date) === k), (p) => p.amount),
  }))
  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <FilterBar><DateRangeFilter value={range} onChange={setRange} /><FilterSelect label="Categories" value={cat} onChange={setCat} options={d.s.settings.revenueCategories} /></FilterBar>
        <ExportMenu subtitle={formatRange(range.from, range.to)} name="income-report" rows={() => [['Date', 'Category', 'Project', 'Revenue recorded, after write-offs (LKR)', 'Received (LKR)', 'Outstanding (LKR)'], ...rows.map((r) => [formatDate(r.date), r.category, r.project?.code ?? '', r.amount, r.received, r.remaining])]} />
      </div>
      <KpiStrip items={[
        { label: 'Revenue recorded', unit: 'LKR', value: M0(sum(rows, (r) => r.amount)), basis: 'Recorded', hint: 'Billed installments and other income in the range, less write-offs.' },
        { label: 'Received against it', unit: 'LKR', value: M0(sum(rows, (r) => r.received)), basis: 'Received', hint: 'Payments received against those records, whenever they came in.' },
        { label: 'Still outstanding', unit: 'LKR', value: M0(sum(rows, (r) => r.remaining)), hint: 'What is still unpaid on those records.' },
        { label: 'Cash received in period', unit: 'LKR', value: M0(sum(months, (m) => m.received)), caption: 'Includes payments for earlier billing', hint: 'Payments dated within the range, whichever period the revenue was recorded in.' },
      ]} />
      <Basis>Revenue is recorded when an installment is billed (net of any applied delay deduction), plus other income, less any amount written off as bad debt. Payments received are shown separately; an installment paid in several payments is counted as revenue once. Contract value is not revenue.</Basis>
      <div className="grid gap-6 xl:grid-cols-5">
        <Card className="xl:col-span-3">
          <CardHeader><CardTitle>Revenue by period</CardTitle><CardDescription>Recorded versus received, by month</CardDescription><CardAction><Legend items={[{ label: 'Recorded', color: 'var(--chart-3)' }, { label: 'Received', color: 'var(--chart-5)' }]} /></CardAction></CardHeader>
          <CardContent className="h-64">
            <ResponsiveContainer><BarChart data={months} barGap={2}><CartesianGrid vertical={false} stroke="var(--border)" /><XAxis dataKey="label" {...axisProps} /><YAxis {...moneyAxis} /><Tooltip cursor={{ fill: 'var(--muted)', opacity: 0.6 }} content={<ChartTooltip />} /><Bar dataKey="recorded" name="Recorded" fill="var(--chart-3)" radius={[3, 3, 0, 0]} maxBarSize={24} /><Bar dataKey="received" name="Received" fill="var(--chart-5)" radius={[3, 3, 0, 0]} maxBarSize={24} /></BarChart></ResponsiveContainer>
          </CardContent>
        </Card>
        <Card className="gap-4 pb-0 xl:col-span-2">
          <CardHeader><CardTitle>Revenue by category</CardTitle></CardHeader>
          <Table>
            <TableHeader><TableRow><TableHead className="pl-6">Category</TableHead><TableHead className="text-right">Recorded (LKR)</TableHead><TableHead className="pr-6 text-right">Received (LKR)</TableHead></TableRow></TableHeader>
            <TableBody>{byCat.map((x) => <TableRow key={x.c}><TableCell className="pl-6">{x.c}</TableCell><TableCell className="text-right">{M0(x.v)}</TableCell><TableCell className="pr-6 text-right">{M0(x.rec)}</TableCell></TableRow>)}</TableBody>
            <TableFooter><TableRow><TableCell className="pl-6">Total</TableCell><TableCell className="text-right">{M0(sum(byCat, (x) => x.v))}</TableCell><TableCell className="pr-6 text-right">{M0(sum(byCat, (x) => x.rec))}</TableCell></TableRow></TableFooter>
          </Table>
        </Card>
      </div>
      <Card className="gap-4 pb-0">
        <CardHeader><CardTitle>Revenue by project</CardTitle></CardHeader>
        <Table>
          <TableHeader><TableRow><TableHead className="pl-6">Project</TableHead><TableHead className="hidden md:table-cell">Type</TableHead><TableHead className="text-right">Recorded (LKR)</TableHead><TableHead className="text-right">Received (LKR)</TableHead><TableHead className="pr-6 text-right">Outstanding (LKR)</TableHead></TableRow></TableHeader>
          <TableBody>{byProject.map((x) => <TableRow key={x.p.project.id}><TableCell className="max-w-80 pl-6"><CellMain title={x.p.project.name} code={x.p.project.code} /></TableCell><TableCell className="hidden md:table-cell">{x.p.project.type}</TableCell><TableCell className="text-right">{M0(x.v)}</TableCell><TableCell className="text-right">{M0(x.rec)}</TableCell><TableCell className="pr-6 text-right font-medium">{M0(x.v - x.rec)}</TableCell></TableRow>)}</TableBody>
        </Table>
      </Card>
    </>
  )
}

// ---------------------------------------------------------------- expenses
export function ExpenseReport() {
  const d = useData()
  const [range, setRange] = useState(defaultRange())
  const [proj, setProj] = useState(ALL)
  const [cat, setCat] = useState(ALL)
  const [basis, setBasis] = useBasis()
  const cash = basis === 'cash'
  const onePro = proj !== ALL && proj !== 'company'
  const pick = (l: { date: string; scope: string; projectId?: string; category: string }) =>
    inRange(l.date, range) && (proj === ALL || (proj === 'company' ? l.scope === 'Company' : l.projectId === proj)) && (cat === ALL || l.category === cat)
  // on cash, stock issues only show for a single project: across the company the stock invoice payments are the cash
  const cashRows = d.cashLines.filter((l) => pick(l) && (!l.fromStock || onePro))
  const rows: { date: string; scope: string; projectId?: string; category: string; description: string; payee: string; amount: number; paid: number; remaining: number; fromStock?: boolean }[] =
    cash ? cashRows.map((l) => ({ ...l, paid: l.amount, remaining: 0 })) : d.expenseLines.filter(pick)
  const cats =[...new Set(rows.map((l) => l.category))].map((c) => ({ c, v: sum(rows.filter((l) => l.category === c), (l) => l.amount), project: sum(rows.filter((l) => l.category === c && l.scope === 'Project'), (l) => l.amount) })).sort((a, b) => b.v - a.v)
  const byProject = d.s.projects.map((p) => ({ p, v: sum(rows.filter((l) => l.projectId === p.id), (l) => l.amount) })).filter((x) => x.v > 0).sort((a, b) => b.v - a.v)
  const company = rows.filter((l) => l.scope === 'Company')
  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <FilterBar>
          <DateRangeFilter value={range} onChange={setRange} />
          <FilterSelect label="Projects" value={proj} onChange={setProj} options={[{ value: 'company', label: 'Company-level only' }, ...d.s.projects.map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }))]} />
          <FilterSelect label="Categories" value={cat} onChange={setCat} options={[...new Set([...d.expenseLines, ...d.cashLines].map((l) => l.category))].sort()} />
        </FilterBar>
        <div className="flex items-center gap-2">
          <BasisToggle value={basis} onChange={setBasis} />
          <ExportMenu subtitle={`${cash ? 'Cash basis' : 'Accrual basis'} · ${formatRange(range.from, range.to)}`} name={cash ? 'expense-report-cash' : 'expense-report'} rows={() => cash
            ? [['Paid on', 'Scope', 'Project', 'Category', 'Description', 'Payee', 'Paid (LKR)'], ...rows.map((l) => [formatDate(l.date), l.fromStock ? 'From stock' : l.scope, d.projectById.get(l.projectId ?? '')?.code ?? '', l.category, l.description, l.payee, l.amount])]
            : [['Date', 'Scope', 'Project', 'Category', 'Description', 'Payee', 'Amount (LKR)', 'Paid (LKR)', 'Unpaid (LKR)'], ...rows.map((l) => [formatDate(l.date), l.scope, d.projectById.get(l.projectId ?? '')?.code ?? '', l.category, l.description, l.payee, l.amount, l.paid, l.remaining])]} />
        </div>
      </div>
      <KpiStrip items={cash ? [
        { label: 'Total paid out', unit: 'LKR', value: M0(sum(rows, (l) => l.amount)), basis: 'Received', caption: `${rows.length} payments`, hint: 'Money paid out in the range: expense, supplier, wage, advance and commission payments.' },
        { label: 'Project payments', unit: 'LKR', value: M0(sum(rows.filter((l) => l.scope === 'Project'), (l) => l.amount)), caption: onePro ? 'Incl. stock issued to it' : 'Wages, materials, expenses, commission', hint: 'Payments for project costs. With one project selected, also stock issued to it at cost.' },
        { label: 'Company-level', unit: 'LKR', value: M0(sum(company, (l) => l.amount)), caption: 'Incl. stock purchases and salary advances', hint: 'Payments not charged to a project, including stock purchases and salary advances.' },
        onePro
          ? { label: 'Issued from stock', unit: 'LKR', value: M0(sum(rows.filter((l) => l.fromStock), (l) => l.amount)), caption: 'Paid when the stock was bought', hint: 'Stock issued to this project, at cost. The cash went out when the stock was bought.' }
          : { label: 'Recorded, not yet paid', unit: 'LKR', value: M0(sum(d.expenseLines.filter(pick), (l) => l.remaining)), caption: 'Counts on accrual, not here', hint: 'Expenses recorded but not paid yet. They count on the accrual basis only.' },
      ] : [
        { label: 'Total expenses', unit: 'LKR', value: M0(sum(rows, (l) => l.amount)), basis: 'Recorded', caption: `${rows.length} records`, hint: 'Every expense recorded in the range, paid or not.' },
        { label: 'Project expenses', unit: 'LKR', value: M0(sum(rows.filter((l) => l.scope === 'Project'), (l) => l.amount)), caption: 'Direct costs incl. commissions', hint: 'Costs charged to projects, including confirmed commission.' },
        { label: 'Company-level', unit: 'LKR', value: M0(sum(company, (l) => l.amount)), caption: 'General business costs', hint: 'General business costs not charged to any project.' },
        { label: 'Unpaid', unit: 'LKR', value: M0(sum(rows, (l) => l.remaining)), hint: 'Recorded but not paid yet.' },
      ]} />
      {cash
        ? <Basis><strong className="text-foreground">Cash basis.</strong> Only money actually paid out, on the day it was paid: expense payments, supplier payments, wage payments (split over the projects worked that week), salary advances when given, and commission payments. Unpaid bills are left out. Buying stock is a company-level payment; when a single project is selected, the stock issued to it is included at issue cost, because the project used it even though no new cash moved.</Basis>
        : <Basis><strong className="text-foreground">Accrual basis.</strong> Project expenses include direct expenses, materials delivered to site, materials issued from stock at issue cost, wages and approved overtime (by attendance), and confirmed commissions, whether paid or not. Stock purchases are not counted until issued. Company-level expenses are never assigned to a project.</Basis>}
      <div className="grid gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader><CardTitle>Expenses by category</CardTitle><CardDescription>Project share in terracotta, company-level in grey</CardDescription></CardHeader>
          <CardContent style={{ height: cats.length * 32 + 30 }}>
            <ResponsiveContainer>
              <BarChart data={cats.map((c) => ({ name: c.c, project: c.project, company: c.v - c.project }))} layout="vertical" margin={{ left: 0, right: 8 }}>
                <XAxis type="number" {...moneyAxis} height={20} /><YAxis type="category" dataKey="name" {...axisProps} width={150} />
                <Tooltip cursor={{ fill: 'var(--muted)', opacity: 0.6 }} content={<ChartTooltip />} />
                <Bar dataKey="project" name="Project" stackId="a" fill="var(--chart-3)" maxBarSize={18} /><Bar dataKey="company" name="Company-level" stackId="a" fill="var(--muted-foreground)" radius={[0, 3, 3, 0]} maxBarSize={18} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
        <Card className="gap-4 pb-0">
          <CardHeader><CardTitle>Project versus company-level</CardTitle></CardHeader>
          <Table>
            <TableHeader><TableRow><TableHead className="pl-6">Charged to</TableHead><TableHead className="pr-6 text-right">Amount (LKR)</TableHead></TableRow></TableHeader>
            <TableBody>
              {byProject.map((x) => <TableRow key={x.p.id}><TableCell className="max-w-80 pl-6"><CellMain title={x.p.name} code={x.p.code} /></TableCell><TableCell className="pr-6 text-right">{M0(x.v)}</TableCell></TableRow>)}
              <TableRow><TableCell className="pl-6"><CellMain title="Company-level expenses" sub={[...new Set(company.map((l) => l.category))].join(', ')} /></TableCell><TableCell className="pr-6 text-right">{M0(sum(company, (l) => l.amount))}</TableCell></TableRow>
            </TableBody>
            <TableFooter><TableRow><TableCell className="pl-6">Total</TableCell><TableCell className="pr-6 text-right">{M0(sum(rows, (l) => l.amount))}</TableCell></TableRow></TableFooter>
          </Table>
        </Card>
      </div>
    </>
  )
}

// ---------------------------------------------------------------- profitability
export function ProfitabilityReport() {
  const d = useData()
  const navigate = useNavigate()
  const [type, setType] = useState(ALL)
  const [status, setStatus] = useState(ALL)
  const [basis, setBasis] = useBasis()
  const cash = basis === 'cash'
  const rows = d.projects.filter((p) => p.project.status !== 'Planning' && (type === ALL || p.project.type === type) && (status === ALL || p.project.status === status))
    .map((p) => ({ ...p, f: projectFigures(p, basis) }))
  const T = (f: (p: (typeof rows)[number]) => number) => M0(sum(rows, f))
  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <FilterBar><FilterSelect label="Types" value={type} onChange={setType} options={['Design', 'Construction', 'Consultation']} /><FilterSelect label="Statuses" value={status} onChange={setStatus} options={['In Progress', 'On Hold', 'Completed']} /></FilterBar>
        <div className="flex items-center gap-2">
          <BasisToggle value={basis} onChange={setBasis} />
          <ExportMenu subtitle={cash ? 'Cash basis' : 'Accrual basis'} name={cash ? 'project-profitability-cash' : 'project-profitability'} rows={() => cash
            ? [['Project', 'Name', 'Status', 'Contract value', 'Received from client', 'Materials paid', 'Labour paid', 'Other expenses paid', 'Commission paid', 'Total paid out', 'Cash profit', 'Receivable'],
              ...rows.map((p) => [p.project.code, p.project.name, p.project.status, p.contractValue, p.f.revenue, p.f.materials, p.f.labour, p.f.expenses, p.f.commission, p.f.costs, p.f.profit, p.receivable])]
            : [['Project', 'Name', 'Status', 'Contract value', 'Delay deduction', 'Revenue recorded', 'Written off', 'Materials', 'Labour', 'Other expenses', 'Commission committed', 'Commission paid', 'Total costs', 'Profit', 'Basis', 'Receivable'],
              ...rows.map((p) => [p.project.code, p.project.name, p.project.status, p.contractValue, p.deduction, p.revenueRecorded, p.writtenOff, p.costs.materialsDirect + p.costs.materialsStock, p.costs.labour, p.costs.expenses, p.commissionCommitted, p.commissionPaid, p.totalCosts, p.profit, p.profitBasis, p.receivable])]} />
        </div>
      </div>
      <KpiStrip items={cash ? [
        { label: 'Contract value', unit: 'LKR', value: T((p) => p.contractValue), basis: 'Agreed', hint: 'Agreed contract value of the projects in this report.' },
        { label: 'Received from clients', unit: 'LKR', value: T((p) => p.f.revenue), basis: 'Received', hint: 'Client payments received for these projects.' },
        { label: 'Paid out for projects', unit: 'LKR', value: T((p) => p.f.costs), caption: 'incl. stock issued at cost', hint: 'Money paid out for these projects, plus stock issued to them at cost.' },
        { label: 'Cash profit', unit: 'LKR', value: T((p) => p.f.profit), caption: `Accrual profit ${formatLKR(sum(rows, (p) => p.profit), { decimals: 0 })}`, hint: 'Received from clients − paid out for projects.' },
        { label: 'Client receivables', unit: 'LKR', value: T((p) => p.receivable), caption: 'Not received — not in cash profit', hint: 'Billed but not received yet. Left out of cash profit.' },
      ] : [
        { label: 'Contract value', unit: 'LKR', value: T((p) => p.contractValue), basis: 'Agreed', hint: 'Agreed contract value of the projects in this report.' },
        { label: 'Revenue recorded', unit: 'LKR', value: T((p) => p.revenueRecorded), basis: 'Recorded', hint: 'Billed installments, net of delay deductions, before write-offs.' },
        { label: 'Project costs', unit: 'LKR', value: T((p) => p.totalCosts), basis: 'Recorded', caption: 'incl. committed commission', hint: 'Materials, labour, expenses and confirmed commission, paid or not.' },
        { label: 'Profit to date', unit: 'LKR', value: T((p) => p.profit), basis: 'Estimated', caption: `${formatLKR(sum(rows.filter((p) => p.profitBasis === 'Recorded'), (p) => p.profit), { decimals: 0 })} on completed projects`, hint: 'Revenue recorded − written off − project costs. An estimate while projects are still in progress.' },
        { label: 'Client receivables', unit: 'LKR', value: T((p) => p.receivable), caption: 'Not profit — still to collect', hint: 'Billed but not received yet. Still to collect, not profit.' },
      ]} />
      {cash ? (
        <Basis>
          <strong className="text-foreground">Cash basis.</strong> Cash profit = payments received from the client − money paid out for the project (expense, supplier, wage and commission payments, plus stock issued to it at FIFO cost, since that stock was already paid for). Unpaid bills, unpaid wages and committed but unpaid commission are left out, and so is money the client still owes. Salary advances and company-level expenses are not charged to projects.
        </Basis>
      ) : <Basis>
        <strong className="text-foreground">Accrual basis.</strong> Profit = revenue recorded − written off − (materials + labour + other direct expenses + confirmed commission), whether paid or not. Revenue recorded already excludes applied delay deductions. Contract value is shown for reference only and is not counted as revenue; outstanding receivables are not profit. Projects still in progress are <em>Estimated</em> because future costs are not yet known. Company-level expenses are excluded.
      </Basis>}
      <Card className="gap-0 py-0">
        <div className="overflow-x-auto text-[13px]">
          <Table className="text-[13px]">
            <TableHeader>
              <TableRow>
                <TableHead className="pl-6">Project</TableHead><TableHead className="text-right">Contract</TableHead><TableHead className="hidden text-right 2xl:table-cell">Deduction</TableHead><TableHead className="text-right">{cash ? 'Received' : 'Revenue rec.'}</TableHead>
                <TableHead className="text-right">Materials</TableHead><TableHead className="text-right">Labour</TableHead><TableHead className="text-right">Other exp.</TableHead><TableHead className="text-right">Commission</TableHead>
                <TableHead className="text-right">{cash ? 'Cash profit' : 'Profit'}</TableHead><TableHead className="text-right">Margin</TableHead><TableHead>Basis</TableHead><TableHead className="pr-6 text-right">Receivable</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((p) => (
                <TableRow key={p.project.id} className="cursor-pointer" onClick={() => navigate(`/projects/${p.project.id}`)}>
                  <TableCell className="min-w-52 pl-6 whitespace-normal"><CellMain title={<span className="line-clamp-2 whitespace-normal">{p.project.name}</span>} code={p.project.code} sub={p.project.status} /></TableCell>
                  <TableCell className="text-right">{M0(p.contractValue)}</TableCell>
                  <TableCell className="hidden text-right 2xl:table-cell">{p.deduction ? <Money value={-p.deduction} decimals={0} currency={false} /> : '—'}</TableCell>
                  <TableCell className="text-right">{M0(p.f.revenue)}</TableCell>
                  <TableCell className="text-right">{M0(p.f.materials)}</TableCell>
                  <TableCell className="text-right">{M0(p.f.labour)}</TableCell>
                  <TableCell className="text-right">{M0(p.f.expenses)}</TableCell>
                  <TableCell className="text-right">{M0(p.f.commission)}{!cash && p.commissionCommitted > 0 && <div className="text-muted-foreground text-xs">{formatLKR(p.commissionPaid, { decimals: 0, currency: false })} paid</div>}</TableCell>
                  <TableCell className="text-right font-semibold"><Money value={p.f.profit} decimals={0} currency={false} tone="auto" /></TableCell>
                  <TableCell className={cn('tabular text-right', p.f.margin < 0 && 'text-destructive')}>{p.f.revenue ? `${p.f.margin.toFixed(1)}%` : '—'}</TableCell>
                  <TableCell><StatusBadge status={cash ? 'Received' : p.profitBasis} /></TableCell>
                  <TableCell className="pr-6 text-right">{M0(p.receivable)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
            <TableFooter>
              <TableRow>
                <TableCell className="pl-6">Total ({rows.length})</TableCell><TableCell className="text-right">{T((p) => p.contractValue)}</TableCell><TableCell className="hidden text-right 2xl:table-cell"><Money value={-sum(rows, (p) => p.deduction)} decimals={0} currency={false} /></TableCell>
                <TableCell className="text-right">{T((p) => p.f.revenue)}</TableCell><TableCell className="text-right">{T((p) => p.f.materials)}</TableCell><TableCell className="text-right">{T((p) => p.f.labour)}</TableCell>
                <TableCell className="text-right">{T((p) => p.f.expenses)}</TableCell><TableCell className="text-right">{T((p) => p.f.commission)}</TableCell><TableCell className="text-right">{T((p) => p.f.profit)}</TableCell><TableCell /><TableCell /><TableCell className="pr-6 text-right">{T((p) => p.receivable)}</TableCell>
              </TableRow>
            </TableFooter>
          </Table>
        </div>
      </Card>
      <Card>
        <CardHeader><CardTitle>{cash ? 'Cash profit by project' : 'Profit to date by project'}</CardTitle><CardDescription>{cash ? 'LKR, received less paid out' : 'LKR, estimated for projects still running'}</CardDescription></CardHeader>
        <CardContent style={{ height: rows.length * 32 + 30 }}>
          <ResponsiveContainer>
            <BarChart data={rows.map((p) => ({ code: p.project.code, name: p.project.name, profit: Math.round(p.f.profit), basis: p.profitBasis }))} layout="vertical">
              <XAxis type="number" {...moneyAxis} height={20} /><YAxis type="category" dataKey="code" {...axisProps} width={100} tick={{ fill: 'var(--muted-foreground)', fontSize: 11, fontFamily: 'var(--font-mono)' }} />
              <Tooltip cursor={{ fill: 'var(--muted)', opacity: 0.6 }} content={<ChartTooltip />} />
              <Bar dataKey="profit" name={cash ? 'Cash profit' : 'Profit to date'} radius={[0, 3, 3, 0]} maxBarSize={18}>{rows.map((p) => <Cell key={p.project.id} fill={p.f.profit < 0 ? 'var(--destructive)' : cash || p.profitBasis === 'Recorded' ? 'var(--chart-5)' : 'var(--chart-3)'} />)}</Bar>
            </BarChart>
          </ResponsiveContainer>
        </CardContent>
      </Card>
    </>
  )
}

// ---------------------------------------------------------------- receivables
export function ReceivablesReport() {
  const d = useData()
  const billed = d.installments.filter((i) => i.inst.billed)
  const open = billed.filter((i) => i.remaining > 0)
  const ageing = ageingBuckets(billed)
  const byClient = d.s.clients.map((c) => {
    const r = billed.filter((i) => i.client.id === c.id)
    return { c, due: sum(r, (i) => i.net), paid: sum(r, (i) => i.paid), wo: sum(r, (i) => i.writtenOff), owed: sum(r, (i) => i.remaining), overdue: sum(r.filter((i) => i.status === 'Overdue'), (i) => i.remaining), partial: r.filter((i) => i.paid > 0 && i.remaining > 0).length }
  }).filter((x) => x.due > 0).sort((a, b) => b.owed - a.owed)
  return (
    <>
      <div className="flex justify-end"><ExportMenu name="client-receivables" rows={() => [['Client', 'Project', 'Installment', 'Billed on', 'Amount due', 'Received', 'Written off', 'Remaining', 'Days overdue', 'Status'], ...billed.map((i) => [i.client.name, i.project.code, i.inst.description, formatDate(i.inst.billedOn ?? i.inst.dueDate), i.net, i.paid, i.writtenOff, i.remaining, i.daysOverdue, i.status])]} /></div>
      <KpiStrip items={[
        { label: 'Amounts owed', unit: 'LKR', value: M0(sum(open, (i) => i.remaining)), basis: 'Recorded', caption: `${open.length} installments`, hint: 'Billed installments that are still unpaid.' },
        { label: 'Overdue', unit: 'LKR', value: M0(sum(open.filter((i) => i.status === 'Overdue'), (i) => i.remaining)), tone: 'destructive', hint: 'Unpaid billed money. It is overdue from the billing day.' },
        { label: 'Partially paid', value: open.filter((i) => i.paid > 0).length, caption: `${formatLKR(sum(open.filter((i) => i.paid > 0), (i) => i.remaining), { decimals: 0 })} still owed on them`, hint: 'Unpaid installments that have had some payment. Underneath: what is still owed on them.' },
        { label: 'Written off', unit: 'LKR', value: M0(d.totals.writtenOff), caption: `${d.s.writeOffs.length} write-offs — not received`, hint: 'Total written off as bad debt. Taken off revenue; not money received.' },
      ]} />
      <Card className="gap-4">
        <CardHeader><CardTitle>Ageing summary</CardTitle><CardDescription>Outstanding by days since billing</CardDescription></CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-5">
          {ageing.map((a, i) => (
            <div key={a.label} className="grid gap-1">
              <span className="text-muted-foreground text-xs">{a.label}</span>
              <Money value={a.amount} decimals={0} className="font-semibold" />
              <div className="bg-muted h-1.5 overflow-hidden rounded-full"><div className="h-full rounded-full" style={{ width: `${(a.amount / Math.max(1, sum(ageing, (x) => x.amount))) * 100}%`, background: i === 0 ? 'var(--chart-1)' : i === 1 ? 'var(--chart-3)' : 'var(--destructive)' }} /></div>
              <span className="text-muted-foreground text-xs">{a.count} items</span>
            </div>
          ))}
        </CardContent>
      </Card>
      <Card className="gap-4 pb-0">
        <CardHeader><CardTitle>By client</CardTitle></CardHeader>
        <Table>
          <TableHeader><TableRow><TableHead className="pl-6">Client</TableHead><TableHead className="text-right">Billed (LKR)</TableHead><TableHead className="text-right">Received (LKR)</TableHead><TableHead className="hidden text-right md:table-cell">Written off (LKR)</TableHead><TableHead className="text-right">Owed (LKR)</TableHead><TableHead className="text-right">Overdue (LKR)</TableHead><TableHead className="hidden pr-6 text-right sm:table-cell">Part-paid items</TableHead></TableRow></TableHeader>
          <TableBody>{byClient.map((x) => <TableRow key={x.c.id}><TableCell className="pl-6 font-medium">{x.c.name}</TableCell><TableCell className="text-right">{M0(x.due)}</TableCell><TableCell className="text-right">{M0(x.paid)}</TableCell><TableCell className="hidden text-right md:table-cell">{x.wo ? M0(x.wo) : '—'}</TableCell><TableCell className="text-right font-medium">{M0(x.owed)}</TableCell><TableCell className="text-right">{x.overdue ? <Money value={x.overdue} decimals={0} currency={false} tone="negative" /> : '—'}</TableCell><TableCell className="tabular hidden pr-6 text-right sm:table-cell">{x.partial || '—'}</TableCell></TableRow>)}</TableBody>
          <TableFooter><TableRow><TableCell className="pl-6">Total</TableCell><TableCell className="text-right">{M0(sum(byClient, (x) => x.due))}</TableCell><TableCell className="text-right">{M0(sum(byClient, (x) => x.paid))}</TableCell><TableCell className="hidden text-right md:table-cell">{M0(sum(byClient, (x) => x.wo))}</TableCell><TableCell className="text-right">{M0(sum(byClient, (x) => x.owed))}</TableCell><TableCell className="text-right">{M0(sum(byClient, (x) => x.overdue))}</TableCell><TableCell className="hidden sm:table-cell" /></TableRow></TableFooter>
        </Table>
      </Card>
      <Card className="gap-4 pb-0">
        <CardHeader><CardTitle>Bad-debt write-offs</CardTitle><CardDescription>Formally abandoned amounts — kept separate from payments</CardDescription></CardHeader>
        {d.s.writeOffs.length ? (
          <Table><TableBody>{d.s.writeOffs.map((w) => { const i = d.installments.find((x) => x.inst.id === w.installmentId)!; return <TableRow key={w.id}><TableCell className="pl-6">{formatDate(w.date)}</TableCell><TableCell><CellMain title={i.client.name} code={i.project.code} sub={i.inst.description} /></TableCell><TableCell className="text-muted-foreground hidden max-w-80 truncate md:table-cell">{w.reason}</TableCell><TableCell className="pr-6 text-right font-medium">{M0(w.amount)}</TableCell></TableRow> })}</TableBody></Table>
        ) : <CardContent className="text-muted-foreground pb-6 text-sm">No debts have been written off this year.</CardContent>}
      </Card>
    </>
  )
}

// ---------------------------------------------------------------- payables
export function PayablesReport() {
  const d = useData()
  const [status, setStatus] = useState(ALL)
  const rows = d.invoices.filter((v) => status === ALL || (status === 'open' ? v.remaining > 0 : v.status === status)).sort((a, b) => a.invoice.dueDate.localeCompare(b.invoice.dueDate))
  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <FilterSelect label="Statuses" value={status} onChange={setStatus} options={[{ value: 'open', label: 'Unpaid (all)' }, 'Overdue', 'Partially Paid', 'Unpaid', 'Paid']} />
        <ExportMenu name="supplier-payables" rows={() => [['Supplier', 'Invoice', 'Invoice date', 'Due date', 'Allocated to', 'Total', 'Paid', 'Remaining', 'Status'], ...rows.map((v) => [v.supplier.name, v.invoice.invoiceNo, formatDate(v.invoice.date), formatDate(v.invoice.dueDate), v.project?.code ?? 'Stock', v.total, v.paid, v.remaining, v.status])]} />
      </div>
      <KpiStrip items={[
        { label: 'Supplier invoices', unit: 'LKR', value: M0(sum(d.invoices, (v) => v.total)), basis: 'Recorded', caption: `${d.invoices.length} invoices`, hint: 'Total of all supplier invoices recorded.' },
        { label: 'Amounts paid', unit: 'LKR', value: M0(sum(d.invoices, (v) => v.paid)), hint: 'Payments made against supplier invoices.' },
        { label: 'Remaining payable', unit: 'LKR', value: M0(d.totals.supplierPayables), hint: 'Supplier invoice amounts not paid yet.' },
        { label: 'Overdue', unit: 'LKR', value: M0(sum(d.invoices.filter((v) => v.status === 'Overdue'), (v) => v.remaining)), tone: 'destructive', hint: 'Unpaid supplier invoices past their due date.' },
        { label: 'Other payables', unit: 'LKR', value: M0(d.totals.expensePayables + d.totals.commissionPayables + d.totals.payrollPayables), caption: 'Bills, commissions, wages', hint: 'Unpaid expenses, confirmed commission and wages.' },
      ]} />
      <Card className="gap-0 py-0">
        <Table>
          <TableHeader><TableRow><TableHead className="pl-6">Due date</TableHead><TableHead>Supplier</TableHead><TableHead>Invoice</TableHead><TableHead className="hidden lg:table-cell">Allocated to</TableHead><TableHead className="text-right">Total (LKR)</TableHead><TableHead className="hidden text-right md:table-cell">Paid (LKR)</TableHead><TableHead className="text-right">Remaining (LKR)</TableHead><TableHead className="pr-6">Status</TableHead></TableRow></TableHeader>
          <TableBody>{rows.map((v) => <TableRow key={v.invoice.id}><TableCell className="pl-6">{formatDate(v.invoice.dueDate)}</TableCell><TableCell>{v.supplier.name}</TableCell><TableCell className="font-mono text-[13px]">{v.invoice.invoiceNo}</TableCell><TableCell className="hidden lg:table-cell">{v.project?.code ?? 'Stock'}</TableCell><TableCell className="text-right">{M0(v.total)}</TableCell><TableCell className="hidden text-right md:table-cell">{M0(v.paid)}</TableCell><TableCell className="text-right font-medium">{M0(v.remaining)}</TableCell><TableCell className="pr-6"><StatusBadge status={v.status} /></TableCell></TableRow>)}</TableBody>
          <TableFooter><TableRow><TableCell className="pl-6" colSpan={3}>{rows.length} invoices</TableCell><TableCell className="hidden lg:table-cell" /><TableCell className="text-right">{M0(sum(rows, (v) => v.total))}</TableCell><TableCell className="hidden text-right md:table-cell">{M0(sum(rows, (v) => v.paid))}</TableCell><TableCell className="text-right">{M0(sum(rows, (v) => v.remaining))}</TableCell><TableCell /></TableRow></TableFooter>
        </Table>
      </Card>
    </>
  )
}

// ---------------------------------------------------------------- labour
export function LabourReport() {
  const d = useData()
  const [range, setRange] = useState(() => ({ from: monthStart(TODAY.slice(0, 7)), to: monthEnd(TODAY.slice(0, 7)), preset: 'month' }))
  const att = d.s.attendance.filter((a) => inRange(a.date, range))
  const emps = d.s.employees.map((e) => {
    const a = att.filter((x) => x.employeeId === e.id)
    const ot = d.s.overtime.filter((o) => o.employeeId === e.id && o.approved && inRange(o.date, range))
    const p = a.filter((x) => x.status === 'Present').length, h = a.filter((x) => x.status === 'Half Day').length
    return { e, p, h, ab: a.filter((x) => x.status === 'Absent').length, days: p + h / 2, wages: (p + h / 2) * e.dailyRate, otH: sum(ot, (o) => o.hours), otPay: sum(ot, (o) => o.hours * o.rate), adv: sum(d.s.advances.filter((x) => x.employeeId === e.id && inRange(x.date, range)), (x) => x.amount) }
  }).filter((x) => x.p + x.h + x.ab > 0)
  const weeks = d.payroll.filter((w) => w.weekEnd >= range.from && w.weekStart <= range.to).slice().reverse()
  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <DateRangeFilter value={range} onChange={setRange} />
        <ExportMenu subtitle={formatRange(range.from, range.to)} name="labour-payments" rows={() => [['Worker', 'Present', 'Half days', 'Absent', 'Days', 'Base wages', 'OT hours', 'OT pay', 'Advances given'], ...emps.map((x) => [x.e.name, x.p, x.h, x.ab, x.days, x.wages, x.otH, x.otPay, x.adv])]} />
      </div>
      <KpiStrip items={[
        { label: 'Base wages', unit: 'LKR', value: M0(sum(emps, (x) => x.wages)), basis: 'Recorded', caption: `${sum(emps, (x) => x.days)} worker-days`, hint: 'Days worked × daily rate in the range.' },
        { label: 'Overtime', unit: 'LKR', value: M0(sum(emps, (x) => x.otPay)), caption: `${sum(emps, (x) => x.otH)} approved hours`, hint: 'Pay for approved overtime hours in the range.' },
        { label: 'Advances given', unit: 'LKR', value: M0(sum(emps, (x) => x.adv)), caption: 'Recovered from weekly pay', hint: 'Salary advances given in the range. Taken back from weekly pay, not an extra cost.' },
        { label: 'Payroll paid', unit: 'LKR', value: M0(sum(weeks, (w) => w.paid)), caption: `${weeks.length} pay weeks overlap this range`, hint: 'Wage payments for pay weeks that overlap the range.' },
        { label: 'Payroll outstanding', unit: 'LKR', value: M0(sum(weeks, (w) => w.remaining)), hint: 'Wages still unpaid for those pay weeks.' },
      ]} />
      <Card className="gap-4 pb-0">
        <CardHeader><CardTitle>Attendance summary</CardTitle><CardDescription>{formatDate(range.from)} – {formatDate(range.to)}</CardDescription></CardHeader>
        <Table>
          <TableHeader><TableRow><TableHead className="pl-6">Worker</TableHead><TableHead className="text-right">Present</TableHead><TableHead className="text-right">Half</TableHead><TableHead className="text-right">Absent</TableHead><TableHead className="text-right">Base wages (LKR)</TableHead><TableHead className="hidden text-right md:table-cell">OT hrs</TableHead><TableHead className="hidden text-right md:table-cell">OT pay (LKR)</TableHead><TableHead className="pr-6 text-right">Advances (LKR)</TableHead></TableRow></TableHeader>
          <TableBody>{emps.map((x) => <TableRow key={x.e.id}><TableCell className="pl-6"><CellMain title={x.e.name} sub={x.e.role} /></TableCell><TableCell className="tabular text-right">{x.p}</TableCell><TableCell className="tabular text-right">{x.h}</TableCell><TableCell className="tabular text-right">{x.ab}</TableCell><TableCell className="text-right">{M0(x.wages)}</TableCell><TableCell className="tabular hidden text-right md:table-cell">{x.otH || '—'}</TableCell><TableCell className="hidden text-right md:table-cell">{x.otPay ? M0(x.otPay) : '—'}</TableCell><TableCell className="pr-6 text-right">{x.adv ? M0(x.adv) : '—'}</TableCell></TableRow>)}</TableBody>
        </Table>
      </Card>
      <Card className="gap-4 pb-0">
        <CardHeader><CardTitle>Weekly payroll</CardTitle></CardHeader>
        <Table>
          <TableHeader><TableRow><TableHead className="pl-6">Week</TableHead><TableHead className="text-right">Gross (LKR)</TableHead><TableHead className="text-right">Advances (LKR)</TableHead><TableHead className="text-right">Net (LKR)</TableHead><TableHead className="text-right">Paid (LKR)</TableHead><TableHead className="text-right">Outstanding (LKR)</TableHead><TableHead className="pr-6">Status</TableHead></TableRow></TableHeader>
          <TableBody>{weeks.map((w) => <TableRow key={w.weekStart}><TableCell className="pl-6">{formatRange(w.weekStart, w.weekEnd)}</TableCell><TableCell className="text-right">{M0(w.gross)}</TableCell><TableCell className="text-right">{w.advances ? <Money value={-w.advances} decimals={0} currency={false} /> : '—'}</TableCell><TableCell className="text-right">{M0(w.net)}</TableCell><TableCell className="text-right">{M0(w.paid)}</TableCell><TableCell className="text-right font-medium">{M0(w.remaining)}</TableCell><TableCell className="pr-6"><StatusBadge status={w.status} /></TableCell></TableRow>)}</TableBody>
        </Table>
      </Card>
    </>
  )
}
