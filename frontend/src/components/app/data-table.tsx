import { useMemo, useState } from 'react'
import { ArrowUpDown, ChevronDown, ChevronLeft, ChevronRight, ChevronUp } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { TableHead } from '@/components/ui/table'

export type SortState = { key: string; dir: 'asc' | 'desc' }

export function useSort<T>(rows: T[], initial: SortState, accessors: Record<string, (r: T) => string | number>) {
  const [sort, setSort] = useState<SortState>(initial)
  const sorted = useMemo(() => {
    const f = accessors[sort.key]
    if (!f) return rows
    return rows.slice().sort((a, b) => {
      const av = f(a), bv = f(b)
      const c = typeof av === 'number' && typeof bv === 'number' ? av - bv : String(av).localeCompare(String(bv))
      return sort.dir === 'asc' ? c : -c
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rows, sort])
  const toggle = (key: string) => setSort((s) => ({ key, dir: s.key === key && s.dir === 'desc' ? 'asc' : s.key === key ? 'desc' : 'asc' }))
  return { sorted, sort, toggle }
}

export function SortableHead({ label, sortKey, sort, onSort, align, className }: { label: React.ReactNode; sortKey: string; sort: SortState; onSort: (k: string) => void; align?: 'right'; className?: string }) {
  const active = sort.key === sortKey
  const Icon = active ? (sort.dir === 'asc' ? ChevronUp : ChevronDown) : ArrowUpDown
  return (
    <TableHead className={cn(align === 'right' && 'text-right', className)} aria-sort={active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : undefined}>
      <button
        type="button"
        onClick={() => onSort(sortKey)}
        className={cn('hover:text-foreground focus-visible:ring-ring/50 -mx-1.5 inline-flex items-center gap-1.5 rounded-sm px-1.5 py-1 outline-none focus-visible:ring-[3px]', active && 'text-foreground', align === 'right' && 'flex-row-reverse')}
      >
        {label}
        <Icon className="size-3.5" />
      </button>
    </TableHead>
  )
}

export function usePaged<T>(rows: T[], pageSize = 10) {
  const [page, setPage] = useState(1)
  const pages = Math.max(1, Math.ceil(rows.length / pageSize))
  const p = Math.min(page, pages)
  return { page: p, setPage, pageSize, total: rows.length, rows: rows.slice((p - 1) * pageSize, p * pageSize) }
}

export function Pagination({ page, pageSize, total, setPage, className }: { page: number; pageSize: number; total: number; setPage: (p: number) => void; className?: string }) {
  const from = total ? (page - 1) * pageSize + 1 : 0
  const to = Math.min(total, page * pageSize)
  return (
    <div className={cn('text-muted-foreground flex items-center justify-between gap-3 text-[13px]', className)}>
      <span>Showing {from}–{to} of {total}</span>
      <div className="flex gap-2">
        <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage(page - 1)}><ChevronLeft />Previous</Button>
        <Button variant="outline" size="sm" disabled={to >= total} onClick={() => setPage(page + 1)}>Next<ChevronRight /></Button>
      </div>
    </div>
  )
}

/** Primary cell: name over a muted sub-line. */
export function CellMain({ title, sub, mono }: { title: React.ReactNode; sub?: React.ReactNode; mono?: boolean }) {
  return (
    <span className="grid min-w-0">
      <span className="truncate font-medium">{title}</span>
      {sub && <span className={cn('text-muted-foreground truncate text-xs', mono && 'font-mono font-medium')}>{sub}</span>}
    </span>
  )
}

export function Mono({ children, className }: { children: React.ReactNode; className?: string }) {
  return <span className={cn('text-muted-foreground font-mono text-xs font-medium', className)}>{children}</span>
}
