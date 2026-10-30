import { HashRouter, Route, Routes } from 'react-router-dom'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from '@/components/ui/sonner'
import { ThemeProvider } from '@/components/app/theme'
import { AppLayout } from '@/components/app/app-layout'
import { SessionGate } from '@/components/app/session-gate'
import Dashboard from '@/pages/dashboard'
import ProjectsList from '@/pages/projects/projects-list'
import CreateProject from '@/pages/projects/create-project'
import ProjectDetail from '@/pages/projects/project-detail'
import ClientsPage from '@/pages/clients/clients-page'
import ClientDetail from '@/pages/clients/client-detail'
import RevenuePage from '@/pages/revenue'
import ExpensesPage from '@/pages/expenses'
import SuppliersPage from '@/pages/suppliers/suppliers-page'
import SupplierDetail from '@/pages/suppliers/supplier-detail'
import SupplierInvoiceForm from '@/pages/suppliers/supplier-invoice-form'
import InventoryPage from '@/pages/inventory'
import LabourPage from '@/pages/labour/labour-page'
import CommissionsPage from '@/pages/commissions'
import ReportsPage from '@/pages/reports/reports-page'
import SettingsPage from '@/pages/settings'
import UsersPage from '@/pages/users'
import NotFound from '@/pages/not-found'

export default function App() {
  return (
    <ThemeProvider>
      <TooltipProvider>
        <SessionGate>
          <HashRouter>
            <Routes>
              <Route element={<AppLayout />}>
                <Route index element={<Dashboard />} />
                <Route path="projects" element={<ProjectsList />} />
                <Route path="projects/new" element={<CreateProject />} />
                <Route path="projects/:id" element={<ProjectDetail />} />
                <Route path="clients" element={<ClientsPage />} />
                <Route path="clients/:id" element={<ClientDetail />} />
                <Route path="revenue" element={<RevenuePage />} />
                <Route path="expenses" element={<ExpensesPage />} />
                <Route path="suppliers" element={<SuppliersPage />} />
                <Route path="suppliers/invoices/new" element={<SupplierInvoiceForm />} />
                <Route path="suppliers/:id" element={<SupplierDetail />} />
                <Route path="inventory" element={<InventoryPage />} />
                <Route path="labour" element={<LabourPage />} />
                <Route path="labour/:tab" element={<LabourPage />} />
                <Route path="commissions" element={<CommissionsPage />} />
                <Route path="reports" element={<ReportsPage />} />
                <Route path="reports/:report" element={<ReportsPage />} />
                <Route path="settings" element={<SettingsPage />} />
                <Route path="users" element={<UsersPage />} />
                <Route path="*" element={<NotFound />} />
              </Route>
            </Routes>
          </HashRouter>
        </SessionGate>
        <Toaster position="bottom-right" />
      </TooltipProvider>
    </ThemeProvider>
  )
}
