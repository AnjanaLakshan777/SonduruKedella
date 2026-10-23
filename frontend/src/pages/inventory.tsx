import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { ArrowLeftRight, Ellipsis, FilePlus2, Info, Package, Plus, TriangleAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { KpiStrip } from '@/components/app/kpi-strip'
import { Money } from '@/components/app/money'
import { StatusBadge } from '@/components/app/status-badge'
import { CellMain, Mono, Pagination, usePaged } from '@/components/app/data-table'
import { ALL, FilterBar, FilterSelect, SearchInput } from '@/components/app/filters'
import { AmountInput, DatePicker, Field, SelectField } from '@/components/app/form'
import { FigureList } from '@/components/app/figure-list'
import { useData, useStore } from '@/store/store'
import { sum, type MaterialView } from '@/lib/finance'
import { formatDate, TODAY } from '@/lib/dates'
import { formatLKR, formatQty } from '@/lib/format'
import type { MovementType, StockMovement } from '@/data/types'
import { cn } from '@/lib/utils'

export default function InventoryPage() {
  useCrumbs([{ label: 'Inventory' }])
  const d = useData()
  const [q, setQ] = useState('')
  const [cat, setCat] = useState(ALL)
  const [status, setStatus] = useState(ALL)
  const [move, setMove] = useState<{ open: boolean; materialId?: string; type?: MovementType }>({ open: false })
  const [view, setView] = useState<MaterialView | undefined>()
  const [addOpen, setAddOpen] = useState(false)
  const rows = d.materials.filter((m) => (cat === ALL || m.material.category === cat) && (status === ALL || m.status === status) && (!q || `${m.material.name} ${m.material.sku}`.toLowerCase().includes(q.toLowerCase())))
  const issuedOct = d.s.stockMovements.filter((m) => m.type === 'Issued' && m.date >= '2026-10-01')
  const moves = useMemo(() => d.s.stockMovements.slice().sort((a, b) => b.date.localeCompare(a.date) || b.id.localeCompare(a.id)), [d])
  const paged = usePaged(moves, 15)
  const liveView = view ? d.materials.find((m) => m.material.id === view.material.id) : undefined

  return (
    <>
      <PageHeader
        title="Inventory"
        description="Materials held in the company store. Buying into stock is not a project cost; issuing to a project is — at the issue cost — so nothing is counted twice."
        actions={<><Button variant="outline" asChild><Link to="/suppliers/invoices/new"><FilePlus2 />Record purchase</Link></Button><Button onClick={() => setMove({ open: true })}><ArrowLeftRight />Stock movement</Button></>}
      />
      <KpiStrip items={[
        { label: 'Stock value', unit: 'LKR', value: <Money value={d.totals.stockValue} decimals={0} currency={false} />, caption: 'At latest unit cost' },
        { label: 'Materials tracked', value: d.materials.length, caption: `${new Set(d.materials.map((m) => m.material.category)).size} categories` },
        { label: 'Low or out of stock', value: d.materials.filter((m) => m.status !== 'In Stock').length, tone: d.materials.some((m) => m.status === 'Out of Stock') ? 'destructive' : 'default', caption: 'At or below reorder level' },
        { label: 'Issued to projects (Oct)', unit: 'LKR', value: <Money value={sum(issuedOct, (m) => -m.qty * m.unitCost)} decimals={0} currency={false} />, basis: 'Recorded', caption: `${issuedOct.length} issues — charged to projects` },
      ]} />
      {d.materials.some((m) => m.status !== 'In Stock') && (
        <div className="bg-warning-soft text-warning flex items-start gap-3 rounded-lg border border-current/20 px-4 py-3 text-sm">
          <TriangleAlert className="mt-0.5 size-4 shrink-0" />
          <div><span className="font-medium">Reorder soon: </span>{d.materials.filter((m) => m.status !== 'In Stock').map((m) => `${m.material.name} (${formatQty(m.qty, m.material.unit)} left)`).join(' · ')}</div>
        </div>
      )}
      <Tabs defaultValue="stock" className="gap-4">
        <TabsList variant="line"><TabsTrigger value="stock">Materials</TabsTrigger><TabsTrigger value="movements">Stock movements</TabsTrigger></TabsList>
        <TabsContent value="stock" className="grid gap-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <FilterBar>
              <SearchInput value={q} onChange={setQ} placeholder="Search material or SKU…" />
              <FilterSelect label="Categories" value={cat} onChange={setCat} options={[...new Set(d.materials.map((m) => m.material.category))]} />
              <FilterSelect label="Stock levels" value={status} onChange={setStatus} options={['In Stock', 'Low Stock', 'Out of Stock']} />
            </FilterBar>
            <Button variant="outline" size="sm" onClick={() => setAddOpen(true)}><Plus />Add material</Button>
          </div>
          <Card className="gap-0 py-0">
            <Table>
              <TableHeader><TableRow><TableHead className="pl-6">Material</TableHead><TableHead className="hidden md:table-cell">Category</TableHead><TableHead className="text-right">In stock</TableHead><TableHead className="hidden text-right sm:table-cell">Reorder level</TableHead><TableHead className="hidden text-right lg:table-cell">Unit cost (LKR)</TableHead><TableHead className="hidden text-right md:table-cell">Value (LKR)</TableHead><TableHead>Status</TableHead><TableHead className="w-12 pr-4" /></TableRow></TableHeader>
              <TableBody>
                {rows.map((m) => (
                  <TableRow key={m.material.id} className="cursor-pointer" onClick={() => setView(m)}>
                    <TableCell className="pl-6"><CellMain title={m.material.name} sub={<Mono>{m.material.sku}</Mono>} /></TableCell>
                    <TableCell className="hidden md:table-cell">{m.material.category}</TableCell>
                    <TableCell className={cn('tabular text-right font-medium', m.status !== 'In Stock' && 'text-destructive-soft-foreground')}>{formatQty(m.qty, m.material.unit)}</TableCell>
                    <TableCell className="tabular text-muted-foreground hidden text-right sm:table-cell">{formatQty(m.material.reorderLevel, m.material.unit)}</TableCell>
                    <TableCell className="hidden text-right lg:table-cell"><Money value={m.material.unitCost} currency={false} /></TableCell>
                    <TableCell className="hidden text-right md:table-cell"><Money value={m.value} currency={false} /></TableCell>
                    <TableCell><StatusBadge status={m.status} /></TableCell>
                    <TableCell className="pr-4" onClick={(e) => e.stopPropagation()}>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${m.material.name}`}><Ellipsis /></Button></DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onSelect={() => setMove({ open: true, materialId: m.material.id, type: 'Issued' })}>Issue to project</DropdownMenuItem>
                          <DropdownMenuItem onSelect={() => setMove({ open: true, materialId: m.material.id, type: 'Received' })}>Record stock received</DropdownMenuItem>
                          <DropdownMenuItem onSelect={() => setMove({ open: true, materialId: m.material.id, type: 'Adjustment' })}>Adjust stock</DropdownMenuItem>
                          <DropdownMenuItem onSelect={() => setView(m)}>View movements</DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
              <TableFooter><TableRow><TableCell className="pl-6" colSpan={2}>{rows.length} materials</TableCell><TableCell colSpan={3} className="hidden lg:table-cell" /><TableCell className="hidden text-right md:table-cell"><Money value={sum(rows, (m) => m.value)} currency={false} /></TableCell><TableCell colSpan={2} /></TableRow></TableFooter>
            </Table>
          </Card>
        </TabsContent>
        <TabsContent value="movements">
          <Card className="gap-0 py-0">
            <MovementsTable moves={paged.rows} />
            <div className="border-t px-6 py-3"><Pagination {...paged} /></div>
          </Card>
        </TabsContent>
      </Tabs>

      <MovementDialog state={move} onClose={() => setMove({ open: false })} />
      <AddMaterialDialog open={addOpen} onOpenChange={setAddOpen} />
      <Sheet open={!!liveView} onOpenChange={(o) => !o && setView(undefined)}>
        <SheetContent className="overflow-y-auto sm:max-w-2xl">
          {liveView && (
            <>
              <SheetHeader><SheetTitle>{liveView.material.name}</SheetTitle><SheetDescription>{liveView.material.sku} · {liveView.material.category} · unit: {liveView.material.unit}</SheetDescription></SheetHeader>
              <div className="grid gap-5 px-6 pb-6">
                <div className="flex gap-2"><StatusBadge status={liveView.status} /></div>
                <FigureList rows={[
                  { label: 'Received into stock', value: <span className="tabular">{formatQty(liveView.received, liveView.material.unit)}</span> },
                  { kind: 'less', label: 'Issued to projects', value: <span className="tabular">{formatQty(liveView.issued, liveView.material.unit)}</span> },
                  { label: 'Adjustments', value: <span className="tabular">{formatQty(liveView.adjusted, liveView.material.unit)}</span> },
                  { kind: 'highlight', label: 'Current stock', value: <span className="tabular">{formatQty(liveView.qty, liveView.material.unit)} · {formatLKR(liveView.value)}</span> },
                ]} />
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" onClick={() => setMove({ open: true, materialId: liveView.material.id, type: 'Issued' })}>Issue to project</Button>
                  <Button size="sm" variant="outline" onClick={() => setMove({ open: true, materialId: liveView.material.id, type: 'Adjustment' })}>Adjust stock</Button>
                </div>
                <div className="overflow-hidden rounded-lg border"><MovementsTable moves={liveView.movements} compact /></div>
              </div>
            </>
          )}
        </SheetContent>
      </Sheet>
    </>
  )
}

function MovementsTable({ moves, compact }: { moves: StockMovement[]; compact?: boolean }) {
  const d = useData()
  return (
    <Table>
      <TableHeader><TableRow><TableHead className="pl-6">Date</TableHead>{!compact && <TableHead>Material</TableHead>}<TableHead>Movement</TableHead><TableHead className="text-right">Qty</TableHead><TableHead className="pr-6 text-right">Cost (LKR)</TableHead></TableRow></TableHeader>
      <TableBody>
        {moves.map((m) => {
          const mat = d.materialById.get(m.materialId)!
          const p = m.projectId ? d.projectById.get(m.projectId) : undefined
          return (
            <TableRow key={m.id}>
              <TableCell className="pl-6">{formatDate(m.date)}</TableCell>
              {!compact && <TableCell><CellMain title={mat.name} sub={<Mono>{mat.sku}</Mono>} /></TableCell>}
              <TableCell className="max-w-72">
                <CellMain title={m.type === 'Issued' ? `Issued to ${p?.code}` : m.type === 'Received' ? 'Received' : 'Adjustment'} sub={m.type === 'Issued' ? p?.name : m.type === 'Received' ? `Invoice ${m.reference ?? ''}` : m.reason} />
              </TableCell>
              <TableCell className={cn('tabular text-right font-medium', m.qty < 0 && 'text-muted-foreground')}>{m.qty > 0 ? '+' : m.qty < 0 ? '−' : ''}{formatQty(Math.abs(m.qty), mat.unit)}</TableCell>
              <TableCell className="pr-6 text-right">{m.type === 'Issued' ? <span title="Charged to the project"><Money value={-m.qty * m.unitCost} currency={false} /></span> : m.type === 'Received' ? <Money value={m.qty * m.unitCost} currency={false} tone="muted" /> : <Money value={m.qty * m.unitCost} currency={false} tone="muted" />}</TableCell>
            </TableRow>
          )
        })}
      </TableBody>
    </Table>
  )
}

function MovementDialog({ state, onClose }: { state: { open: boolean; materialId?: string; type?: MovementType }; onClose: () => void }) {
  const d = useData()
  const addStockMovement = useStore((s) => s.addStockMovement)
  const [type, setType] = useState<MovementType>('Issued')
  const [materialId, setMaterialId] = useState('')
  const [date, setDate] = useState(TODAY)
  const [qty, setQty] = useState<number | ''>('')
  const [direction, setDirection] = useState<'add' | 'remove'>('remove')
  const [cost, setCost] = useState<number | ''>('')
  const [projectId, setProjectId] = useState('')
  const [reason, setReason] = useState('')
  const [reference, setReference] = useState('')
  const [tried, setTried] = useState(false)
  useEffect(() => { if (state.open) { setType(state.type ?? 'Issued'); setMaterialId(state.materialId ?? ''); setDate(TODAY); setQty(''); setDirection('remove'); setProjectId(''); setReason(''); setReference(''); setTried(false) } }, [state])
  const mv = d.materials.find((m) => m.material.id === materialId)
  useEffect(() => { setCost(mv?.material.unitCost ?? '') }, [mv?.material.unitCost])
  const q = typeof qty === 'number' ? qty : 0
  const err = {
    material: !materialId ? 'Choose a material.' : undefined,
    qty: !(q > 0) ? 'Enter a quantity.' : (type === 'Issued' || (type === 'Adjustment' && direction === 'remove')) && mv && q > mv.qty ? `Only ${formatQty(mv.qty, mv.material.unit)} in stock.` : undefined,
    project: type === 'Issued' && !projectId ? 'Choose the project receiving the material.' : undefined,
    reason: type === 'Adjustment' && reason.trim().length < 5 ? 'Give a reason for the adjustment (count difference, damage…).' : undefined,
    cost: type === 'Received' && !(Number(cost) > 0) ? 'Enter the unit cost.' : undefined,
  }
  function save() {
    setTried(true)
    if (Object.values(err).some(Boolean) || !mv) return
    const signed = type === 'Received' ? q : type === 'Issued' ? -q : direction === 'add' ? q : -q
    addStockMovement({ materialId, date, type, qty: signed, unitCost: type === 'Received' ? Number(cost) : mv.material.unitCost, projectId: type === 'Issued' ? projectId : undefined, reason: reason || undefined, reference: reference || undefined })
    const p = d.projectById.get(projectId)
    toast.success(type === 'Issued' ? 'Issued to project' : type === 'Received' ? 'Stock received' : 'Stock adjusted', {
      description: type === 'Issued' ? `${formatQty(q, mv.material.unit)} ${mv.material.name} → ${p?.code}. ${formatLKR(q * mv.material.unitCost)} charged to the project.` : `${mv.material.name}: ${signed > 0 ? '+' : '−'}${formatQty(q, mv.material.unit)}`,
    })
    onClose()
  }
  return (
    <Dialog open={state.open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader><DialogTitle>Stock movement</DialogTitle><DialogDescription>Issuing to a project charges its cost to that project. Purchases from suppliers are best recorded as supplier invoices.</DialogDescription></DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Movement" required className="sm:col-span-2">
            <RadioGroup value={type} onValueChange={(v) => setType(v as MovementType)} className="grid gap-2 sm:grid-cols-3">
              {(['Issued', 'Received', 'Adjustment'] as const).map((t) => (
                <Label key={t} className="has-[[data-state=checked]]:border-primary-text has-[[data-state=checked]]:bg-primary-soft/60 flex cursor-pointer items-center gap-2.5 rounded-md border p-2.5 font-normal"><RadioGroupItem value={t} />{t === 'Issued' ? 'Issue to project' : t === 'Received' ? 'Stock received' : 'Adjustment'}</Label>
              ))}
            </RadioGroup>
          </Field>
          <Field label="Material" required error={tried ? err.material : undefined} className="sm:col-span-2" hint={mv ? `${formatQty(mv.qty, mv.material.unit)} in stock · ${formatLKR(mv.material.unitCost)} per ${mv.material.unit}` : undefined}>
            <SelectField value={materialId} onChange={setMaterialId} invalid={tried && !!err.material} placeholder="Select material" options={d.materials.map((m) => ({ value: m.material.id, label: `${m.material.sku} · ${m.material.name}` }))} />
          </Field>
          {type === 'Issued' && <Field label="Project" required error={tried ? err.project : undefined} className="sm:col-span-2"><SelectField value={projectId} onChange={setProjectId} invalid={tried && !!err.project} placeholder="Select project" options={d.s.projects.filter((p) => p.type === 'Construction' && (p.status === 'In Progress' || p.status === 'On Hold' || p.status === 'Planning')).map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }))} /></Field>}
          {type === 'Adjustment' && (
            <Field label="Direction" required className="sm:col-span-2">
              <RadioGroup value={direction} onValueChange={(v) => setDirection(v as typeof direction)} className="flex gap-6"><Label className="font-normal"><RadioGroupItem value="remove" />Reduce stock</Label><Label className="font-normal"><RadioGroupItem value="add" />Increase stock</Label></RadioGroup>
            </Field>
          )}
          <Field label="Quantity" required error={tried ? err.qty : undefined}><div className="flex items-center gap-2"><Input type="number" min={0} className="text-right" value={qty} onChange={(e) => setQty(e.target.value === '' ? '' : Number(e.target.value))} aria-invalid={(tried && !!err.qty) || undefined} /><span className="text-muted-foreground text-sm">{mv?.material.unit}</span></div></Field>
          <Field label="Date" required><DatePicker value={date} onChange={setDate} /></Field>
          {type === 'Received' && <Field label="Unit cost" required error={tried ? err.cost : undefined}><AmountInput value={cost} onChange={setCost} /></Field>}
          {type === 'Adjustment' && <Field label="Reason" required error={tried ? err.reason : undefined} className="sm:col-span-2"><Textarea rows={2} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Monthly count — 4 bags hardened" /></Field>}
          <Field label="Reference" optional><Input value={reference} onChange={(e) => setReference(e.target.value)} placeholder={type === 'Issued' ? 'Goods issue note no.' : 'GRN / invoice no.'} /></Field>
          {type === 'Issued' && mv && q > 0 && <div className="bg-muted/60 flex items-center gap-2 self-end rounded-md p-2.5 text-sm"><Info className="text-muted-foreground size-4 shrink-0" />Project cost: <Money value={q * mv.material.unitCost} className="font-medium" /></div>}
          {type === 'Received' && <p className="text-muted-foreground flex gap-2 text-xs sm:col-span-2"><Info className="size-3.5 shrink-0" />No supplier payable is created here. For credit purchases, <Link to="/suppliers/invoices/new" className="text-primary-text underline" onClick={onClose}>record a supplier invoice</Link> instead.</p>}
        </div>
        <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button onClick={save}>Save movement</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function AddMaterialDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const { addMaterial, settings, materials } = useStore()
  const [f, setF] = useState({ name: '', sku: '', category: '', unit: 'nos', reorderLevel: '' as number | '', unitCost: '' as number | '' })
  const [tried, setTried] = useState(false)
  useEffect(() => { if (open) { setTried(false); setF({ name: '', sku: '', category: '', unit: 'nos', reorderLevel: '', unitCost: '' }) } }, [open])
  const err = { name: !f.name.trim() ? 'Enter a name.' : undefined, sku: !f.sku.trim() ? 'Enter an item code.' : materials.some((m) => m.sku.toLowerCase() === f.sku.trim().toLowerCase()) ? 'This code is already used.' : undefined, category: !f.category.trim() ? 'Enter a category.' : undefined }
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader><DialogTitle>Add material</DialogTitle><DialogDescription>Starts with zero stock. Record a purchase or stock received to add quantity.</DialogDescription></DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Material name" required error={tried ? err.name : undefined} className="sm:col-span-2"><Input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} placeholder="e.g. Roofing sheet (Amano 8 ft)" /></Field>
          <Field label="SKU / item code" required error={tried ? err.sku : undefined}><Input value={f.sku} onChange={(e) => setF({ ...f, sku: e.target.value.toUpperCase() })} className="font-mono" /></Field>
          <Field label="Category" required error={tried ? err.category : undefined}><Input value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })} list="mat-cats" /><datalist id="mat-cats">{[...new Set(materials.map((m) => m.category))].map((c) => <option key={c} value={c} />)}</datalist></Field>
          <Field label="Unit of measurement" required><SelectField value={f.unit} onChange={(v) => setF({ ...f, unit: v })} options={settings.units} /></Field>
          <Field label="Reorder level" optional><Input type="number" min={0} className="text-right" value={f.reorderLevel} onChange={(e) => setF({ ...f, reorderLevel: e.target.value === '' ? '' : Number(e.target.value) })} /></Field>
          <Field label="Default unit cost" optional className="sm:col-span-2"><AmountInput value={f.unitCost} onChange={(v) => setF({ ...f, unitCost: v })} /></Field>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button onClick={() => { setTried(true); if (Object.values(err).some(Boolean)) return; addMaterial({ name: f.name.trim(), sku: f.sku.trim(), category: f.category.trim(), unit: f.unit, reorderLevel: Number(f.reorderLevel) || 0, unitCost: Number(f.unitCost) || 0 }); toast.success('Material added', { description: f.name }); onOpenChange(false) }}><Package />Add material</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
