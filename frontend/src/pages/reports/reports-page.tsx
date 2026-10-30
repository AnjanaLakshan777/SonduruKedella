import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowRight, BarChart3, FileSpreadsheet, HardHat, HandCoins, Receipt, Scale, Truck, Users } from 'lucide-react'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { Money } from '@/components/app/money'
import { useData } from '@/store/store'
import { fyLabel } from '@/lib/dates'
import { IncomeReport, ExpenseReport, ProfitabilityReport, ReceivablesReport, PayablesReport, LabourReport } from './reports'

export const REPORTS = [
  { id: 'income', label: 'Income', icon: HandCoins, desc: 'Revenue by category, project and month; recorded versus received.' },
  { id: 'expenses', label: 'Expenses', icon: Receipt, desc: 'Expenses by category and project; project versus company-level costs.' },
  { id: 'profitability', label: 'Project Profitability', icon: Scale, desc: 'Contract, revenue, direct costs, commissions, deductions and profit per project.' },
  { id: 'receivables', label: 'Client Receivables', icon: Users, desc: 'What clients owe, ageing, partial payments and bad-debt write-offs.' },
  { id: 'payables', label: 'Supplier Payables', icon: Truck, desc: 'Supplier invoices, amounts paid, remaining and overdue.' },
  { id: 'labour', label: 'Labour Payments', icon: HardHat, desc: 'Attendance, weekly payroll, advances and overtime.' },
] as const

export default function ReportsPage() {
  const { report } = useParams()
  const navigate = useNavigate()
  const d = useData()
  const current = REPORTS.find((r) => r.id === report)
  useCrumbs([{ label: 'Reports', to: '/reports' }, ...(current ? [{ label: current.label }] : [])])

  if (!current) {
    const highlights: Record<string, React.ReactNode> = {
      income: <>Revenue recorded <Money value={d.totals.revenueRecorded} decimals={0} /></>,
      expenses: <>Project costs <Money value={d.totals.projectCosts} decimals={0} /></>,
      profitability: <>Est. project profit <Money value={d.totals.projectProfit} decimals={0} /></>,
      receivables: <>Overdue <Money value={d.totals.overdue} decimals={0} /></>,
      payables: <>Supplier payables <Money value={d.totals.supplierPayables} decimals={0} /></>,
      labour: <>Payroll unpaid <Money value={d.totals.payrollPayables} decimals={0} /></>,
    }
    return (
      <>
        <PageHeader title="Reports" description={`Financial reports for the financial year ${fyLabel(d.s.settings.financialYearStart)}, built from the same records as every other screen. Cash-flow reports are not part of this version.`} />
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {REPORTS.map((r) => (
            <Link key={r.id} to={`/reports/${r.id}`} className="group focus-visible:ring-ring/50 rounded-xl outline-none focus-visible:ring-[3px]">
              <Card className="group-hover:border-primary-text/40 h-full gap-3 transition-colors">
                <CardHeader>
                  <div className="bg-primary-soft text-primary-soft-foreground mb-2 grid size-9 place-items-center rounded-md"><r.icon className="size-4" /></div>
                  <CardTitle className="flex items-center justify-between">{r.label}<ArrowRight className="text-muted-foreground size-4 transition-transform group-hover:translate-x-0.5" /></CardTitle>
                  <CardDescription>{r.desc}</CardDescription>
                  <div className="mt-2 text-sm">{highlights[r.id]}</div>
                </CardHeader>
              </Card>
            </Link>
          ))}
        </div>
        <div className="text-muted-foreground flex items-center gap-2 text-sm"><FileSpreadsheet className="size-4" />Every report can be exported to CSV. Excel export arrives with the backend.</div>
      </>
    )
  }

  return (
    <>
      <PageHeader title={current.label} description={current.desc} meta={<span className="text-muted-foreground flex items-center gap-1.5 text-xs font-medium"><BarChart3 className="size-3.5" />Report</span>} />
      <Tabs value={current.id} onValueChange={(v) => navigate(`/reports/${v}`)}>
        <TabsList variant="line" aria-label="Reports">{REPORTS.map((r) => <TabsTrigger key={r.id} value={r.id}>{r.label}</TabsTrigger>)}</TabsList>
      </Tabs>
      {current.id === 'income' && <IncomeReport />}
      {current.id === 'expenses' && <ExpenseReport />}
      {current.id === 'profitability' && <ProfitabilityReport />}
      {current.id === 'receivables' && <ReceivablesReport />}
      {current.id === 'payables' && <PayablesReport />}
      {current.id === 'labour' && <LabourReport />}
    </>
  )
}
