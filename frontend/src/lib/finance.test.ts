import { describe, expect, it } from 'vitest'
import { createSeed } from '@/data/seed'
import { derive, projectActivity } from './finance'

describe('project timeline', () => {
  it('lists only the activity log, newest first, ordered within a day by when it was recorded', () => {
    const s = createSeed()
    s.activity = [
      { id: '99', date: '2026-10-30', projectId: 'p1', kind: 'note', text: 'billed', createdAt: '2026-10-30T05:13:09Z' },
      { id: '100', date: '2026-10-30', projectId: 'p1', kind: 'payment', text: 'paid', createdAt: '2026-10-30T05:13:12Z' },
      { id: '7', date: '2026-10-29', projectId: 'p1', kind: 'status', text: 'created' },
      { id: '8', date: '2026-10-30', projectId: 'p2', kind: 'note', text: 'other project' },
    ]
    // the payment was recorded after the billing on the same day, so it comes first
    expect(projectActivity(derive(s), 'p1').map((a) => a.text)).toEqual(['paid', 'billed', 'created'])
  })

  it('breaks a tie on the same moment by the higher id, compared as numbers', () => {
    const s = createSeed()
    s.activity = [
      { id: '99', date: '2026-10-30', projectId: 'p1', kind: 'note', text: 'older', createdAt: '2026-10-30T05:13:09Z' },
      { id: '100', date: '2026-10-30', projectId: 'p1', kind: 'note', text: 'newer', createdAt: '2026-10-30T05:13:09Z' },
    ]
    expect(projectActivity(derive(s), 'p1').map((a) => a.text)).toEqual(['newer', 'older'])
  })
})
