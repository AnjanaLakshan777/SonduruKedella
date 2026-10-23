import * as React from 'react'
import { DayPicker } from 'react-day-picker'
import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { buttonVariants } from '@/components/ui/button'

function Calendar({ className, classNames, showOutsideDays = true, ...props }: React.ComponentProps<typeof DayPicker>) {
  return (
    <DayPicker
      showOutsideDays={showOutsideDays}
      weekStartsOn={1}
      className={cn('p-3', className)}
      classNames={{
        months: 'relative flex flex-col gap-4 sm:flex-row',
        month: 'flex w-full flex-col gap-4',
        month_caption: 'flex h-8 items-center justify-center text-sm font-medium',
        nav: 'absolute inset-x-0 top-0 flex items-center justify-between',
        button_previous: cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-8'),
        button_next: cn(buttonVariants({ variant: 'ghost', size: 'icon' }), 'size-8'),
        month_grid: 'w-full border-collapse',
        weekdays: 'flex',
        weekday: 'text-muted-foreground w-8 text-[0.8rem] font-normal',
        week: 'mt-1 flex w-full',
        day: 'relative size-8 p-0 text-center text-sm',
        day_button: cn(buttonVariants({ variant: 'ghost' }), 'size-8 p-0 font-normal aria-selected:opacity-100'),
        selected: '[&>button]:bg-primary [&>button]:text-primary-foreground [&>button:hover]:bg-primary [&>button:hover]:text-primary-foreground rounded-md',
        today: '[&>button]:bg-accent [&>button]:text-accent-foreground rounded-md',
        outside: 'text-muted-foreground opacity-50',
        disabled: 'text-muted-foreground opacity-50',
        hidden: 'invisible',
        ...classNames,
      }}
      components={{
        Chevron: ({ orientation }) => (orientation === 'left' ? <ChevronLeftIcon className="size-4" /> : <ChevronRightIcon className="size-4" />),
      }}
      {...props}
    />
  )
}

export { Calendar }
