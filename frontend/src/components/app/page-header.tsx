import { cn } from '@/lib/utils'

export function PageHeader({ title, description, actions, meta, className }: { title: React.ReactNode; description?: React.ReactNode; actions?: React.ReactNode; meta?: React.ReactNode; className?: string }) {
  return (
    <div className={cn('flex flex-wrap items-end justify-between gap-4', className)}>
      <div className="min-w-0">
        {meta && <div className="mb-1.5 flex flex-wrap items-center gap-2">{meta}</div>}
        <h1 className="text-2xl leading-8 font-semibold tracking-tight text-balance">{title}</h1>
        {description && <p className="text-muted-foreground mt-1 text-sm">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

export function SectionTitle({ title, description, actions }: { title: React.ReactNode; description?: React.ReactNode; actions?: React.ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 className="text-lg leading-7 font-semibold tracking-tight">{title}</h2>
        {description && <p className="text-muted-foreground text-sm">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}
