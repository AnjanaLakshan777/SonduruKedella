import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Bell, Calendar, ChevronDown, Moon, PanelLeft, RotateCcw, Search, Sun, CircleAlert, Clock, Truck } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { useData, useStore } from '@/store/store'
import { useAuth } from '@/store/auth'
import { emptyState } from '@/data/api-load'
import { addDays, formatDate, formatLongToday, TODAY } from '@/lib/dates'
import { formatLKR } from '@/lib/format'
import { useTheme } from './theme'
import { cn } from '@/lib/utils'

export interface Crumb { label: string; to?: string }

function useSearch(q: string) {
  const d = useData()
  return useMemo(() => {
    const t = q.trim().toLowerCase()
    if (t.length < 2) return []
    const out: { group: string; label: string; sub: string; to: string }[] = []
    for (const p of d.projects) if (`${p.project.name} ${p.project.code} ${p.client.name}`.toLowerCase().includes(t)) out.push({ group: 'Projects', label: p.project.name, sub: `${p.project.code} · ${p.client.name}`, to: `/projects/${p.project.id}` })
    for (const c of d.s.clients) if (`${c.name} ${c.city} ${c.phone}`.toLowerCase().includes(t)) out.push({ group: 'Clients', label: c.name, sub: c.city, to: `/clients/${c.id}` })
    for (const s of d.s.suppliers) if (`${s.name} ${s.supplies}`.toLowerCase().includes(t)) out.push({ group: 'Suppliers', label: s.name, sub: s.supplies, to: `/suppliers/${s.id}` })
    for (const v of d.invoices) if (v.invoice.invoiceNo.toLowerCase().includes(t)) out.push({ group: 'Transactions', label: `Invoice ${v.invoice.invoiceNo}`, sub: `${v.supplier.name} · ${formatLKR(v.total)}`, to: `/suppliers/${v.supplier.id}` })
    for (const l of d.expenseLines.filter((l) => l.source === 'Manual')) if (`${l.description} ${l.payee}`.toLowerCase().includes(t)) out.push({ group: 'Transactions', label: l.description, sub: `${formatDate(l.date)} · ${formatLKR(l.amount)}`, to: `/expenses?q=${encodeURIComponent(l.description)}` })
    for (const p of d.s.clientPayments) if ((p.reference ?? '').toLowerCase().includes(t)) {
      const i = d.installments.find((x) => x.inst.id === p.installmentId)!
      out.push({ group: 'Transactions', label: `Payment ${p.reference}`, sub: `${i.client.name} · ${formatLKR(p.amount)}`, to: `/projects/${i.project.id}?tab=payments` })
    }
    return out.slice(0, 12)
  }, [d, q])
}

