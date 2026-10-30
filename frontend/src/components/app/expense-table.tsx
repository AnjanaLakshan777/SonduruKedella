import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Ellipsis, ExternalLink, HandCoins, Paperclip, Pencil } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { useData, useStore } from '@/store/store'
import { sum, type ExpenseLine } from '@/lib/finance'
import { formatDate } from '@/lib/dates'
import { formatLKR } from '@/lib/format'
import { Money } from './money'
import { StatusBadge } from './status-badge'
import { CellMain, Mono, Pagination, SortableHead, usePaged, useSort } from './data-table'
import { PaymentDialog } from './payment-dialog'
import { RecordExpenseDialog } from './record-expense-dialog'
import { FigureList } from './figure-list'
import { EmptyState } from './empty-state'

export function ExpenseTable({ lines, showProject = true, pageSize = 12, empty }: { lines: ExpenseLine[]; showProject?: boolean; pageSize?: number; empty?: React.ReactNode }) {
  const d = useData()
  const navigate = useNavigate()
  const recordExpensePayment = useStore((s) => s.recordExpensePayment)
  const [payFor, setPayFor] = useState<ExpenseLine | undefined>()
  const [editFor, setEditFor] = useState<ExpenseLine | undefined>()
  const [view, setView] = useState<ExpenseLine | undefined>()
  const { sorted, sort, toggle } = useSort(lines, { key: 'date', dir: 'desc' }, { date: (l) => l.date, amount: (l) => l.amount, remaining: (l) => l.remaining, category: (l) => l.category })
  const paged = usePaged(sorted, pageSize)

  function openSource(l: ExpenseLine) {
    if (l.source === 'Supplier invoice') { const inv = d.invoices.find((v) => v.invoice.id === l.refId); if (inv) navigate(`/suppliers/${inv.supplier.id}`) }
    else if (l.source === 'Stock issue') navigate('/inventory')
    else if (l.source === 'Payroll') navigate('/labour/payroll')
    else if (l.source === 'Commission') navigate('/commissions')
  }

  if (!lines.length) return <>{empty ?? <EmptyState title="No expenses recorded" />}</>
  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <SortableHead label="Date" sortKey="date" sort={sort} onSort={toggle} className="pl-6" />
            <TableHead>Description</TableHead>
            {showProject && <TableHead className="hidden lg:table-cell">Project / Company</TableHead>}
            <SortableHead label="Category" sortKey="category" sort={sort} onSort={toggle} className="hidden md:table-cell" />
            <SortableHead label="Amount (LKR)" sortKey="amount" sort={sort} onSort={toggle} align="right" />
            <SortableHead label="Unpaid (LKR)" sortKey="remaining" sort={sort} onSort={toggle} align="right" className="hidden sm:table-cell" />
            <TableHead>Payment</TableHead>
            <TableHead className="w-12 pr-4"><span className="sr-only">Actions</span></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {paged.rows.map((l) => {
            const p = l.projectId ? d.projectById.get(l.projectId) : undefined
            return (
              <TableRow key={l.id} className="cursor-pointer" onClick={() => setView(l)}>
                <TableCell className="pl-6">{formatDate(l.date)}</TableCell>
                <TableCell className="max-w-80">
                  <CellMain title={<span className="flex items-center gap-1.5">{l.description}{l.expense?.attachment && <Paperclip className="text-muted-foreground size-3.5 shrink-0" aria-label="Has attachment" />}</span>} sub={<>{l.payee}{l.source !== 'Manual' && <> · <span className="text-primary-text">{l.source === 'Stock issue' ? 'From stock' : `From ${l.source.toLowerCase()}`}</span></>}</>} />
                </TableCell>
                {showProject && <TableCell className="hidden lg:table-cell">{p ? <CellMain title={<Mono className="text-foreground">{p.code}</Mono>} sub={p.name} /> : <Badge variant="outline">Company-level</Badge>}</TableCell>}
                <TableCell className="hidden md:table-cell">{l.category}{l.category === 'Electricity' && <span className="text-muted-foreground block text-xs">{l.group === 'Design' ? 'Design work' : l.group === 'Company' ? 'Office' : 'Site'}</span>}</TableCell>
                <TableCell className="text-right font-medium"><Money value={l.amount} currency={false} /></TableCell>
                <TableCell className="hidden text-right sm:table-cell">{l.remaining > 0 ? <Money value={l.remaining} currency={false} /> : <span className="text-muted-foreground">—</span>}</TableCell>
                <TableCell><StatusBadge status={l.status} /></TableCell>
                <TableCell className="pr-4" onClick={(e) => e.stopPropagation()}>
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-8" aria-label="Expense actions"><Ellipsis /></Button></DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onSelect={() => setView(l)}>View details</DropdownMenuItem>
                      {l.source === 'Manual' ? (
                        <>
                          <DropdownMenuItem disabled={l.remaining <= 0} onSelect={() => setPayFor(l)}><HandCoins />Record payment</DropdownMenuItem>
                          <DropdownMenuItem onSelect={() => setEditFor(l)}><Pencil />Edit</DropdownMenuItem>
                        </>
                      ) : (
                        <DropdownMenuItem onSelect={() => openSource(l)}><ExternalLink />Open in {l.source === 'Stock issue' ? 'Inventory' : l.source === 'Payroll' ? 'Payroll' : l.source === 'Commission' ? 'Commissions' : 'Suppliers'}</DropdownMenuItem>
                      )}
                    </DropdownMenuContent>
                  </DropdownMenu>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
        <TableFooter>
          <TableRow>
            <TableCell className="pl-6" colSpan={showProject ? 2 : 2}>{lines.length} record{lines.length === 1 ? '' : 's'}</TableCell>
            {showProject && <TableCell className="hidden lg:table-cell" />}
            <TableCell className="hidden md:table-cell" />
            <TableCell className="text-right"><Money value={sum(lines, (l) => l.amount)} currency={false} /></TableCell>
            <TableCell className="hidden text-right sm:table-cell"><Money value={sum(lines, (l) => l.remaining)} currency={false} /></TableCell>
            <TableCell colSpan={2} />
          </TableRow>
        </TableFooter>
      </Table>
      {sorted.length > pageSize && <div className="border-t px-6 py-3"><Pagination {...paged} /></div>}

      <PaymentDialog
        open={!!payFor} onOpenChange={(o) => !o && setPayFor(undefined)} direction="out" verb="Record Payment" successTitle="Expense payment recorded"
        title={`Pay ${payFor?.payee ?? ''}`} description={payFor?.description}
        context={payFor ? [{ label: 'Expense amount', value: payFor.amount }, { kind: 'less', label: 'Already paid', value: payFor.paid }, { kind: 'total', label: 'Unpaid', value: payFor.remaining }] : undefined}
        outstanding={payFor?.remaining ?? 0}
        onSubmit={(p) => (payFor ? recordExpensePayment({ expenseId: payFor.refId, ...p }) : undefined)}
      />
      <RecordExpenseDialog open={!!editFor} onOpenChange={(o) => !o && setEditFor(undefined)} edit={editFor?.expense} />
      <Sheet open={!!view} onOpenChange={(o) => !o && setView(undefined)}>
        <SheetContent className="overflow-y-auto">
          {view && (
            <>
              <SheetHeader>
                <SheetTitle>{view.description}</SheetTitle>
                <SheetDescription>{view.category} · {view.scope === 'Company' ? 'Company-level expense' : `${d.projectById.get(view.projectId!)?.code} · ${d.projectById.get(view.projectId!)?.name}`}</SheetDescription>
              </SheetHeader>
              <div className="grid gap-5 px-6 pb-6">
                <div className="flex flex-wrap gap-2"><StatusBadge status={view.status} /><Badge variant="outline">{view.source}</Badge><Badge variant="outline">{view.group}</Badge></div>
                <FigureList rows={[
                  { label: 'Expense recorded', value: view.amount, note: formatDate(view.date) },
                  { kind: 'less', label: 'Payments made', value: view.paid },
                  { kind: 'highlight', label: view.status === 'From Stock' ? 'Paid through the stock purchase' : 'Unpaid', value: view.remaining },
                ]} />
                <dl className="grid gap-2 text-sm">
                  {[['Paid to', view.payee], ['Due date', view.dueDate ? formatDate(view.dueDate) : '—'], ['Invoice / bill', view.expense?.invoiceNo ?? '—'],
                    ...Object.entries(view.expense?.details ?? {}).map(([k, v]) => [k, String(v)]),
                    ['Attachment', view.expense?.attachment ? view.expense.attachment.fileName : '—'], ['Notes', view.expense?.notes ?? '—']].map(([k, v]) => (
                    <div key={k} className="grid grid-cols-[120px_1fr] gap-3"><dt className="text-muted-foreground">{k}</dt><dd className="min-w-0 break-words">{v}</dd></div>
                  ))}
                </dl>
                {view.expense && (
                  <div className="grid gap-2">
                    <div className="text-sm font-medium">Payments</div>
                    {d.s.expensePayments.filter((p) => p.expenseId === view.refId).map((p) => (
                      <div key={p.id} className="flex justify-between border-b pb-2 text-sm last:border-0"><span>{formatDate(p.date)} · {p.method}</span><Money value={p.amount} /></div>
                    ))}
                    {!d.s.expensePayments.some((p) => p.expenseId === view.refId) && <p className="text-muted-foreground text-sm">No payments yet.</p>}
                  </div>
                )}
                <div className="flex flex-wrap gap-2">
                  {view.source === 'Manual' ? (
                    <>
                      <Button disabled={view.remaining <= 0} onClick={() => { setPayFor(view); setView(undefined) }}><HandCoins />Record payment</Button>
                      <Button variant="outline" onClick={() => { setEditFor(view); setView(undefined) }}><Pencil />Edit</Button>
                    </>
                  ) : <Button variant="outline" onClick={() => openSource(view)}><ExternalLink />Open source record</Button>}
                </div>
                {view.source !== 'Manual' && <p className="text-muted-foreground text-xs">This cost is generated from {view.source.toLowerCase()} records{view.source === 'Stock issue' ? ` at ${formatLKR(view.amount)} issue cost — the original stock purchase is not counted as a project expense, so nothing is counted twice` : ''}.</p>}
              </div>
            </>
          )}
        </SheetContent>
      </Sheet>
    </>
  )
}
