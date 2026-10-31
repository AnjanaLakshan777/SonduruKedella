import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

type Tone = 'secondary' | 'primary' | 'success' | 'warning' | 'destructive' | 'outline'
export const STATUS_TONES: Record<string, Tone> = {
  Upcoming: 'secondary', Unpaid: 'secondary', Planning: 'secondary', Draft: 'secondary', Admin: 'secondary', 'Super Admin': 'primary', Frozen: 'secondary', 'Nothing to Pay': 'secondary', Pending: 'secondary', Absent: 'secondary', Outstanding: 'primary',
  Due: 'primary', 'In Progress': 'primary', 'Ready to Pay': 'primary', Confirmed: 'primary', Scheduled: 'primary', Received: 'success',
  'Partially Paid': 'warning', 'Billing Late': 'warning', 'On Hold': 'warning', 'Half Day': 'warning', Estimated: 'warning', 'Partly Deducted': 'warning', 'Awaiting Approval': 'warning',
  Paid: 'success', Completed: 'success', 'In Stock': 'success', Present: 'success', Deducted: 'success', Approved: 'success', Active: 'success',
  Overdue: 'destructive', 'Out of Stock': 'destructive',
  'Written Off': 'outline', 'Bad Debt': 'outline', Cancelled: 'outline', 'From Stock': 'outline', Inactive: 'outline', Recorded: 'outline',
}

/** Status word + tone. The word carries the meaning; colour only reinforces it. */
export function StatusBadge({ status, title, className }: { status: string; title?: string; className?: string }) {
  const tone = STATUS_TONES[status] ?? 'secondary'
  return (
    <Badge variant={tone} title={title} className={className}>
      <span
        aria-hidden
        className={cn('size-1.5 rounded-full bg-current', tone === 'secondary' && 'bg-muted-foreground', tone === 'outline' && 'bg-transparent border border-current')}
      />
      {status}
    </Badge>
  )
}