export function Topbar({ crumbs, onToggleNav }: { crumbs: Crumb[]; onToggleNav: () => void }) {
  const d = useData()
  const load = useStore((s) => s.load)
  const { user, logout } = useAuth()
  const initials = (user?.displayName ?? '').split(' ').filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('')
  const roleLabel = user?.role === 'SUPER_ADMIN' ? 'Super Admin' : 'Admin'
  const { theme, setTheme } = useTheme()
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [open, setOpen] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const results = useSearch(q)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement)?.tagName
      if (e.key === '/' && tag !== 'INPUT' && tag !== 'TEXTAREA') { e.preventDefault(); inputRef.current?.focus() }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const overdue = d.installments.filter((i) => i.status === 'Overdue').sort((a, b) => b.daysOverdue - a.daysOverdue)
  const dueSoon = d.payablesDue.filter((p) => p.status !== 'Overdue' && p.dueDate <= addDays(TODAY, 3))
  const otPending = d.s.overtime.filter((o) => !o.approved)
  const count = overdue.length + dueSoon.length + (otPending.length ? 1 : 0)

  return (
    <header className="bg-background/95 supports-[backdrop-filter]:bg-background/80 sticky top-0 z-30 flex h-14 items-center gap-2 border-b px-3 backdrop-blur sm:px-4">
      <Button variant="ghost" size="icon" aria-label="Toggle navigation" onClick={onToggleNav}><PanelLeft /></Button>
      <nav aria-label="Breadcrumb" className="flex min-w-0 items-center gap-1.5 border-l pl-3 text-sm">
        {crumbs.map((c, i) => {
          const last = i === crumbs.length - 1
          return (
            <span key={i} className={cn('flex min-w-0 items-center gap-1.5', !last && 'hidden md:flex')}>
              {i > 0 && <span className="text-muted-foreground">/</span>}
              {last || !c.to ? <span className={cn('truncate', last && 'font-medium')} aria-current={last ? 'page' : undefined}>{c.label}</span> : <Link to={c.to} className="text-muted-foreground hover:text-foreground truncate">{c.label}</Link>}
            </span>
          )
        })}
      </nav>
      <div className="ml-auto flex items-center gap-2 sm:gap-3">
        <Popover open={open && results.length > 0} onOpenChange={setOpen}>
          <PopoverTrigger asChild>
            <label className="border-input dark:bg-input/30 bg-background focus-within:border-ring focus-within:ring-ring/50 text-muted-foreground hidden h-8 w-56 items-center gap-2 rounded-md border px-2.5 focus-within:ring-[3px] md:flex lg:w-72">
              <Search className="size-4 shrink-0" />
              <input
                ref={inputRef}
                value={q}
                onChange={(e) => { setQ(e.target.value); setOpen(true) }}
                onFocus={() => setOpen(true)}
                onKeyDown={(e) => { if (e.key === 'Enter' && results[0]) { navigate(results[0].to); setOpen(false); setQ('') } }}
                placeholder="Search projects, clients, suppliers…"
                aria-label="Search projects, clients, suppliers and transactions"
                className="text-foreground placeholder:text-muted-foreground w-full min-w-0 bg-transparent text-sm outline-none"
              />
              <kbd className="rounded border px-1.5 font-mono text-[11px]">/</kbd>
            </label>
          </PopoverTrigger>
          <PopoverContent align="end" className="w-[min(420px,calc(100vw-2rem))] p-1" onOpenAutoFocus={(e) => e.preventDefault()}>
            {['Projects', 'Clients', 'Suppliers', 'Transactions'].map((g) => {
              const rs = results.filter((r) => r.group === g)
              if (!rs.length) return null
              return (
                <div key={g} className="py-1">
                  <div className="text-muted-foreground px-2 py-1 text-xs font-medium">{g}</div>
                  {rs.map((r, i) => (
                    <button key={i} type="button" onClick={() => { navigate(r.to); setOpen(false); setQ('') }} className="hover:bg-accent grid w-full rounded-sm px-2 py-1.5 text-left text-sm">
                      <span className="truncate">{r.label}</span>
                      <span className="text-muted-foreground truncate text-xs">{r.sub}</span>
                    </button>
                  ))}
                </div>
              )
            })}
          </PopoverContent>
        </Popover>
        <Button variant="ghost" size="icon" className="md:hidden" aria-label="Search" onClick={() => toast('Search', { description: 'Use a wider screen for global search, or the search box on each list page.' })}><Search /></Button>
        <span className="text-muted-foreground hidden items-center gap-1.5 text-[13px] whitespace-nowrap xl:inline-flex"><Calendar className="size-3.5" />{formatLongToday()}</span>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" aria-label={`${count} notifications`} className="relative">
              <Bell className="size-[18px]" />
              {count > 0 && <span className="bg-destructive dark:bg-destructive/70 tabular absolute top-0.5 right-0 min-w-4 rounded-full px-1 text-[10px] leading-4 font-semibold text-white">{count}</span>}
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-[min(380px,calc(100vw-2rem))]">
            <DropdownMenuLabel>Needs attention</DropdownMenuLabel>
            {overdue.slice(0, 4).map((i) => (
              <DropdownMenuItem key={i.inst.id} onSelect={() => navigate(`/projects/${i.project.id}?tab=payments`)} className="items-start">
                <CircleAlert className="text-destructive! mt-0.5" />
                <span className="grid">
                  <span>{i.client.name} — {formatLKR(i.remaining)} overdue</span>
                  <span className="text-muted-foreground text-xs">{i.inst.description} · {i.daysOverdue} days past {formatDate(i.inst.dueDate)}</span>
                </span>
              </DropdownMenuItem>
            ))}
            {dueSoon.map((p) => (
              <DropdownMenuItem key={p.id} onSelect={() => navigate('/suppliers?tab=payables')} className="items-start">
                <Truck className="mt-0.5" />
                <span className="grid">
                  <span>{p.payee} — {formatLKR(p.amount)} due {formatDate(p.dueDate)}</span>
                  <span className="text-muted-foreground text-xs">{p.kind} {p.ref}</span>
                </span>
              </DropdownMenuItem>
            ))}
            {otPending.length > 0 && (
              <DropdownMenuItem onSelect={() => navigate('/labour/overtime')} className="items-start">
                <Clock className="mt-0.5" />
                <span className="grid"><span>{otPending.length} overtime entries awaiting approval</span><span className="text-muted-foreground text-xs">Recorded by the site supervisor</span></span>
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button type="button" className="hover:bg-accent focus-visible:ring-ring/50 flex items-center gap-2 rounded-md p-0.5 pr-1.5 text-left outline-none focus-visible:ring-[3px]" aria-label="Account menu">
              <span className="bg-primary-soft text-primary-soft-foreground grid size-8 place-items-center rounded-full text-xs font-semibold">{initials}</span>
              <span className="hidden lg:grid">
                <span className="text-[13px] leading-4 font-medium">{user?.displayName}</span>
                <span className="text-muted-foreground text-xs leading-4">{roleLabel}</span>
              </span>
              <ChevronDown className="text-muted-foreground size-3.5" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel className="grid"><span>{user?.displayName}</span><span className="text-muted-foreground text-xs font-normal">{user?.email}</span></DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem onSelect={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>{theme === 'dark' ? <Sun /> : <Moon />}{theme === 'dark' ? 'Light theme' : 'Dark theme'}</DropdownMenuItem>
            <DropdownMenuItem onSelect={() => navigate('/settings')}>Company settings</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onSelect={() => { load().then(() => toast.success('Data reloaded')).catch((e) => toast.error('Could not reload', { description: e.message })) }}><RotateCcw />Reload data</DropdownMenuItem>
            <DropdownMenuItem onSelect={() => { logout().finally(() => useStore.setState({ ...emptyState(), loaded: false, frozenWeeks: new Set() })) }}>Sign out</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}
