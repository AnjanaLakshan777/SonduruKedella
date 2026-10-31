import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { ArrowRight, ChevronDown, CircleAlert, FileText, HandCoins, Lock, Phone, Receipt } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Progress } from '@/components/ui/progress'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { KpiStrip } from '@/components/app/kpi-strip'
import { FigureList } from '@/components/app/figure-list'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { EmptyState } from '@/components/app/empty-state'
import { FileDropzone } from '@/components/app/file-dropzone'
import { BasisToggle, useBasis } from '@/components/app/basis-toggle'
import { useData, useStore } from '@/store/store'
import { projectActivity } from '@/lib/finance'
import { diffDays, formatDate, TODAY } from '@/lib/dates'
import { apiUrl, errorMessage } from '@/lib/api'
import { formatLKR } from '@/lib/format'
import type { ProjectStatus } from '@/data/types'
import { cn } from '@/lib/utils'
import { ProjectPayments } from './project-payments'
import { ProjectExpenses } from './project-expenses'
import { ProjectCommissions } from './project-commissions'

const M = (v: number) => <Money value={v} decimals={0} currency={false} />

export default function ProjectDetail() {
  const { id = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const d = useData()
  const navigate = useNavigate()
  const { setProjectStatus, uploadAgreement } = useStore()
  const pf = d.projectFinById.get(id)
  useCrumbs([{ label: 'Projects', to: '/projects' }, { label: pf?.project.name ?? 'Not found' }])
  const [payFor, setPayFor] = useState<string | null>(null)
  const [basis, setBasis] = useBasis()
  const cash = basis === 'cash'
  if (!pf) return <EmptyState title="Project not found" description="It may have been removed." action={<Button asChild><Link to="/projects">Back to Projects</Link></Button>} />

  const p = pf.project
  const tab = params.get('tab') ?? 'overview'
  const setTab = (t: string) => setParams(t === 'overview' ? {} : { tab: t }, { replace: true })
  const nextDue = pf.installments.find((i) => i.remaining > 0 && (i.inst.billed || i.status === 'Upcoming'))
  const payable = pf.installments.filter((i) => i.inst.billed && i.remaining > 0)
  const expenseCount = d.expenseLines.filter((l) => l.projectId === id).length
  const hasCommission = p.type !== 'Consultation'
  const activity = projectActivity(d, id)

  async function changeStatus(s: ProjectStatus) {
    try {
      await setProjectStatus(p.id, s)
      toast.success(`Status changed to ${s}`, { description: s === 'Completed' && p.delay?.enabled && !p.delay.appliedAmount ? 'Check the delay deduction on the Payments tab before the final invoice.' : undefined })
    } catch (e) {
      toast.error('Status not changed', { description: errorMessage(e) })
    }
  }

  return (
    <>
      <PageHeader
        meta={<><span className="text-muted-foreground font-mono text-xs font-medium">{p.code}</span><Badge variant="outline">{p.type}</Badge><StatusBadge status={p.status} /></>}
        title={p.name}
        description={<>For <Link to={`/clients/${pf.client.id}`} className="text-foreground underline-offset-4 hover:underline">{pf.client.name}</Link> · {p.location} · {formatDate(p.startDate)} – {formatDate(p.expectedCompletion)}</>}
        actions={
          <>
            <DropdownMenu>
              <DropdownMenuTrigger asChild><Button variant="outline">Change status<ChevronDown /></Button></DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuLabel>Project status</DropdownMenuLabel>
                <DropdownMenuSeparator />
                {d.s.settings.projectStatuses.map((s) => <DropdownMenuItem key={s} disabled={s === p.status} onSelect={() => changeStatus(s)}>{s}</DropdownMenuItem>)}
              </DropdownMenuContent>
            </DropdownMenu>
            <Button onClick={() => { setTab('payments'); setPayFor(payable[0]?.inst.id ?? null) }} disabled={!payable.length} title={!payable.length ? 'Nothing billed is outstanding' : undefined}><HandCoins />Record Payment</Button>
          </>
        }
      />

      <KpiStrip
        items={[
          { label: 'Contract Value', unit: 'LKR', value: M(pf.contractValue), basis: 'Agreed', caption: pf.deduction ? `Adjusted ${formatLKR(pf.adjustedValue, { decimals: 0, currency: false })} after deduction` : 'Unchanged by deductions' },
          { label: 'Revenue Recorded', unit: 'LKR', value: M(pf.revenueRecorded - pf.writtenOff), basis: 'Recorded', caption: pf.writtenOff ? `After ${formatLKR(pf.writtenOff, { decimals: 0, currency: false })} written off` : `${pf.installments.filter((i) => i.inst.billed).length} of ${pf.installments.length} installments billed` },
          { label: 'Payments Received', unit: 'LKR', value: M(pf.received), basis: 'Received', caption: `${pf.collectedPct.toFixed(0)}% of amount payable` },
          { label: 'Receivable', unit: 'LKR', value: M(pf.receivable), basis: 'Recorded', caption: pf.overdue ? <span className="text-destructive-soft-foreground font-medium">{formatLKR(pf.overdue, { decimals: 0, currency: false })} overdue</span> : 'Nothing overdue' },
          { label: 'Project Costs', unit: 'LKR', value: M(pf.totalCosts), basis: 'Recorded', caption: pf.payables.total ? `${formatLKR(pf.payables.total, { decimals: 0, currency: false })} still to pay` : 'All paid' },
          cash
            ? { label: 'Cash Profit', unit: 'LKR', value: <Money value={pf.cash.profit} decimals={0} currency={false} tone="auto" />, basis: 'Received', caption: `Accrual: ${formatLKR(pf.profit, { decimals: 0, currency: false })}`, hint: 'Client payments received − money paid out for this project (expense, supplier, wage and commission payments, plus stock issued at cost).' }
            : { label: pf.profitBasis === 'Recorded' ? 'Project Profit' : 'Profit to Date', unit: 'LKR', value: <Money value={pf.profit} decimals={0} currency={false} tone="auto" />, basis: pf.profitBasis, caption: pf.profitBasis === 'Estimated' ? `Cash basis: ${formatLKR(pf.cash.profit, { decimals: 0, currency: false })}` : `${pf.margin.toFixed(1)}% margin`, hint: 'Revenue recorded − written off − project costs (materials, labour, expenses, confirmed commissions), whether paid or not. Costs still to come are not included.' },
        ]}
      />

      <Tabs value={tab} onValueChange={setTab} className="gap-6">
        <TabsList variant="line" aria-label="Project sections">
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="agreement">Agreement{!p.agreement && p.type !== 'Consultation' && <span className="bg-warning size-1.5 rounded-full" aria-label="missing" />}</TabsTrigger>
          <TabsTrigger value="payments">Payments & Receivables<Badge variant="secondary" className="px-1.5 py-0">{pf.installments.length}</Badge></TabsTrigger>
          <TabsTrigger value="expenses">Expenses<Badge variant="secondary" className="px-1.5 py-0">{expenseCount}</Badge></TabsTrigger>
          {hasCommission && <TabsTrigger value="commissions">Commissions</TabsTrigger>}
          <TabsTrigger value="activity">Activity</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="grid items-start gap-6 xl:grid-cols-3">
          <div className="grid gap-6 xl:col-span-2">
            {pf.attention.length > 0 && (
              <div className="bg-warning-soft text-warning flex items-start gap-3 rounded-lg border border-current/20 px-4 py-3 text-sm">
                <CircleAlert className="mt-0.5 size-4 shrink-0" />
                <div><div className="font-medium">Needs attention</div><div>{pf.attention.join(' · ')}</div></div>
              </div>
            )}
            <div className="grid gap-6 lg:grid-cols-2">
              <Card>
                <CardHeader><CardTitle>Client money</CardTitle><CardDescription>From agreed value to what is still owed</CardDescription></CardHeader>
                <CardContent>
                  <FigureList decimals={0} rows={[
                    { label: 'Agreed contract value', value: pf.contractValue, note: 'Unchanged by deductions' },
                    ...(pf.deduction ? [{ kind: 'less' as const, label: 'Delay deduction', value: pf.deduction, note: 'Liquidated damages, applied ' + formatDate(p.delay?.appliedOn) }] : []),
                    { kind: 'total', label: 'Adjusted amount payable', value: pf.adjustedValue },
                    { label: 'Revenue recorded (billed)', value: pf.revenueRecorded },
                    { kind: 'sub', label: 'Scheduled, not yet billed', value: pf.unbilled },
                    { kind: 'less', label: 'Payments received', value: pf.received },
                    ...(pf.writtenOff ? [{ kind: 'less' as const, label: 'Written off (bad debt)', value: pf.writtenOff, note: 'Not money received' }] : []),
                    { kind: 'highlight', label: 'Remaining receivable', value: pf.receivable },
                  ]} />
                </CardContent>
              </Card>
              <Card>
                <CardHeader>
                  <CardTitle>Costs and result</CardTitle>
                  <CardDescription>{cash ? 'Money received and paid out to date' : `Recorded to date · ${pf.profitBasis === 'Estimated' ? 'not final' : 'project completed'}`}</CardDescription>
                  <CardAction><BasisToggle value={basis} onChange={setBasis} /></CardAction>
                </CardHeader>
                <CardContent>
                  {cash ? (
                    <>
                      <FigureList decimals={0} rows={[
                        { label: 'Payments received from client', value: pf.cash.received },
                        ...(pf.cash.materials ? [{ kind: 'less' as const, label: 'Materials paid for', value: pf.cash.materials, note: pf.costs.materialsStock ? 'Incl. stock issued at FIFO cost — paid when the stock was bought' : undefined }] : []),
                        ...(pf.cash.labour ? [{ kind: 'less' as const, label: 'Wages paid', value: pf.cash.labour }] : []),
                        ...(pf.cash.expenses ? [{ kind: 'less' as const, label: 'Other expenses paid', value: pf.cash.expenses }] : []),
                        ...(pf.cash.commission ? [{ kind: 'less' as const, label: 'Commission paid', value: pf.cash.commission }] : []),
                        { kind: 'highlight', label: <span className="flex items-center gap-2">Cash profit<StatusBadge status="Received" /></span>, value: <Money value={pf.cash.profit} decimals={0} tone="auto" /> },
                      ]} />
                      <p className="text-muted-foreground mt-3 text-xs">Cash basis: unpaid bills ({formatLKR(pf.payables.total, { decimals: 0 })}) and money the client still owes ({formatLKR(pf.receivable, { decimals: 0 })}) are not counted until they are paid.</p>
                    </>
                  ) : (<>
                  <FigureList decimals={0} rows={[
                    { label: 'Revenue recorded', value: pf.revenueRecorded - pf.writtenOff, note: pf.writtenOff ? 'After write-offs' : undefined },
                    ...(pf.costs.materialsDirect ? [{ kind: 'less' as const, label: 'Materials delivered to site', value: pf.costs.materialsDirect }] : []),
                    ...(pf.costs.materialsStock ? [{ kind: 'less' as const, label: 'Materials issued from stock', value: pf.costs.materialsStock, note: 'At FIFO issue cost, less returns — stock purchases are not counted again' }] : []),
                    ...(pf.costs.labour ? [{ kind: 'less' as const, label: 'Labour (wages and overtime)', value: pf.costs.labour }] : []),
                    ...Object.entries(pf.costs.expensesByCategory).map(([k, v]) => ({ kind: 'less' as const, label: k, value: v })),
                    ...(pf.commissionCommitted ? [{ kind: 'less' as const, label: 'Commission committed', value: pf.commissionCommitted, note: `${formatLKR(pf.commissionPaid)} paid so far` }] : []),
                    { kind: 'highlight', label: <span className="flex items-center gap-2">{pf.profitBasis === 'Recorded' ? 'Project profit' : 'Profit to date'}<StatusBadge status={pf.profitBasis} /></span>, value: <Money value={pf.profit} decimals={0} tone="auto" /> },
                  ]} />
                  {pf.profitBasis === 'Estimated' && <p className="text-muted-foreground mt-3 text-xs">Estimated: work, materials and bills still to come are not included, so the final profit will be lower than this figure.</p>}
                  </>)}
                </CardContent>
              </Card>
            </div>
            <Card>
              <CardHeader>
                <CardTitle>Payment schedule</CardTitle>
                <CardDescription>{pf.installments.filter((i) => i.status === 'Paid').length} paid · {pf.installments.filter((i) => i.status === 'Overdue').length} overdue · {pf.installments.filter((i) => i.status === 'Upcoming').length} upcoming</CardDescription>
                <CardAction><Button variant="link" size="sm" className="h-auto px-0" onClick={() => setTab('payments')}>Manage<ArrowRight /></Button></CardAction>
              </CardHeader>
              <CardContent className="grid gap-2">
                {pf.installments.map((i) => (
                  <div key={i.inst.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 border-b pb-2 text-sm last:border-b-0 last:pb-0">
                    <span className="min-w-0 flex-1 truncate">{i.inst.description}</span>
                    <span className="text-muted-foreground w-24">{formatDate(i.inst.dueDate)}</span>
                    <Money value={i.net} decimals={0} className="w-32 text-right" />
                    <span className="w-28"><StatusBadge status={i.status} /></span>
                  </div>
                ))}
                {!pf.installments.length && <p className="text-muted-foreground text-sm">No schedule yet.</p>}
              </CardContent>
            </Card>
          </div>

          <div className="grid gap-6">
            <Card>
              <CardHeader><CardTitle>Project details</CardTitle></CardHeader>
              <CardContent>
                <dl className="grid gap-3 text-sm">
                  {[
                    ['Client', <Link key="c" to={`/clients/${pf.client.id}`} className="font-medium underline-offset-4 hover:underline">{pf.client.name}</Link>],
                    ['Phone', <a key="t" href={`tel:${pf.client.phone.replace(/\s/g, '')}`} className="inline-flex items-center gap-1.5"><Phone className="text-muted-foreground size-3.5" />{pf.client.phone}</a>],
                    ['Type', p.type],
                    ['Start date', formatDate(p.startDate)],
                    ['Expected completion', <span key="e">{formatDate(p.expectedCompletion)}{p.status !== 'Completed' && p.expectedCompletion < TODAY && <span className="text-destructive ml-1.5 text-xs">({diffDays(TODAY, p.expectedCompletion)} days past)</span>}</span>],
                    ...(p.actualCompletion ? [['Actual completion', formatDate(p.actualCompletion)]] : []),
                    ['Agreement', p.agreement ? <button key="a" className="inline-flex items-center gap-1.5 underline-offset-4 hover:underline" onClick={() => setTab('agreement')}><FileText className="size-3.5" />Uploaded {formatDate(p.agreement.uploadedAt)}</button> : <button key="a" className="text-warning underline-offset-4 hover:underline" onClick={() => setTab('agreement')}>Not uploaded</button>],
                  ].map(([k, v], i) => (
                    <div key={i} className="grid grid-cols-[130px_1fr] gap-3"><dt className="text-muted-foreground">{k}</dt><dd className="min-w-0">{v}</dd></div>
                  ))}
                </dl>
                {p.description && <p className="text-muted-foreground mt-4 border-t pt-4 text-sm">{p.description}</p>}
                {p.notes && <p className="bg-muted/60 mt-3 rounded-md p-3 text-sm">{p.notes}</p>}
              </CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle>Collection</CardTitle><CardDescription>Received against the adjusted amount payable</CardDescription></CardHeader>
              <CardContent className="grid gap-3">
                <div className="flex items-baseline justify-between text-sm"><Money value={pf.received} decimals={0} className="font-semibold" /><span className="text-muted-foreground tabular">{pf.collectedPct.toFixed(0)}%</span></div>
                <Progress value={pf.collectedPct} indicatorClassName={pf.collectedPct >= 99.99 ? 'bg-success' : undefined} aria-label="Collected" />
                <div className="text-muted-foreground text-xs">of {formatLKR(pf.adjustedValue, { decimals: 0 })}</div>
                {nextDue && (
                  <div className="bg-muted/50 mt-1 grid gap-1 rounded-md p-3 text-sm">
                    <span className="text-muted-foreground text-xs">{nextDue.status === 'Overdue' ? 'Overdue' : 'Next due'}</span>
                    <span className="font-medium">{nextDue.inst.description}</span>
                    <span className="flex justify-between"><Money value={nextDue.remaining} decimals={0} /><span className={cn(nextDue.status === 'Overdue' && 'text-destructive')}>{formatDate(nextDue.inst.dueDate)}</span></span>
                  </div>
                )}
              </CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle>Still to pay for this project</CardTitle></CardHeader>
              <CardContent>
                <FigureList rows={[
                  { label: 'Suppliers (site deliveries)', value: pf.payables.suppliers },
                  { label: 'Other project expenses', value: pf.payables.expenses },
                  ...(hasCommission ? [{ label: 'Commission', value: pf.payables.commission }] : []),
                  { kind: 'total' as const, label: 'Project payables', value: pf.payables.total },
                ]} />
                <Button variant="link" size="sm" className="mt-2 h-auto px-0" onClick={() => setTab('expenses')}><Receipt />View project expenses</Button>
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        <TabsContent value="agreement" className="grid max-w-3xl gap-4">
          <Card>
            <CardHeader>
              <CardTitle>Agreement document</CardTitle>
              <CardDescription>Upload the signed agreement as a PDF so it stays with the project. This app stores an existing agreement — it does not create or sign one.</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4">
              <FileDropzone doc={p.agreement} onUpload={(file) => uploadAgreement(p.id, file)} downloadUrl={apiUrl(`/projects/${p.id}/agreement/download`)} />
              <p className="text-muted-foreground flex items-center gap-1.5 text-xs"><Lock className="size-3.5" />Private to this project. Files are never shared through a public link.</p>
            </CardContent>
          </Card>
          {p.delay?.enabled && (
            <Card>
              <CardHeader><CardTitle>Terms recorded from this agreement</CardTitle></CardHeader>
              <CardContent className="grid gap-2 text-sm">
                <div className="flex justify-between gap-3"><span className="text-muted-foreground">Contract value</span><Money value={p.contractValue} /></div>
                <div className="flex justify-between gap-3"><span className="text-muted-foreground">Completion deadline</span><span>{formatDate(p.delay.contractDeadline)}</span></div>
                <div className="flex justify-between gap-3"><span className="text-muted-foreground">Delay deduction</span><span className="text-right">{p.delay.notes ?? 'Configured'}</span></div>
                <Button variant="link" size="sm" className="h-auto justify-self-start px-0" onClick={() => setTab('payments')}>Review delay deduction<ArrowRight /></Button>
              </CardContent>
            </Card>
          )}
        </TabsContent>

        <TabsContent value="payments"><ProjectPayments pf={pf} payFor={payFor} setPayFor={setPayFor} /></TabsContent>
        <TabsContent value="expenses"><ProjectExpenses pf={pf} /></TabsContent>
        {hasCommission && <TabsContent value="commissions"><ProjectCommissions pf={pf} /></TabsContent>}

        <TabsContent value="activity" className="max-w-3xl">
          <Card>
            <CardHeader><CardTitle>Activity</CardTitle><CardDescription>Payments, costs, documents and decisions on this project, newest first</CardDescription></CardHeader>
            <CardContent>
              <ol className="relative grid gap-5 before:absolute before:inset-y-1 before:left-[5px] before:w-px before:bg-border">
                {activity.map((a) => (
                  <li key={a.id} className="relative grid grid-cols-[1fr_auto] gap-x-4 pl-6 text-sm">
                    <span className={cn('absolute top-1.5 left-0 size-[11px] rounded-full border-2 border-card', a.kind === 'payment' ? 'bg-success' : a.kind === 'writeoff' || a.kind === 'deduction' ? 'bg-destructive' : a.kind === 'expense' || a.kind === 'commission' ? 'bg-muted-foreground' : 'bg-primary')} />
                    <span className="min-w-0">{a.text}</span>
                    {a.amount != null ? <Money value={a.kind === 'payment' || a.kind === 'revenue' ? a.amount : -a.amount} signed={a.kind === 'payment'} tone={a.kind === 'payment' ? 'positive' : 'default'} className="text-right font-medium" /> : <span />}
                    <span className="text-muted-foreground col-span-2 text-xs">{formatDate(a.date)}</span>
                  </li>
                ))}
              </ol>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
      <div className="text-muted-foreground flex justify-end text-xs"><button className="hover:text-foreground" onClick={() => navigate('/projects')}>← All projects</button></div>
    </>
  )
}
