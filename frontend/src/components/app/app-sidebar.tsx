import { NavLink, useLocation } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import logo from '@/assets/sonduru-kedella-logo.png'
import mark from '@/assets/sonduru-kedella-roof-mark.png'
import { NAV, SETTINGS_ITEM, USERS_ITEM, type NavItem } from './nav'
import { useAuth } from '@/store/auth'

function isActive(pathname: string, to: string) {
  return to === '/' ? pathname === '/' : pathname === to || pathname.startsWith(to + '/')
}

export function AppSidebar({ collapsed, counts, onNavigate }: { collapsed?: boolean; counts?: Record<string, { n: number; alert?: boolean }>; onNavigate?: () => void }) {
  const { pathname } = useLocation()
  const isSuperAdmin = useAuth((s) => s.user?.role === 'SUPER_ADMIN')
  const item = (it: NavItem) => {
    const on = isActive(pathname, it.to)
    const c = counts?.[it.to]
    const link = (
      <NavLink
        to={it.to}
        onClick={onNavigate}
        aria-current={on ? 'page' : undefined}
        className={cn(
          'relative flex h-8 items-center gap-2 overflow-hidden rounded-md px-2 text-sm whitespace-nowrap outline-none',
          'text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus-visible:ring-sidebar-ring/50 focus-visible:ring-[3px]',
          on && 'bg-sidebar-accent text-sidebar-accent-foreground font-medium before:bg-sidebar-primary before:absolute before:inset-y-1.5 before:left-0 before:w-[3px] before:rounded-full',
          collapsed && 'justify-center px-0',
        )}
      >
        <it.icon className={cn('size-4 shrink-0', on && 'text-primary-text')} />
        {!collapsed && <span className="flex-1 truncate">{it.label}</span>}
        {!collapsed && c && c.n > 0 && (
          <span className={cn('tabular rounded-sm px-1.5 text-xs', c.alert ? 'bg-destructive-soft text-destructive-soft-foreground' : 'bg-secondary text-secondary-foreground')}>{c.n}</span>
        )}
      </NavLink>
    )
    return (
      <li key={it.to}>
        {collapsed ? (
          <Tooltip>
            <TooltipTrigger asChild>{link}</TooltipTrigger>
            <TooltipContent side="right">{it.label}{c && c.n > 0 ? ` · ${c.n}` : ''}</TooltipContent>
          </Tooltip>
        ) : link}
      </li>
    )
  }
  return (
    <aside aria-label="Main navigation" className={cn('bg-sidebar text-sidebar-foreground border-sidebar-border flex h-full flex-col border-r', collapsed ? 'w-12' : 'w-64')}>
      <div className={cn('border-sidebar-border flex h-[88px] shrink-0 items-center justify-center border-b', collapsed ? 'px-1' : 'px-4')}>
        <NavLink to="/" onClick={onNavigate} className="focus-visible:ring-sidebar-ring/50 rounded-md outline-none focus-visible:ring-[3px]" aria-label="Sonduru Kedella — Dashboard">
          {collapsed ? <img src={mark} alt="" className="size-10" /> : <img src={logo} alt="Sonduru Kedella — Home Design & Construction" className="h-16 w-auto" />}
        </NavLink>
      </div>
      <nav className="flex-1 overflow-y-auto p-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
        {NAV.map((g, i) => (
          <div key={i} className={cn('py-2', collapsed && i > 0 && 'border-sidebar-border border-t py-1')}>
            {g.label && !collapsed && <div className="text-sidebar-foreground/70 flex h-8 items-center px-2 text-xs font-medium">{g.label}</div>}
            <ul className="grid gap-0.5">{g.items.map(item)}</ul>
          </div>
        ))}
        <div className={cn('border-sidebar-border border-t', collapsed ? 'py-1' : 'pt-2')}><ul>{isSuperAdmin && item(USERS_ITEM)}{item(SETTINGS_ITEM)}</ul></div>
      </nav>
    </aside>
  )
}
