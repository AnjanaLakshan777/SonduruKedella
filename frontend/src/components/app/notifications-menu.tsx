import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell, CalendarClock, CheckCheck, CircleAlert, Clock, Truck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { useData } from '@/store/store'
import { useAuth } from '@/store/auth'
import { formatDate } from '@/lib/dates'
import { notifications, type NoticeKind } from '@/lib/notifications'
import { cn } from '@/lib/utils'

const ICONS: Record<NoticeKind, React.ReactNode> = {
  overdue: <CircleAlert className="text-destructive! mt-0.5" />,
  'billing-late': <CalendarClock className="text-warning! mt-0.5" />,
  payable: <Truck className="mt-0.5" />,
  overtime: <Clock className="mt-0.5" />,
}

// "read" lives in this browser only, per user. Storage can be blocked, so every access is guarded.
function useReadKeys(userId: number | undefined) {
  const storageKey = `sk.notifications.read.${userId ?? 'anon'}`
  const [read, setRead] = useState<Set<string>>(() => {
    try { return new Set(JSON.parse(localStorage.getItem(storageKey) ?? '[]') as string[]) } catch { return new Set() }
  })
  const save = (next: Set<string>) => {
    setRead(next)
    try { localStorage.setItem(storageKey, JSON.stringify([...next])) } catch { /* still read for this session */ }
  }
  return { read, save }
}

export function NotificationsMenu() {
  const d = useData()
  const navigate = useNavigate()
  const { user } = useAuth()
  const items = useMemo(() => notifications(d), [d])
  const { read, save } = useReadKeys(user?.id)
  const unread = items.filter((n) => !read.has(n.key)).length

  // only keep keys that are still showing, so the stored list doesn't grow forever
  const markAll = () => save(new Set(items.map((n) => n.key)))
  const markOne = (key: string) => save(new Set([...items.map((n) => n.key).filter((k) => read.has(k)), key]))

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label={`${unread} unread notifications`} className="relative">
          <Bell className="size-[18px]" />
          {unread > 0 && <span className="bg-destructive dark:bg-destructive/70 tabular absolute top-0.5 right-0 min-w-4 rounded-full px-1 text-[10px] leading-4 font-semibold text-white">{unread}</span>}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-[min(380px,calc(100vw-2rem))] p-0">
        <div className="flex items-center justify-between gap-2 py-1 pr-1">
          <DropdownMenuLabel>Needs attention{items.length ? <span className="text-muted-foreground font-normal"> · {items.length}</span> : null}</DropdownMenuLabel>
          <Button variant="ghost" size="sm" className="h-7 text-xs" disabled={!unread} onClick={markAll}><CheckCheck />Mark all as read</Button>
        </div>
        <DropdownMenuSeparator className="my-0" />
        <div className="max-h-[min(28rem,70vh)] overflow-y-auto overscroll-contain p-1">
          {items.map((n) => {
            const isRead = read.has(n.key)
            return (
              <DropdownMenuItem key={n.key} onSelect={() => { markOne(n.key); navigate(n.to) }} className="items-start">
                {ICONS[n.kind]}
                <span className={cn('grid min-w-0 flex-1', isRead && 'text-muted-foreground')}>
                  <span className={cn(!isRead && 'font-medium')}>{n.title}</span>
                  <span className="text-muted-foreground text-xs">{n.detail}</span>
                  <span className="text-muted-foreground text-[11px]">{formatDate(n.date)}</span>
                </span>
                {!isRead && <span aria-label="Unread" className="bg-primary mt-1.5 size-2 shrink-0 rounded-full" />}
              </DropdownMenuItem>
            )
          })}
          {!items.length && <p className="text-muted-foreground px-2 py-6 text-center text-sm">Nothing needs attention.</p>}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
