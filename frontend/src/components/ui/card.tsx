import * as React from 'react'
import { cn } from '@/lib/utils'

function Card({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="card" className={cn('bg-card text-card-foreground flex flex-col gap-6 rounded-xl border py-6 shadow-sm min-w-0', className)} {...props} />
}
function CardHeader({ className, ...props }: React.ComponentProps<'div'>) {
  return (
    <div
      data-slot="card-header"
      className={cn('@container/card-header grid auto-rows-min items-start gap-1.5 px-6 sm:grid-rows-[auto_auto] sm:has-data-[slot=card-action]:grid-cols-[1fr_auto]', className)}
      {...props}
    />
  )
}
function CardTitle({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="card-title" className={cn('leading-tight font-semibold', className)} {...props} />
}
function CardDescription({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="card-description" className={cn('text-muted-foreground text-sm', className)} {...props} />
}
function CardAction({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="card-action" className={cn('flex flex-wrap gap-2 self-start pt-1 sm:col-start-2 sm:row-span-2 sm:row-start-1 sm:justify-self-end sm:pt-0', className)} {...props} />
}
function CardContent({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="card-content" className={cn('px-6', className)} {...props} />
}
function CardFooter({ className, ...props }: React.ComponentProps<'div'>) {
  return <div data-slot="card-footer" className={cn('flex items-center px-6 [.border-t]:pt-6', className)} {...props} />
}

export { Card, CardHeader, CardFooter, CardTitle, CardAction, CardDescription, CardContent }
