import type { DelayTerms } from '@/data/types'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Label } from '@/components/ui/label'
import { AmountInput, DatePicker, Field } from './form'

export const RULE_LABEL: Record<DelayTerms['rule'], string> = {
  per_day: 'Amount per day late',
  percent_per_week: '% of contract value per full week late',
  fixed_amount: 'One fixed amount if late',
}

/** Configures the agreed delay-deduction rule. Nothing is hard-coded: every contract can differ. */
export function DelayTermsForm({ value, onChange, contractValue, showErrors }: { value: DelayTerms; onChange: (v: DelayTerms) => void; contractValue: number; showErrors?: boolean }) {
  const set = (patch: Partial<DelayTerms>) => onChange({ ...value, ...patch })
  const capPct = value.maxDeduction && contractValue ? ((value.maxDeduction / contractValue) * 100).toFixed(1) : null
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Field label="Contractual completion deadline" required error={showErrors && !value.contractDeadline ? 'Set the deadline from the agreement.' : undefined}>
        <DatePicker value={value.contractDeadline} onChange={(v) => set({ contractDeadline: v })} invalid={showErrors && !value.contractDeadline} />
      </Field>
      <Field label="Grace period" optional hint="Days after the deadline before deductions start.">
        <div className="flex items-center gap-2"><Input type="number" min={0} className="w-28 text-right" value={value.graceDays ?? ''} onChange={(e) => set({ graceDays: e.target.value === '' ? undefined : Math.max(0, Number(e.target.value)) })} /><span className="text-muted-foreground text-sm">days</span></div>
      </Field>
      <Field label="Agreed deduction rule" required className="sm:col-span-2">
        <RadioGroup value={value.rule} onValueChange={(v) => set({ rule: v as DelayTerms['rule'] })} className="grid gap-2 sm:grid-cols-3">
          {(Object.keys(RULE_LABEL) as DelayTerms['rule'][]).map((r) => (
            <Label key={r} className="has-[[data-state=checked]]:border-primary-text has-[[data-state=checked]]:bg-primary-soft/60 flex cursor-pointer items-start gap-2.5 rounded-md border p-3 leading-snug font-normal">
              <RadioGroupItem value={r} className="mt-0.5" />
              {RULE_LABEL[r]}
            </Label>
          ))}
        </RadioGroup>
      </Field>
      {value.rule === 'per_day' && (
        <Field label="Deduction per day" required error={showErrors && !(value.ratePerDay! > 0) ? 'Enter the agreed daily amount.' : undefined}>
          <AmountInput value={value.ratePerDay ?? ''} onChange={(v) => set({ ratePerDay: v === '' ? undefined : v })} invalid={showErrors && !(value.ratePerDay! > 0)} />
        </Field>
      )}
      {value.rule === 'percent_per_week' && (
        <Field label="Percent per full week" required error={showErrors && !(value.percentPerWeek! > 0) ? 'Enter the agreed percentage.' : undefined}>
          <div className="flex items-center gap-2"><Input type="number" step="0.1" min={0} className="w-28 text-right" value={value.percentPerWeek ?? ''} onChange={(e) => set({ percentPerWeek: e.target.value === '' ? undefined : Number(e.target.value) })} aria-invalid={(showErrors && !(value.percentPerWeek! > 0)) || undefined} /><span className="text-muted-foreground text-sm">% of contract value</span></div>
        </Field>
      )}
      {value.rule === 'fixed_amount' && (
        <Field label="Fixed deduction" required error={showErrors && !(value.fixedAmount! > 0) ? 'Enter the agreed amount.' : undefined}>
          <AmountInput value={value.fixedAmount ?? ''} onChange={(v) => set({ fixedAmount: v === '' ? undefined : v })} invalid={showErrors && !(value.fixedAmount! > 0)} />
        </Field>
      )}
      <Field label="Maximum deduction" optional hint={capPct ? `${capPct}% of the contract value` : 'Leave empty if the agreement sets no cap.'}>
        <AmountInput value={value.maxDeduction ?? ''} onChange={(v) => set({ maxDeduction: v === '' ? undefined : v })} />
      </Field>
      <Field label="Clause reference and notes" optional className="sm:col-span-2">
        <Textarea rows={2} value={value.notes ?? ''} onChange={(e) => set({ notes: e.target.value })} placeholder="e.g. Clause 14.2 — LKR 15,000 per calendar day, capped at 5%." />
      </Field>
    </div>
  )
}
