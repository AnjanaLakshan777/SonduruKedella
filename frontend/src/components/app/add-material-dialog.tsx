import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Package } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { AmountInput, Field, SelectField } from '@/components/app/form'
import { useStore } from '@/store/store'
import { errorMessage } from '@/lib/api'

// Used on the Inventory page and from a supplier invoice line, which passes the name typed so far
// and gets the new material's code back to select it.
export function AddMaterialDialog({ open, onOpenChange, initialName, onAdded }: { open: boolean; onOpenChange: (o: boolean) => void; initialName?: string; onAdded?: (sku: string) => void }) {
  const { addMaterial, settings, materials } = useStore()
  const [f, setF] = useState({ name: '', sku: '', category: '', unit: 'nos', unitCost: '' as number | '' })
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (open) { setTried(false); setF({ name: initialName ?? '', sku: '', category: '', unit: 'nos', unitCost: '' }) } }, [open, initialName])
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
          <Field label="Default unit cost" optional className="sm:col-span-2"><AmountInput value={f.unitCost} onChange={(v) => setF({ ...f, unitCost: v })} /></Field>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={busy} onClick={async () => {
            setTried(true)
            if (Object.values(err).some(Boolean)) return
            setBusy(true)
            try {
              await addMaterial({ name: f.name.trim(), sku: f.sku.trim(), category: f.category.trim(), unit: f.unit, unitCost: Number(f.unitCost) || 0 })
              toast.success('Material added', { description: f.name })
              onAdded?.(f.sku.trim())
              onOpenChange(false)
            } catch (e) {
              toast.error('Material not added', { description: errorMessage(e) })
            } finally {
              setBusy(false)
            }
          }}><Package />Add material</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
