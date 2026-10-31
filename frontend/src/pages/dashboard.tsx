import { useMemo } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, Plus } from 'lucide-react'
import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { KpiStrip } from '@/components/app/kpi-strip'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { CellMain, Mono } from '@/components/app/data-table'
import { axisProps, ChartTooltip, Legend, moneyAxis } from '@/components/app/chart'
import { BasisToggle, useBasis } from '@/components/app/basis-toggle'
import { useData } from '@/store/store'
import { addDays, addMonths, diffDays, formatDate, formatDateShort, monthLabel, monthsBetween, TODAY } from '@/lib/dates'
import { formatLKR } from '@/lib/format'
import { sum } from '@/lib/finance'
import { cn } from '@/lib/utils'

const M = (v: number) => <Money value={v} decimals={0} currency={false} />

function ViewAll({ to, label }: { to: string; label: string }) {
  return (
    <CardAction>
      <Button variant="link" size="sm" asChild className="h-auto px-0"><Link to={to}>{label}<ArrowRight /></Link></Button>
    </CardAction>
  )
}

export default function Dashboard() {
  useCrumbs([{ label: 'Dashboard' }])
  const d = useData()
  const navigate = useNavigate()
  const t = d.totals

  const [basis, setBasis] = useBasis()
  const cash = basis === 'cash'
  // the charts follow the toggle too: cash months are money in and out on the day it moved
  const monthly = useMemo(
    () => monthsBetween(addMonths(TODAY.slice(0, 7), -11), TODAY.slice(0, 7)).map((k) => {
      const m = d.monthly.get(k)
      return { month: k, label: monthLabel(k), revenue: (cash ? m?.received : m?.revenue) ?? 0, expenses: (cash ? m?.paid : m?.expenses) ?? 0 }
    }),
    [d, cash],
  )
  const byType = useMemo(() => {
    const rows = (['Construction', 'Design', 'Consultation'] as const).map((type) => ({
      type, value: sum(d.projects.filter((p) => p.project.type === type), (p) => (cash ? p.received : p.revenueRecorded - p.writtenOff)),
    }))
    rows.push({ type: 'Other Income' as never, value: cash ? t.receivedOther : t.revenueOther })
    return rows
  }, [d, cash, t.revenueOther, t.receivedOther])
  const typeColors = ['var(--chart-5)', 'var(--chart-3)', 'var(--chart-1)', 'var(--muted-foreground)']
  const totalRev = sum(byType, (r) => r.value)
  const activeClients = new Set(d.projects.filter((p) => p.project.status === 'In Progress').map((p) => p.client.id)).size
  const expensesRecorded = sum(d.expenseLines, (l) => l.amount)
  // what's late is the client's payment, not the revenue, so the caption talks about cash to collect.
  // One figure when they're the same, rather than the same number twice
  const L0 = (v: number) => formatLKR(v, { decimals: 0, currency: false })
  const overdueText = <span className="text-destructive-soft-foreground font-medium">Cash overdue {L0(t.overdue)}</span>
  const owedCaption = t.receivable <= 0 ? 'All cash collected'
    : t.overdue <= 0 ? `Cash to collect ${L0(t.receivable)}`
    : Math.abs(t.overdue - t.receivable) < 0.5 ? overdueText
    : <><span>Cash to collect {L0(t.receivable)} · </span>{overdueText}</>
  const profitRows = d.projects
    .filter((p) => (cash ? p.received > 0 || p.cash.costs > 0 : p.revenueRecorded > 0))
    .map((p) => ({ id: p.project.id, name: p.project.name, code: p.project.code, profit: Math.round(cash ? p.cash.profit : p.profit), basis: p.profitBasis }))
    .sort((a, b) => b.profit - a.profit)

  const attention = d.projects.filter((p) => p.attention.length && p.project.status !== 'Planning').sort((a, b) => b.overdue - a.overdue).slice(0, 6)
  const overdue = [
    ...d.installments.filter((i) => i.status === 'Overdue').map((i) => ({ id: i.inst.id, client: i.client.name, sub: `${i.project.code} · ${i.inst.description}`, remaining: i.remaining, due: i.inst.billedOn ?? i.inst.dueDate, days: i.daysOverdue, to: `/projects/${i.project.id}?tab=payments` })),
    ...d.otherIncome.filter((o) => o.status === 'Overdue').map((o) => ({ id: o.income.id, client: o.income.payer ?? 'Other income', sub: `Other income · ${o.income.description}`, remaining: o.remaining, due: o.income.date, days: diffDays(TODAY, o.income.date), to: '/revenue' })),
  ].sort((a, b) => b.days - a.days)
  // the next two weeks
  const payablesBy = addDays(TODAY, 14)
  const payables = d.payablesDue.filter((p) => p.dueDate <= payablesBy).slice(0, 6)

  const recent = useMemo(() => {
    const rows: { id: string; date: string; text: string; party: string; amount: number; to: string }[] = []
    for (const i of d.installments) for (const p of i.payments) rows.push({ id: p.id, date: p.date, text: `Client payment — ${i.inst.description}`, party: i.client.name, amount: p.amount, to: `/projects/${i.project.id}?tab=payments` })
    for (const v of d.invoices) for (const p of v.payments) rows.push({ id: p.id, date: p.date, text: `Supplier payment — ${v.invoice.invoiceNo}`, party: v.supplier.name, amount: -p.amount, to: `/suppliers/${v.supplier.id}` })
    for (const e of d.s.expensePayments) {
      const x = d.s.expenses.find((y) => y.id === e.expenseId)!
      rows.push({ id: e.id, date: e.date, text: `${x.category} — ${x.description}`, party: x.payee, amount: -e.amount, to: `/expenses?q=${encodeURIComponent(x.description)}` })
    }
    for (const c of d.commissions) for (const p of c.payments) rows.push({ id: p.id, date: p.date, text: `Commission — ${c.project.code}`, party: c.arr.recipient, amount: -p.amount, to: '/commissions' })
    const wk = new Map<string, number>()
    for (const p of d.s.payrollPayments) wk.set(p.date, (wk.get(p.date) ?? 0) + p.amount)
    for (const [date, amt] of wk) rows.push({ id: `wk${date}`, date, text: 'Weekly wages paid', party: 'Site workers', amount: -amt, to: '/labour/payroll' })
    return rows.sort((a, b) => b.date.localeCompare(a.date) || b.id.localeCompare(a.id)).slice(0, 8)
  }, [d])

  return (
    <>
      <PageHeader
        title="Dashboard"
        description={`All figures to date · as of ${formatDate(TODAY)}`}
        actions={<><BasisToggle value={basis} onChange={setBasis} /><Button asChild><Link to="/projects/new"><Plus />Create Project</Link></Button></>}
      />

      <KpiStrip
        items={[
          { label: 'Active Projects', value: t.activeProjects, caption: `${t.activeByType.Construction} construction · ${t.activeByType.Design} design · ${t.activeByType.Consultation} consultation` },
          { label: 'Contract Value (active)', unit: 'LKR', value: M(t.activeContractValue), basis: 'Agreed', caption: 'Not revenue until billed', hint: 'Total agreed contract value of In Progress projects. Only billed installments count as revenue.' },
          { label: 'Total Clients', value: d.s.clients.length, caption: `${activeClients} with active projects` },
          cash
            ? { label: 'Total Revenue', unit: 'LKR', value: M(t.paymentsReceived), basis: 'Received', caption: `Accrual: ${formatLKR(t.revenueNet, { decimals: 0, currency: false })}`, hint: 'Money actually received: client payments plus other income receipts, on the day they came in.' }
            : { label: 'Total Revenue', unit: 'LKR', value: M(t.revenueNet), basis: 'Recorded', caption: owedCaption, hint: 'Billed installments (net of delay deductions) plus other income, less amounts written off as bad debt. An installment is counted once, however many payments it takes.' },
          cash
            ? { label: 'Total Expenses', unit: 'LKR', value: M(t.cashPaidOut), basis: 'Received', caption: `Accrual: ${formatLKR(expensesRecorded, { decimals: 0, currency: false })}`, hint: 'Money actually paid out: expense, supplier (incl. stock purchases), wage, salary advance and commission payments, on the day they were paid.' }
            : { label: 'Total Expenses', unit: 'LKR', value: M(expensesRecorded), basis: 'Recorded', caption: `incl. ${formatLKR(t.companyExpenses, { decimals: 0, currency: false })} company-level`, hint: 'Project costs (direct expenses, materials to site, stock issued at cost, wages, confirmed commission) plus company-level expenses, whether paid or not. Stock purchases count only when issued.' },
          cash
            ? { label: 'Cash Project Profit', unit: 'LKR', value: M(t.projectCashProfit), basis: 'Received', caption: `Accrual: ${formatLKR(t.projectProfit, { decimals: 0, currency: false })}`, hint: 'Client payments received − money paid out for projects (expense, supplier, wage and commission payments, plus stock issued at cost). Unpaid bills and amounts still owed by clients are left out. Excludes company-level expenses.' }
            : { label: 'Est. Project Profit', unit: 'LKR', value: M(t.projectProfit), basis: 'Estimated', caption: `Cash basis: ${formatLKR(t.projectCashProfit, { decimals: 0, currency: false })}`, hint: 'Revenue recorded − written off − project costs (materials, labour, expenses, confirmed commissions), whether paid or not. Excludes costs not yet incurred and company-level expenses.' },
        ]}
      />

      <div className="grid gap-6 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader>
            <CardTitle>Monthly Revenue vs Expenses</CardTitle>
            <CardDescription>{cash ? 'Money received against money paid out (project and company), last 12 months' : 'Revenue recorded against all expenses recorded (project and company), last 12 months'}</CardDescription>
            <CardAction><Legend items={[{ label: cash ? 'Received' : 'Revenue recorded', color: 'var(--chart-3)' }, { label: cash ? 'Paid out' : 'Expenses', color: 'var(--muted-foreground)' }]} /></CardAction>
          </CardHeader>
          <CardContent className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={monthly} barGap={2} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
                <CartesianGrid vertical={false} stroke="var(--border)" />
                <XAxis dataKey="label" {...axisProps} />
                <YAxis {...moneyAxis} />
                <Tooltip cursor={{ fill: 'var(--muted)', opacity: 0.6 }} content={<ChartTooltip />} />
                <Bar dataKey="revenue" name={cash ? 'Received' : 'Revenue recorded'} fill="var(--chart-3)" radius={[3, 3, 0, 0]} maxBarSize={28} />
                <Bar dataKey="expenses" name={cash ? 'Paid out' : 'Expenses'} fill="var(--muted-foreground)" radius={[3, 3, 0, 0]} maxBarSize={28} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Revenue by Type</CardTitle>
            <CardDescription>{cash ? 'Money received to date' : 'Revenue recorded to date'}</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4">
            <div className="flex h-3 overflow-hidden rounded-full" role="img" aria-label="Revenue share by project type">
              {byType.map((r, i) => r.value > 0 && <div key={r.type} style={{ width: `${(r.value / totalRev) * 100}%`, background: typeColors[i] }} className="border-card border-r-2 last:border-r-0" />)}
            </div>
            <dl className="grid gap-3">
              {byType.map((r, i) => (
                <div key={r.type} className="flex items-center justify-between gap-3 text-sm">
                  <dt className="flex items-center gap-2"><span className="size-2.5 rounded-[3px]" style={{ background: typeColors[i] }} />{r.type}</dt>
                  <dd className="flex items-baseline gap-3"><span className="text-muted-foreground tabular text-xs">{((r.value / totalRev) * 100).toFixed(1)}%</span><Money value={r.value} decimals={0} className="font-medium" /></dd>
                </div>
              ))}
            </dl>
            <div className="mt-2 grid gap-2 border-t pt-4">
              <div className="flex items-baseline justify-between text-sm"><span className="font-medium">Collected from revenue recorded</span><span className="tabular text-muted-foreground text-xs">{((t.paymentsReceived / t.revenueNet) * 100).toFixed(0)}%</span></div>
              <Progress value={(t.paymentsReceived / t.revenueNet) * 100} aria-label="Collected" />
              <div className="text-muted-foreground flex justify-between text-xs"><span>Received {formatLKR(t.paymentsReceived, { decimals: 0 })}</span><span>Recorded {formatLKR(t.revenueNet, { decimals: 0 })}</span></div>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-6 xl:grid-cols-5">
        <Card className="xl:col-span-2">
          <CardHeader>
            <CardTitle>Project Profitability</CardTitle>
            <CardDescription>{cash ? 'Cash profit per project — received less paid out' : 'Profit to date per project — estimated until completed'}</CardDescription>
            <ViewAll to="/reports/profitability" label="Full report" />
          </CardHeader>
          <CardContent style={{ height: profitRows.length * 34 + 24 }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={profitRows} layout="vertical" margin={{ top: 0, right: 8, left: 0, bottom: 0 }}>
                <XAxis type="number" {...moneyAxis} height={20} />
                <YAxis type="category" dataKey="code" {...axisProps} width={96} tick={{ fill: 'var(--muted-foreground)', fontSize: 11, fontFamily: 'var(--font-mono)' }} />
                <ReferenceLine x={0} stroke="var(--border)" />
                <Tooltip cursor={{ fill: 'var(--muted)', opacity: 0.6 }} content={<ChartTooltip labelFormatter={(code) => profitRows.find((r) => r.code === code)?.name ?? code} />} />
                <Bar dataKey="profit" name={cash ? 'Cash profit' : 'Profit to date'} radius={[0, 3, 3, 0]} maxBarSize={18} onClick={(r: { id?: string }) => r.id && navigate(`/projects/${r.id}`)} className="cursor-pointer">
                  {profitRows.map((r) => <Cell key={r.id} fill={r.profit < 0 ? 'var(--destructive)' : cash || r.basis === 'Recorded' ? 'var(--chart-5)' : 'var(--chart-3)'} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
          {!cash && <CardContent className="-mt-2"><Legend items={[{ label: 'Estimated (in progress)', color: 'var(--chart-3)' }, { label: 'Recorded (completed)', color: 'var(--chart-5)' }]} /></CardContent>}
        </Card>

        <Card className="gap-4 pb-0 xl:col-span-3">
          <CardHeader>
            <CardTitle>Projects Requiring Attention</CardTitle>
            <CardDescription>Overdue money, schedule risk, policy warnings</CardDescription>
            <ViewAll to="/projects" label="All projects" />
          </CardHeader>
          <Table>
            <TableHeader><TableRow><TableHead className="pl-6">Project</TableHead><TableHead>Status</TableHead><TableHead>Why</TableHead></TableRow></TableHeader>
            <TableBody>
              {attention.map((p) => (
                <TableRow key={p.project.id} className="cursor-pointer" onClick={() => navigate(`/projects/${p.project.id}`)}>
                  <TableCell className="max-w-64 pl-6"><CellMain title={p.project.name} sub={`${p.project.code} · ${p.client.name}`} /></TableCell>
                  <TableCell><StatusBadge status={p.project.status} /></TableCell>
                  <TableCell className="pr-6 whitespace-normal">
                    <ul className="grid gap-0.5 text-[13px]">{p.attention.slice(0, 2).map((a) => <li key={a.text} className={cn(a.tone === 'danger' && 'text-destructive-soft-foreground font-medium')}>{a.text}</li>)}</ul>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      </div>

      <div className="grid gap-6 xl:grid-cols-2">
        <Card className="gap-4 pb-0">
          <CardHeader>
            <CardTitle>Overdue Client Receivables</CardTitle>
            <CardDescription>Billed and still unpaid · total {formatLKR(sum(overdue, (i) => i.remaining))}</CardDescription>
            <ViewAll to="/clients?tab=receivables" label="All receivables" />
          </CardHeader>
          <Table>
            <TableHeader><TableRow><TableHead className="pl-6">Client</TableHead><TableHead className="text-right">Remaining (LKR)</TableHead><TableHead>Billed</TableHead><TableHead className="pr-6 text-right">Days</TableHead></TableRow></TableHeader>
            <TableBody>
              {overdue.map((i) => (
                <TableRow key={i.id} className="cursor-pointer" onClick={() => navigate(i.to)}>
                  <TableCell className="max-w-60 pl-6"><CellMain title={i.client} sub={i.sub} /></TableCell>
                  <TableCell className="text-right font-medium"><Money value={i.remaining} currency={false} /></TableCell>
                  <TableCell>{formatDate(i.due)}</TableCell>
                  <TableCell className="pr-6 text-right"><span className="bg-destructive-soft text-destructive-soft-foreground tabular rounded-md px-2 py-0.5 text-xs font-medium">{i.days} days</span></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>

        <Card className="gap-4 pb-0">
          <CardHeader>
            <CardTitle>Upcoming Supplier Payments</CardTitle>
            <CardDescription>Credit invoices and bills due by {formatDate(payablesBy)}</CardDescription>
            <ViewAll to="/suppliers?tab=payables" label="All payables" />
          </CardHeader>
          <Table>
            <TableHeader><TableRow><TableHead className="pl-6">Payee</TableHead><TableHead className="text-right">Amount (LKR)</TableHead><TableHead>Due</TableHead><TableHead className="pr-6">Status</TableHead></TableRow></TableHeader>
            <TableBody>
              {payables.map((p) => (
                <TableRow key={p.id}>
                  <TableCell className="max-w-60 pl-6"><CellMain title={p.payee} sub={<>{p.kind === 'Supplier invoice' ? <Mono>{p.ref}</Mono> : p.ref}{p.projectId ? ` · ${d.projectById.get(p.projectId)?.code}` : ' · Stock / company'}</>} /></TableCell>
                  <TableCell className="text-right font-medium"><Money value={p.amount} currency={false} /></TableCell>
                  <TableCell>{formatDateShort(p.dueDate)}</TableCell>
                  <TableCell className="pr-6"><StatusBadge status={p.status === 'Overdue' ? 'Overdue' : p.status === 'Partially Paid' ? 'Partially Paid' : 'Due'} /></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      </div>

      <Card className="gap-4 pb-0">
        <CardHeader>
          <CardTitle>Recent Financial Transactions</CardTitle>
          <CardDescription>Money received and paid, newest first</CardDescription>
          <ViewAll to="/reports/income" label="Reports" />
        </CardHeader>
        <Table>
          <TableHeader><TableRow><TableHead className="pl-6">Date</TableHead><TableHead>Transaction</TableHead><TableHead className="hidden md:table-cell">Party</TableHead><TableHead className="pr-6 text-right">Amount (LKR)</TableHead></TableRow></TableHeader>
          <TableBody>
            {recent.map((r) => (
              <TableRow key={r.id} className="cursor-pointer" onClick={() => navigate(r.to)}>
                <TableCell className="pl-6">{formatDate(r.date)}</TableCell>
                <TableCell className="max-w-96 truncate">{r.text}</TableCell>
                <TableCell className="text-muted-foreground hidden md:table-cell">{r.party}</TableCell>
                <TableCell className="pr-6 text-right font-medium"><Money value={r.amount} currency={false} signed tone={r.amount > 0 ? 'positive' : 'default'} /></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </>
  )
}
