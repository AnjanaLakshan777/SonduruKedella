import { BarChart3, FolderKanban, HandCoins, HardHat, LayoutDashboard, Package, Percent, Receipt, Settings, Truck, Users, type LucideIcon } from 'lucide-react'

export interface NavItem { to: string; label: string; icon: LucideIcon }
export const NAV: { label?: string; items: NavItem[] }[] = [
  { items: [{ to: '/', label: 'Dashboard', icon: LayoutDashboard }] },
  { label: 'Projects', items: [{ to: '/projects', label: 'Projects', icon: FolderKanban }, { to: '/clients', label: 'Clients', icon: Users }] },
  { label: 'Finance', items: [
    { to: '/revenue', label: 'Revenue', icon: HandCoins },
    { to: '/expenses', label: 'Expenses', icon: Receipt },
    { to: '/commissions', label: 'Commissions', icon: Percent },
    { to: '/reports', label: 'Reports', icon: BarChart3 },
  ] },
  { label: 'Operations', items: [
    { to: '/suppliers', label: 'Suppliers', icon: Truck },
    { to: '/inventory', label: 'Inventory', icon: Package },
    { to: '/labour', label: 'Labour & Payroll', icon: HardHat },
  ] },
]
export const SETTINGS_ITEM: NavItem = { to: '/settings', label: 'Settings', icon: Settings }
