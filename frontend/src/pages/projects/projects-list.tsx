import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Ellipsis, FolderKanban, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { CellMain, Pagination, SortableHead, usePaged, useSort } from '@/components/app/data-table'
import { ALL, FilterBar, FilterSelect, SearchInput } from '@/components/app/filters'
import { EmptyState } from '@/components/app/empty-state'
import { useData } from '@/store/store'
import { formatDate, TODAY } from '@/lib/dates'
import { sum, type ProjectFinance } from '@/lib/finance'

// this year and next, from today
const YEAR = TODAY.slice(0, 4), NEXT = String(Number(YEAR) + 1)
const YEARS = [{ value: 'started-this', label: `Started in ${YEAR}` }, { value: 'due-this', label: `Due by 31 Dec ${YEAR}` }, { value: 'due-next', label: `Due in ${NEXT}` }]

export default function ProjectsList() {
  useCrumbs([{ label: 'Projects' }])
  const d = useData()
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [type, setType] = useState(ALL)
  const [status, setStatus] = useState(ALL)
  const [client, setClient] = useState(ALL)
  const [date, setDate] = useState(ALL)

  const filtered = useMemo(() => d.projects.filter((p) => {
    if (q && !`${p.project.name} ${p.project.code} ${p.client.name} ${p.project.location}`.toLowerCase().includes(q.toLowerCase())) return false
    if (type !== ALL && p.project.type !== type) return false
    if (status !== ALL && p.project.status !== status) return false
    if (client !== ALL && p.client.id !== client) return false
    if (date === 'started-this' && !p.project.startDate.startsWith(YEAR)) return false
    if (date === 'due-this' && p.project.expectedCompletion > `${YEAR}-12-31`) return false
    if (date === 'due-next' && !p.project.expectedCompletion.startsWith(NEXT)) return false
    return true
  }), [d, q, type, status, client, date])

  const { sorted, sort, toggle } = useSort<ProjectFinance>(filtered, { key: 'code', dir: 'desc' }, {
    code: (p) => p.project.code, name: (p) => p.project.name, contract: (p) => p.contractValue, received: (p) => p.received,
    costs: (p) => p.totalCosts, receivable: (p) => p.receivable, due: (p) => p.project.expectedCompletion,
  })
  const paged = usePaged(sorted, 10)
  const active = q !== '' || type !== ALL || status !== ALL || client !== ALL || date !== ALL

  return (
    <>
      <PageHeader
        title="Projects"
        description="Every design, construction and consultation job with its contract, money in, costs and what is still owed."
        actions={<Button asChild><Link to="/projects/new"><Plus />Create Project</Link></Button>}
      />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <FilterBar active={active} onClear={() => { setQ(''); setType(ALL); setStatus(ALL); setClient(ALL); setDate(ALL) }}>
          <SearchInput value={q} onChange={setQ} placeholder="Search name, ID, client…" />
          <FilterSelect label="Types" value={type} onChange={setType} options={['Design', 'Construction', 'Consultation']} />
          <FilterSelect label="Statuses" value={status} onChange={setStatus} options={d.s.settings.projectStatuses} />
          <FilterSelect label="Clients" value={client} onChange={setClient} options={d.s.clients.map((c) => ({ value: c.id, label: c.name }))} />
          <FilterSelect label="Dates" value={date} onChange={setDate} options={YEARS} />
        </FilterBar>
      </div>
      <Card className="gap-0 py-0">
        {sorted.length === 0 ? (
          <EmptyState icon={FolderKanban} title="No projects match these filters" description="Try a different status or clear the filters." action={<Button size="sm" variant="outline" onClick={() => { setQ(''); setType(ALL); setStatus(ALL); setClient(ALL); setDate(ALL) }}>Clear filters</Button>} />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <SortableHead label="Project" sortKey="name" sort={sort} onSort={toggle} className="pl-6" />
                  <TableHead className="hidden lg:table-cell">Client</TableHead>
                  <TableHead>Type</TableHead>
                  <SortableHead label="Contract (LKR)" sortKey="contract" sort={sort} onSort={toggle} align="right" />
                  <SortableHead label="Received (LKR)" sortKey="received" sort={sort} onSort={toggle} align="right" className="hidden md:table-cell" />
                  <SortableHead label="Project Costs (LKR)" sortKey="costs" sort={sort} onSort={toggle} align="right" className="hidden xl:table-cell" />
                  <SortableHead label="Outstanding (LKR)" sortKey="receivable" sort={sort} onSort={toggle} align="right" />
                  <TableHead>Status</TableHead>
                  <SortableHead label="Expected Completion" sortKey="due" sort={sort} onSort={toggle} className="hidden lg:table-cell" />
                  <TableHead className="w-12 pr-4"><span className="sr-only">Actions</span></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {paged.rows.map((p) => (
                  <TableRow key={p.project.id} className="cursor-pointer" onClick={() => navigate(`/projects/${p.project.id}`)}>
                    <TableCell className="max-w-72 pl-6"><CellMain title={p.project.name} code={p.project.code} /></TableCell>
                    <TableCell className="hidden max-w-48 truncate lg:table-cell">{p.client.name}</TableCell>
                    <TableCell>{p.project.type}</TableCell>
                    <TableCell className="text-right"><Money value={p.contractValue} currency={false} decimals={0} /></TableCell>
                    <TableCell className="hidden text-right md:table-cell"><Money value={p.received} currency={false} decimals={0} /></TableCell>
                    <TableCell className="hidden text-right xl:table-cell"><Money value={p.totalCosts} currency={false} decimals={0} /></TableCell>
                    <TableCell className="text-right font-medium">
                      {p.receivable > 0 ? <Money value={p.receivable} currency={false} decimals={0} tone={p.overdue > 0 ? 'negative' : 'default'} /> : <span className="text-muted-foreground">—</span>}
                      {p.overdue > 0 && <div className="text-destructive-soft-foreground text-xs font-normal">overdue</div>}
                    </TableCell>
                    <TableCell><StatusBadge status={p.project.status} /></TableCell>
                    <TableCell className="hidden lg:table-cell">{formatDate(p.project.expectedCompletion)}</TableCell>
                    <TableCell className="pr-4" onClick={(e) => e.stopPropagation()}>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${p.project.name}`}><Ellipsis /></Button></DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onSelect={() => navigate(`/projects/${p.project.id}`)}>Open project</DropdownMenuItem>
                          <DropdownMenuItem onSelect={() => navigate(`/projects/${p.project.id}?tab=payments`)}>Payments & receivables</DropdownMenuItem>
                          <DropdownMenuItem onSelect={() => navigate(`/projects/${p.project.id}?tab=expenses`)}>Project expenses</DropdownMenuItem>
                          <DropdownMenuItem onSelect={() => navigate(`/projects/${p.project.id}?tab=agreement`)}>Agreement PDF</DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
              <TableFooter>
                <TableRow>
                  <TableCell className="pl-6">{sorted.length} project{sorted.length === 1 ? '' : 's'}</TableCell>
                  <TableCell className="hidden lg:table-cell" />
                  <TableCell />
                  <TableCell className="text-right"><Money value={sum(sorted, (p) => p.contractValue)} currency={false} decimals={0} /></TableCell>
                  <TableCell className="hidden text-right md:table-cell"><Money value={sum(sorted, (p) => p.received)} currency={false} decimals={0} /></TableCell>
                  <TableCell className="hidden text-right xl:table-cell"><Money value={sum(sorted, (p) => p.totalCosts)} currency={false} decimals={0} /></TableCell>
                  <TableCell className="text-right"><Money value={sum(sorted, (p) => p.receivable)} currency={false} decimals={0} /></TableCell>
                  <TableCell colSpan={3} />
                </TableRow>
              </TableFooter>
            </Table>
            <div className="border-t px-6 py-3"><Pagination {...paged} /></div>
          </>
        )}
      </Card>
    </>
  )
}
