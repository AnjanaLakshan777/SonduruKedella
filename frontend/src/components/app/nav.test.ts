import { describe, expect, it } from 'vitest'
import type { CurrentUser } from '@/store/auth'
import { canOpen, canOpenPath, homeOf, pageOf } from './nav'

const user = (pages: CurrentUser['pages']): CurrentUser => ({
  id: 1, username: 'x', displayName: 'X', email: 'x@example.com', role: 'CUSTOM', roleName: 'Supervisor', pages, readAreas: [],
})

describe('page permissions in the UI', () => {
  it('finds the page a link goes to', () => {
    expect(pageOf('/')).toBe('DASHBOARD')
    expect(pageOf('/projects/12')).toBe('PROJECTS')
    expect(pageOf('/labour/overtime')).toBe('LABOUR')
    expect(pageOf('/suppliers/invoices/new')).toBe('SUPPLIERS')
    // Users checks itself; unknown paths go to the not-found page
    expect(pageOf('/users')).toBeUndefined()
    expect(pageOf('/nowhere')).toBeUndefined()
  })

  it('opens only the ticked pages and sends the user to the first one', () => {
    const supervisor = user(['LABOUR'])
    expect(canOpen(supervisor, 'LABOUR')).toBe(true)
    expect(canOpen(supervisor, 'PROJECTS')).toBe(false)
    expect(canOpen(supervisor, undefined)).toBe(true)
    expect(homeOf(supervisor)).toBe('/labour')
    expect(homeOf(user(['REPORTS', 'SETTINGS']))).toBe('/reports')
    expect(canOpen(undefined, 'DASHBOARD')).toBe(false)
  })

  it('keeps Users for Super Admins, so another user lands on their own first page', () => {
    const auditor = user(['REPORTS'])
    expect(canOpenPath(auditor, '/users')).toBe(false)
    expect(canOpenPath({ ...auditor, role: 'SUPER_ADMIN' }, '/users')).toBe(true)
    expect(canOpenPath(auditor, '/reports/income?x=1')).toBe(true)
    expect(canOpenPath(auditor, '/')).toBe(false)
    expect(homeOf(auditor)).toBe('/reports')
  })
})
