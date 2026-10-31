import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Ellipsis, FilePenLine, HandCoins } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { useStore } from '@/store/store'
import { sum, type InstallmentView } from '@/lib/finance'
import { formatDate } from '@/lib/dates'
import { formatLKR } from '@/lib/format'
import { Money } from './money'
import { StatusBadge } from './status-badge'
import { CellMain, Pagination, SortableHead, usePaged, useSort } from './data-table'
import { PaymentDialog } from './payment-dialog'
import { WriteOffDialog } from './write-off-dialog'

/** Client receivables: original amount, received, remaining, billing date, days overdue (counted from the billing day), status. */
export function ReceivablesTable({ rows, showClient = true, pageSize = 12 }: { rows: InstallmentView[]; showClient?: boolean; pageSize?: number }) {
  const navigate = useNavigate()
  const recordClientPayment = useStore((s) => s.recordClientPayment)
  const [payFor, setPayFor] = useState<InstallmentView | undefined>()
  const [woFor, setWoFor] = useState<InstallmentView | undefined>()
  const { sorted, sort, toggle } = useSort(rows, { key: 'days', dir: 'desc' }, {
    client: (i) => i.client.name, due: (i) => i.inst.billedOn ?? i.inst.dueDate, remaining: (i) => i.remaining, days: (i) => i.daysOverdue * 1e6 - i.daysToDue, original: (i) => i.net,
  })
  const paged = usePaged(sorted, pageSize)
  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            {showClient && <SortableHead label="Client" sortKey="client" sort={sort} onSort={toggle} className="pl-6" />}
            <TableHead className={showClient ? '' : 'pl-6'}>Related project</TableHead>
            <SortableHead label="Amount Due (LKR)" sortKey="original" sort={sort} onSort={toggle} align="right" className="hidden md:table-cell" />
            <TableHead className="hidden text-right md:table-cell">Received (LKR)</TableHead>
            <SortableHead label="Remaining (LKR)" sortKey="remaining" sort={sort} onSort={toggle} align="right" />
            <SortableHead label="Billed On" sortKey="due" sort={sort} onSort={toggle} />
            <SortableHead label="Days Overdue" sortKey="days" sort={sort} onSort={toggle} align="right" className="hidden sm:table-cell" />
            <TableHead>Status</TableHead>
            <TableHead className="w-12 pr-4"><span className="sr-only">Actions</span></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {paged.rows.map((i) => (
            <TableRow key={i.inst.id} className="cursor-pointer" onClick={() => navigate(`/projects/${i.project.id}?tab=payments`)}>
              {showClient && <TableCell className="max-w-52 pl-6"><CellMain title={i.client.name} sub={i.client.phone} /></TableCell>}
              <TableCell className={showClient ? 'max-w-64' : 'max-w-64 pl-6'}><CellMain title={i.inst.description} code={i.project.code} sub={i.project.name} /></TableCell>
              <TableCell className="hidden text-right md:table-cell"><Money value={i.net} currency={false} />{i.deduction > 0 && <div className="text-muted-foreground text-xs">after {formatLKR(i.deduction, { decimals: 0 })} deduction</div>}</TableCell>
              <TableCell className="hidden text-right md:table-cell">{i.paid ? <Money value={i.paid} currency={false} /> : <span className="text-muted-foreground">—</span>}{i.writtenOff > 0 && <div className="text-muted-foreground text-xs">{formatLKR(i.writtenOff, { decimals: 0 })} written off</div>}</TableCell>
              <TableCell className="text-right font-medium">{i.inst.billed ? <Money value={i.remaining} currency={false} /> : <span className="text-muted-foreground font-normal">not billed</span>}</TableCell>
              <TableCell>{formatDate(i.inst.billedOn ?? i.inst.dueDate)}</TableCell>
              <TableCell className="tabular hidden text-right sm:table-cell">{i.daysOverdue ? <span className="text-destructive-soft-foreground font-medium">{i.daysOverdue}</span> : <span className="text-muted-foreground">—</span>}</TableCell>
              <TableCell><StatusBadge status={i.status} title={i.status === 'Overdue' && i.paid > 0 ? 'Partially paid' : undefined} /></TableCell>
              <TableCell className="pr-4" onClick={(e) => e.stopPropagation()}>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-8" aria-label="Receivable actions"><Ellipsis /></Button></DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem disabled={!i.inst.billed || i.remaining <= 0} onSelect={() => setPayFor(i)}><HandCoins />Record payment</DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => navigate(`/projects/${i.project.id}?tab=payments`)}>Open project schedule</DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem variant="destructive" disabled={!i.inst.billed || i.remaining <= 0} onSelect={() => setWoFor(i)}><FilePenLine />Write off as bad debt…</DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
        <TableFooter>
          <TableRow>
            <TableCell className="pl-6" colSpan={showClient ? 2 : 1}>{rows.length} item{rows.length === 1 ? '' : 's'}</TableCell>
            <TableCell className="hidden text-right md:table-cell"><Money value={sum(rows, (i) => i.net)} currency={false} /></TableCell>
            <TableCell className="hidden text-right md:table-cell"><Money value={sum(rows, (i) => i.paid)} currency={false} /></TableCell>
            <TableCell className="text-right"><Money value={sum(rows.filter((i) => i.inst.billed), (i) => i.remaining)} currency={false} /></TableCell>
            <TableCell colSpan={4} />
          </TableRow>
        </TableFooter>
      </Table>
      {sorted.length > pageSize && <div className="border-t px-6 py-3"><Pagination {...paged} /></div>}
      <PaymentDialog
        open={!!payFor} onOpenChange={(o) => !o && setPayFor(undefined)}
        title={`Record payment — ${payFor?.client.name ?? ''}`} description={payFor ? `${payFor.project.code} · ${payFor.inst.description}` : undefined}
        context={payFor ? [{ label: 'Amount due', value: payFor.net }, { kind: 'less', label: 'Already received', value: payFor.paid }, ...(payFor.writtenOff ? [{ kind: 'less' as const, label: 'Written off', value: payFor.writtenOff }] : []), { kind: 'total', label: 'Outstanding', value: payFor.remaining }] : undefined}
        outstanding={payFor?.remaining ?? 0}
        onSubmit={(p) => (payFor ? recordClientPayment({ installmentId: payFor.inst.id, ...p }) : undefined)}
      />
      <WriteOffDialog view={woFor} open={!!woFor} onOpenChange={(o) => !o && setWoFor(undefined)} />
    </>
  )
}

export function ageingBuckets(rows: InstallmentView[]) {
  const b = [
    { label: '0–30 days', test: (i: InstallmentView) => i.status === 'Overdue' && i.daysOverdue <= 30 },
    { label: '31–60 days', test: (i: InstallmentView) => i.daysOverdue > 30 && i.daysOverdue <= 60 },
    { label: '61–90 days', test: (i: InstallmentView) => i.daysOverdue > 60 && i.daysOverdue <= 90 },
    { label: 'Over 90 days', test: (i: InstallmentView) => i.daysOverdue > 90 },
  ]
  const open = rows.filter((i) => i.inst.billed && i.remaining > 0)
  return b.map((x) => ({ label: x.label, amount: sum(open.filter(x.test), (i) => i.remaining), count: open.filter(x.test).length }))
}
