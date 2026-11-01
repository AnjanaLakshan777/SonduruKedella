import { describe, expect, it } from 'vitest'
import { revenueAfterNote } from './format'

describe('revenueAfterNote', () => {
  it('names delay deductions and write-offs as what came off revenue', () => {
    expect(revenueAfterNote(150000, 30000)).toBe('After 150,000 delay deduction · 30,000 written off')
    expect(revenueAfterNote(150000, 0)).toBe('After 150,000 delay deduction')
    expect(revenueAfterNote(0, 30000)).toBe('After 30,000 written off')
    expect(revenueAfterNote(0, 0)).toBeUndefined()
  })
})
