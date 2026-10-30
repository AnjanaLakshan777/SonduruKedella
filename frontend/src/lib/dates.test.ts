import { describe, expect, it } from 'vitest'
import { addMonths, fyLabel, monthEnd, monthStart } from './dates'

describe('month and financial-year helpers', () => {
  it('finds the first and last day of a month, leap years included', () => {
    expect(monthStart('2026-10')).toBe('2026-10-01')
    expect(monthEnd('2026-10')).toBe('2026-10-31')
    expect(monthEnd('2026-11')).toBe('2026-11-30')
    expect(monthEnd('2028-02')).toBe('2028-02-29')
    expect(monthEnd('2027-02')).toBe('2027-02-28')
  })

  it('moves months across year ends both ways', () => {
    expect(addMonths('2026-04', 11)).toBe('2027-03')
    expect(addMonths('2026-10', -2)).toBe('2026-08')
    expect(addMonths('2027-01', -1)).toBe('2026-12')
  })

  it('writes the financial year the way accountants do', () => {
    expect(fyLabel('2026-04-01')).toBe('2026/27')
    expect(fyLabel('2099-04-01')).toBe('2099/00')
  })
})
