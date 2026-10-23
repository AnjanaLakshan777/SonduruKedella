import { useMemo, useState } from 'react'
import { Plus, Receipt } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ExpenseTable } from '@/components/app/expense-table'
import { RecordExpenseDialog } from '@/components/app/record-expense-dialog'
import { EmptyState } from '@/components/app/empty-state'
import { Money } from '@/components/app/money'
import { useData } from '@/store/store'
import type { ProjectFinance } from '@/lib/finance'
import { cn } from '@/lib/utils'

export function ProjectExpenses({ pf }: { pf: ProjectFinance }) {
  const d = useData()
  const [open, setOpen] = useState(false)
  const [cat, setCat] = useState<string | null>(null)
  const lines = useMemo(() => d.expenseLines.filter((l) => l.projectId === pf.project.id), [d, pf.project.id])
  const cats = useMemo(() => {
    const m = new Map<string, number>()
    for (const l of lines) m.set(l.category, (m.get(l.category) ?? 0) + l.amount)
    return [...m.entries()].sort((a, b) => b[1] - a[1])
  }, [lines])
  const shown = cat ? lines.filter((l) => l.category === cat) : lines
  return (
    <div className="grid gap-4">
      {cats.length > 0 && (
        <div className="flex flex-wrap gap-2" role="group" aria-label="Filter by category">
          <button type="button" onClick={() => setCat(null)} className={cn('rounded-md border px-3 py-1.5 text-left text-sm', !cat ? 'border-primary-text bg-primary-soft/60' : 'hover:bg-accent')}>
            <div className="text-muted-foreground text-xs">All costs</div><Money value={pf.totalCosts} decimals={0} className="font-medium" />
          </button>
          {cats.map(([c, v]) => (
            <button key={c} type="button" onClick={() => setCat(cat === c ? null : c)} className={cn('rounded-md border px-3 py-1.5 text-left text-sm', cat === c ? 'border-primary-text bg-primary-soft/60' : 'hover:bg-accent')}>
              <div className="text-muted-foreground text-xs">{c}</div><Money value={v} decimals={0} className="font-medium" />
            </button>
          ))}
        </div>
      )}
      <Card className="gap-4 pb-0">
        <CardHeader>
          <CardTitle>Project expenses{cat ? ` — ${cat}` : ''}</CardTitle>
          <CardDescription>Direct costs of this project. Materials issued from stock are valued at issue cost; labour comes from attendance and approved overtime.</CardDescription>
          <CardAction><Button onClick={() => setOpen(true)}><Plus />Record Expense</Button></CardAction>
        </CardHeader>
        <ExpenseTable
          lines={shown}
          showProject={false}
          empty={<EmptyState icon={Receipt} title="No expenses recorded for this project" description="Record materials, labour, machinery and other costs here to see the project's real profitability." action={<Button size="sm" onClick={() => setOpen(true)}><Plus />Record Expense</Button>} className="border-t" />}
        />
      </Card>
      <RecordExpenseDialog open={open} onOpenChange={setOpen} projectId={pf.project.id} />
    </div>
  )
}
