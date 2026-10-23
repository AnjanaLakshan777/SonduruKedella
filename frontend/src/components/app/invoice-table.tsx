import { Fragment, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronDown, ChevronRight, HandCoins, Paperclip } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useStore } from '@/store/store'
import { sum, type InvoiceView } from '@/lib/finance'
import { formatDate } from '@/lib/dates'
import { formatQty } from '@/lib/format'
import { Money } from './money'
import { StatusBadge } from './status-badge'
import { CellMain, Mono, Pagination, SortableHead, usePaged, useSort } from './data-table'
import { PaymentDialog } from './payment-dialog'

export function InvoiceTable({ rows, showSupplier = true, pageSize = 12 }: { rows: InvoiceView[]; showSupplier?: boolean; pageSize?: number }) {
  const navigate = useNavigate()
  const recordSupplierPayment = useStore((s) => s.recordSupplierPayment)
  const [payFor, setPayFor] = useState<InvoiceView | undefined>()
  const [openRow, setOpenRow] = useState<string | null>(null)
  const { sorted, sort, toggle } = useSort(rows, { key: 'due', dir: 'desc' }, { date: (v) => v.invoice.date, due: (v) => v.invoice.dueDate, total: (v) => v.total, remaining: (v) => v.remaining, supplier: (v) => v.supplier.name })
  const paged = usePaged(sorted, pageSize)
  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-10 pl-4"><span className="sr-only">Expand</span></TableHead>
            <TableHead>Invoice</TableHead>
            {showSupplier && <SortableHead label="Supplier" sortKey="supplier" sort={sort} onSort={toggle} className="hidden md:table-cell" />}
            <TableHead className="hidden lg:table-cell">Allocated to</TableHead>
            <SortableHead label="Total (LKR)" sortKey="total" sort={sort} onSort={toggle} align="right" />
            <TableHead className="hidden text-right sm:table-cell">Paid (LKR)</TableHead>
            <SortableHead label="Outstanding (LKR)" sortKey="remaining" sort={sort} onSort={toggle} align="right" />
            <SortableHead label="Due Date" sortKey="due" sort={sort} onSort={toggle} className="hidden sm:table-cell" />
            <TableHead>Status</TableHead>
            <TableHead className="pr-4"><span className="sr-only">Actions</span></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {paged.rows.map((v) => {
            const open = openRow === v.invoice.id
            return (
              <Fragment key={v.invoice.id}>
                <TableRow>
                  <TableCell className="pl-4"><Button variant="ghost" size="icon" className="size-7" aria-expanded={open} aria-label="Show items" onClick={() => setOpenRow(open ? null : v.invoice.id)}>{open ? <ChevronDown /> : <ChevronRight />}</Button></TableCell>
                  <TableCell><CellMain title={<span className="flex items-center gap-1.5 font-mono text-[13px]">{v.invoice.invoiceNo}{v.invoice.attachment && <Paperclip className="text-muted-foreground size-3.5" aria-label="Invoice attached" />}</span>} sub={`${formatDate(v.invoice.date)} · ${v.invoice.lines.length} item${v.invoice.lines.length === 1 ? '' : 's'}`} /></TableCell>
                  {showSupplier && <TableCell className="hidden max-w-52 md:table-cell"><button className="truncate text-left underline-offset-4 hover:underline" onClick={() => navigate(`/suppliers/${v.supplier.id}`)}>{v.supplier.name}</button></TableCell>}
                  <TableCell className="hidden lg:table-cell">{v.project ? <Mono className="text-foreground">{v.project.code}</Mono> : <Badge variant="outline">Stock</Badge>}</TableCell>
                  <TableCell className="text-right"><Money value={v.total} currency={false} /></TableCell>
                  <TableCell className="hidden text-right sm:table-cell">{v.paid ? <Money value={v.paid} currency={false} /> : <span className="text-muted-foreground">—</span>}</TableCell>
                  <TableCell className="text-right font-medium">{v.remaining > 0 ? <Money value={v.remaining} currency={false} /> : <span className="text-muted-foreground font-normal">—</span>}</TableCell>
                  <TableCell className="hidden sm:table-cell">{formatDate(v.invoice.dueDate)}{v.remaining > 0 && v.daysToDue >= 0 && v.daysToDue <= 7 && <div className="text-warning text-xs">in {v.daysToDue} day{v.daysToDue === 1 ? '' : 's'}</div>}</TableCell>
                  <TableCell><StatusBadge status={v.status} /></TableCell>
                  <TableCell className="pr-4"><Button size="sm" variant="outline" disabled={v.remaining <= 0} onClick={() => setPayFor(v)}><HandCoins />Pay</Button></TableCell>
                </TableRow>
                {open && (
                  <TableRow className="bg-muted/30 hover:bg-muted/30">
                    <TableCell />
                    <TableCell colSpan={showSupplier ? 9 : 8} className="py-3 whitespace-normal">
                      <div className="grid gap-1.5 text-sm">
                        {v.invoice.lines.map((l) => (
                          <div key={l.id} className="flex flex-wrap justify-between gap-3"><span>{l.description} — {formatQty(l.qty, l.unit)} × <Money value={l.unitPrice} decimals={2} /></span><Money value={l.qty * l.unitPrice} className="font-medium" /></div>
                        ))}
                        {v.payments.map((p) => <div key={p.id} className="text-muted-foreground flex justify-between gap-3 text-xs"><span>Paid {formatDate(p.date)} · {p.method}{p.reference ? ` · ${p.reference}` : ''}</span><Money value={-p.amount} tone="muted" /></div>)}
                        {!v.project && <p className="text-muted-foreground text-xs">Received into stock. Project costs are recorded only when materials are issued to a project.</p>}
                      </div>
                    </TableCell>
                  </TableRow>
                )}
              </Fragment>
            )
          })}
        </TableBody>
        <TableFooter>
          <TableRow>
            <TableCell /><TableCell>{rows.length} invoices</TableCell>
            {showSupplier && <TableCell className="hidden md:table-cell" />}
            <TableCell className="hidden lg:table-cell" />
            <TableCell className="text-right"><Money value={sum(rows, (v) => v.total)} currency={false} /></TableCell>
            <TableCell className="hidden text-right sm:table-cell"><Money value={sum(rows, (v) => v.paid)} currency={false} /></TableCell>
            <TableCell className="text-right"><Money value={sum(rows, (v) => v.remaining)} currency={false} /></TableCell>
            <TableCell colSpan={3} />
          </TableRow>
        </TableFooter>
      </Table>
      {sorted.length > pageSize && <div className="border-t px-6 py-3"><Pagination {...paged} /></div>}
      <PaymentDialog
        open={!!payFor} onOpenChange={(o) => !o && setPayFor(undefined)} direction="out" successTitle="Supplier payment recorded"
        title={`Pay ${payFor?.supplier.name ?? ''}`} description={payFor ? `Invoice ${payFor.invoice.invoiceNo} · due ${formatDate(payFor.invoice.dueDate)}` : undefined}
        context={payFor ? [{ label: 'Invoice total', value: payFor.total }, { kind: 'less', label: 'Already paid', value: payFor.paid }, { kind: 'total', label: 'Outstanding payable', value: payFor.remaining }] : undefined}
        outstanding={payFor?.remaining ?? 0}
        onSubmit={(p) => payFor && recordSupplierPayment({ invoiceId: payFor.invoice.id, date: p.date, amount: p.amount, method: p.method, reference: p.reference })}
      />
    </>
  )
}
