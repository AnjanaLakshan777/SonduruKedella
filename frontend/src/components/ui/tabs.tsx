import * as React from 'react'
import * as TabsPrimitive from '@radix-ui/react-tabs'
import { cn } from '@/lib/utils'

function Tabs({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.Root>) {
  return <TabsPrimitive.Root data-slot="tabs" className={cn('flex flex-col gap-4 min-w-0', className)} {...props} />
}
/** variant "line": underline tabs for page sections; "pill": the preset's segmented control. */
function TabsList({ className, variant = 'pill', ...props }: React.ComponentProps<typeof TabsPrimitive.List> & { variant?: 'pill' | 'line' }) {
  return (
    <TabsPrimitive.List
      data-slot="tabs-list"
      data-variant={variant}
      className={cn(
        variant === 'pill'
          ? 'bg-black/5 dark:bg-muted text-muted-foreground inline-flex h-9 w-fit max-w-full items-center justify-center rounded-lg p-[3px] overflow-x-auto'
          : 'flex w-full items-end gap-5 border-b overflow-x-auto [scrollbar-width:none]',
        className,
      )}
      {...props}
    />
  )
}
function TabsTrigger({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.Trigger>) {
  return (
    <TabsPrimitive.Trigger
      data-slot="tabs-trigger"
      className={cn(
        "focus-visible:border-ring focus-visible:ring-ring/50 inline-flex items-center justify-center gap-1.5 text-sm font-medium whitespace-nowrap transition-[color,box-shadow] focus-visible:ring-[3px] focus-visible:outline-1 disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
        'in-data-[variant=pill]:text-foreground in-data-[variant=pill]:h-[calc(100%-1px)] in-data-[variant=pill]:flex-1 in-data-[variant=pill]:rounded-md in-data-[variant=pill]:border in-data-[variant=pill]:border-transparent in-data-[variant=pill]:px-2.5 in-data-[variant=pill]:py-1 in-data-[variant=pill]:data-[state=active]:bg-card in-data-[variant=pill]:data-[state=active]:shadow-sm dark:in-data-[variant=pill]:data-[state=active]:bg-input/30 dark:in-data-[variant=pill]:data-[state=active]:border-input',
        'in-data-[variant=line]:text-muted-foreground in-data-[variant=line]:hover:text-foreground in-data-[variant=line]:-mb-px in-data-[variant=line]:border-b-2 in-data-[variant=line]:border-transparent in-data-[variant=line]:px-0.5 in-data-[variant=line]:pb-2.5 in-data-[variant=line]:pt-1 in-data-[variant=line]:data-[state=active]:text-foreground in-data-[variant=line]:data-[state=active]:border-primary',
        className,
      )}
      {...props}
    />
  )
}
function TabsContent({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.Content>) {
  return <TabsPrimitive.Content data-slot="tabs-content" className={cn('flex-1 outline-none min-w-0', className)} {...props} />
}

export { Tabs, TabsList, TabsTrigger, TabsContent }
