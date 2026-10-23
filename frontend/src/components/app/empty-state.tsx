import type { LucideIcon } from 'lucide-react'
import { Inbox } from 'lucide-react'
import { cn } from '@/lib/utils'

export function EmptyState({ icon: Icon = Inbox, title, description, action, className }: { icon?: LucideIcon; title: React.ReactNode; description?: React.ReactNode; action?: React.ReactNode; className?: string }) {
  return (
    <div className={cn('grid justify-items-center gap-2 px-6 py-12 text-center', className)}>
      <div className="bg-muted text-muted-foreground grid size-10 place-items-center rounded-md"><Icon className="size-5" /></div>
      <div className="font-semibold">{title}</div>
      {description && <p className="text-muted-foreground mb-2 max-w-sm text-sm text-balance">{description}</p>}
      {action}
    </div>
  )
}
