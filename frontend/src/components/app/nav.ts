import { BarChart3, FolderKanban, HandCoins, HardHat, LayoutDashboard, Package, Percent, Receipt, Settings, Truck, UserCog, Users, type LucideIcon } from 'lucide-react'
import type { AppPage, CurrentUser } from '@/store/auth'

export interface NavItem { to: string; label: string; icon: LucideIcon; page?: AppPage }
export const NAV: { label?: string; items: NavItem[] }[] = [
  { items: [{ to: '/', label: 'Dashboard', icon: LayoutDashboard, page: 'DASHBOARD' }] },
  { label: 'Projects', items: [{ to: '/projects', label: 'Projects', icon: FolderKanban, page: 'PROJECTS' }, { to: '/clients', label: 'Clients', icon: Users, page: 'CLIENTS' }] },
  { label: 'Finance', items: [
    { to: '/revenue', label: 'Revenue', icon: HandCoins, page: 'REVENUE' },
    { to: '/expenses', label: 'Expenses', icon: Receipt, page: 'EXPENSES' },
    { to: '/commissions', label: 'Commissions', icon: Percent, page: 'COMMISSIONS' },
    { to: '/reports', label: 'Reports', icon: BarChart3, page: 'REPORTS' },
  ] },
  { label: 'Operations', items: [
    { to: '/suppliers', label: 'Suppliers', icon: Truck, page: 'SUPPLIERS' },
    { to: '/inventory', label: 'Inventory', icon: Package, page: 'INVENTORY' },
    { to: '/labour', label: 'Labour & Payroll', icon: HardHat, page: 'LABOUR' },
  ] },
]
export const SETTINGS_ITEM: NavItem = { to: '/settings', label: 'Settings', icon: Settings, page: 'SETTINGS' }
// shown to Super Admins only
export const USERS_ITEM: NavItem = { to: '/users', label: 'Users', icon: UserCog }

// every page a custom role can be given, in sidebar order
export const PAGE_ITEMS: NavItem[] = [...NAV.flatMap((g) => g.items), SETTINGS_ITEM]

export const canOpen = (user: CurrentUser | undefined, page?: AppPage) => !page || !!user?.pages.includes(page)

// a whole path: Users is Super Admin only, every other page goes by the user's pages
export function canOpenPath(user: CurrentUser | undefined, path: string) {
  const bare = path.split(/[?#]/)[0]
  if (bare === USERS_ITEM.to || bare.startsWith(USERS_ITEM.to + '/')) return user?.role === 'SUPER_ADMIN'
  return canOpen(user, pageOf(bare))
}

// the page a path belongs to; undefined for Users (see canOpenPath) and unknown paths
export function pageOf(pathname: string): AppPage | undefined {
  const first = '/' + (pathname.split('/')[1] ?? '')
  return PAGE_ITEMS.find((it) => it.to === first)?.page
}

// where a user lands when the page they asked for isn't theirs
export function homeOf(user: CurrentUser | undefined): string {
  return PAGE_ITEMS.find((it) => canOpen(user, it.page))?.to ?? '/'
}
