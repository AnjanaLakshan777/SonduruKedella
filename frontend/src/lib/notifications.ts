import { addDays, formatDate, TODAY } from './dates'
import { billingLate, type Derived } from './finance'
import { formatLKR } from './format'

export type NoticeKind = 'overdue' | 'billing-late' | 'payable' | 'overtime'

export interface Notice {
  /** stays the same while the cause lasts, so "read" sticks until something new happens */
  key: string
  kind: NoticeKind
  /** the day this became worth a look; the list is newest first by this */
  date: string
  title: string
  detail: string
  to: string
}

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`

// Everything the bell shows, newest first. Same-day ties keep the order below: money owed first.
export function notifications(d: Derived): Notice[] {
  const out: Notice[] = []
  for (const i of d.installments.filter((x) => x.status === 'Overdue')) {
    const billedOn = i.inst.billedOn ?? i.inst.dueDate
    out.push({
      key: `overdue:${i.inst.id}`, kind: 'overdue', date: billedOn,
      title: `${i.client.name} — ${formatLKR(i.remaining)} overdue`,
      detail: `${i.inst.description} · ${i.daysOverdue ? `${plural(i.daysOverdue, 'day')} since billed on ${formatDate(billedOn)}` : 'billed today'}`,
      to: `/projects/${i.project.id}?tab=payments`,
    })
  }
  for (const i of billingLate(d.installments)) {
    out.push({
      // late from the day after the planned date
      key: `billing-late:${i.inst.id}`, kind: 'billing-late', date: addDays(i.inst.dueDate, 1),
      title: `${i.project.name} — ${i.inst.description} not billed`,
      detail: `${formatLKR(i.inst.amount)} · planned ${formatDate(i.inst.dueDate)}, ${plural(i.daysLate, 'day')} ago`,
      to: `/projects/${i.project.id}?tab=payments`,
    })
  }
  for (const p of d.payablesDue.filter((x) => x.status !== 'Overdue' && x.dueDate <= addDays(TODAY, 3))) {
    out.push({
      // shows up three days before it's due
      key: `payable:${p.id}:${p.dueDate}`, kind: 'payable', date: addDays(p.dueDate, -3),
      title: `${p.payee} — ${formatLKR(p.amount)} due ${formatDate(p.dueDate)}`,
      detail: `${p.kind} ${p.ref}`,
      to: '/suppliers?tab=payables',
    })
  }
  const ot = d.s.overtime.filter((o) => !o.approved)
  if (ot.length) {
    const latest = ot.reduce((a, b) => (b.date > a.date ? b : a))
    out.push({
      // a new entry makes a new key, so it comes back as unread
      key: `overtime:${ot.map((o) => o.id).sort().join(',')}`, kind: 'overtime', date: latest.date,
      title: `${ot.length} overtime ${ot.length === 1 ? 'entry' : 'entries'} awaiting approval`,
      detail: 'Recorded by the site supervisor',
      to: '/labour/overtime',
    })
  }
  // stable sort, so same-day items keep the order they were added in
  return out.sort((a, b) => b.date.localeCompare(a.date))
}
