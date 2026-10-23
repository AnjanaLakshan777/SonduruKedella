import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Plus, Receipt } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { KpiStrip } from '@/components/app/kpi-strip'
import { Money } from '@/components/app/money'
import { ExpenseTable } from '@/components/app/expense-table'
import { RecordExpenseDialog } from '@/components/app/record-expense-dialog'
import { ALL, DateRangeFilter, FilterBar, FilterSelect, SearchInput, defaultRange, inRange } from '@/components/app/filters'
import { EmptyState } from '@/components/app/empty-state'
import { useData } from '@/store/store'
import { sum } from '@/lib/finance'
import { formatLKR } from '@/lib/format'

export default function ExpensesPage() {
  useCrumbs([{ label: 'Expenses' }])
  const d = useData()
  const [params] = useSearchParams()
  const [q, setQ] = useState(params.get('q') ?? '')
  const [range, setRange] = useState(defaultRange())
  const [project, setProject] = useState(ALL)
  const [category, setCategory] = useState(ALL)
  const [payee, setPayee] = useState(ALL)
  const [status, setStatus] = useState(ALL)
  const [source, setSource] = useState(ALL)
  const [open, setOpen] = useState(false)

  const cats = useMemo(() => [...new Set(Object.values(d.s.settings.expenseCategories).flat())], [d])
  const rows = useMemo(() => d.expenseLines.filter((l) =>
    inRange(l.date, range)
    && (project === ALL || (project === 'company' ? l.scope === 'Company' : project === 'projects' ? l.scope === 'Project' : l.projectId === project))
    && (category === ALL || l.category === category)
    && (payee === ALL || l.payee === payee)
    && (status === ALL || l.status === status)
    && (source === ALL || l.source === source)
    && (!q || `${l.description} ${l.payee} ${l.category}`.toLowerCase().includes(q.toLowerCase())),
  ), [d, range, project, category, payee, status, source, q])
  const active = q !== '' || project !== ALL || category !== ALL || payee !== ALL || status !== ALL || source !== ALL || range.preset !== 'fy'
  const clear = () => { setQ(''); setProject(ALL); setCategory(ALL); setPayee(ALL); setStatus(ALL); setSource(ALL); setRange(defaultRange()) }

  return (
    <>
      <PageHeader title="Expenses" description="Every cost, marked as a project expense or a company-level expense. Materials, labour and commissions from other modules appear here automatically." actions={<Button onClick={() => setOpen(true)}><Plus />Record Expense</Button>} />
      <KpiStrip items={[
        { label: 'Expenses in view', unit: 'LKR', value: <Money value={sum(rows, (l) => l.amount)} decimals={0} currency={false} />, basis: 'Recorded', caption: `${rows.length} records` },
        { label: 'Project expenses', unit: 'LKR', value: <Money value={sum(rows.filter((l) => l.scope === 'Project'), (l) => l.amount)} decimals={0} currency={false} />, caption: 'Counted in project profitability' },
        { label: 'Company-level', unit: 'LKR', value: <Money value={sum(rows.filter((l) => l.scope === 'Company'), (l) => l.amount)} decimals={0} currency={false} />, caption: 'Not assigned to any project' },
        { label: 'Unpaid', unit: 'LKR', value: <Money value={sum(rows, (l) => l.remaining)} decimals={0} currency={false} />, caption: `${formatLKR(sum(rows.filter((l) => l.status === 'Overdue'), (l) => l.remaining), { decimals: 0 })} overdue` },
      ]} />
      <FilterBar active={active} onClear={clear}>
        <SearchInput value={q} onChange={setQ} placeholder="Search description, payee…" />
        <DateRangeFilter value={range} onChange={setRange} />
        <FilterSelect label="Projects" value={project} onChange={setProject} options={[{ value: 'projects', label: 'All project expenses' }, { value: 'company', label: 'Company-level only' }, ...d.s.projects.map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }))]} />
        <FilterSelect label="Categories" value={category} onChange={setCategory} options={cats} />
        <FilterSelect label="Suppliers / payees" value={payee} onChange={setPayee} options={[...new Set(d.expenseLines.map((l) => l.payee))].sort()} />
        <FilterSelect label="Payment statuses" value={status} onChange={setStatus} options={['Paid', 'Partially Paid', 'Unpaid', 'Overdue', 'From Stock']} />
        <FilterSelect label="Sources" value={source} onChange={setSource} options={['Manual', 'Supplier invoice', 'Stock issue', 'Payroll', 'Commission']} />
      </FilterBar>
      <Card className="gap-0 py-0">
        <ExpenseTable lines={rows} empty={<EmptyState icon={Receipt} title="No expenses match" description="Change the filters, or record a new expense." action={<Button size="sm" variant="outline" onClick={clear}>Clear filters</Button>} />} />
      </Card>
      <RecordExpenseDialog open={open} onOpenChange={setOpen} />
    </>
  )
}
