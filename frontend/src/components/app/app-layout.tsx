import { createContext, useContext, useEffect, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Sheet, SheetContent, SheetTitle } from '@/components/ui/sheet'
import { useData } from '@/store/store'
import { AppSidebar } from './app-sidebar'
import { Topbar, type Crumb } from './topbar'

const CrumbCtx = createContext<(c: Crumb[]) => void>(() => {})
/** Pages call this to set the breadcrumb in the top bar. */
export function useCrumbs(crumbs: Crumb[]) {
  const set = useContext(CrumbCtx)
  const key = JSON.stringify(crumbs)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => set(crumbs), [key])
}

export function AppLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const [mobile, setMobile] = useState(false)
  const [crumbs, setCrumbs] = useState<Crumb[]>([{ label: 'Dashboard' }])
  const { pathname } = useLocation()
  const d = useData()
  useEffect(() => { window.scrollTo(0, 0) }, [pathname])
  const overdueClients = new Set(d.installments.filter((i) => i.status === 'Overdue').map((i) => i.client.id)).size
  const lowStock = d.materials.filter((m) => m.status !== 'In Stock').length
  const counts = { '/clients': { n: overdueClients, alert: true }, '/inventory': { n: lowStock } }

  function toggle() {
    if (window.matchMedia('(max-width: 767px)').matches) setMobile(true)
    else setCollapsed((c) => !c)
  }
  return (
    <CrumbCtx.Provider value={setCrumbs}>
      <div className="flex min-h-svh">
        <div className="sticky top-0 hidden h-svh shrink-0 md:block"><AppSidebar collapsed={collapsed} counts={counts} /></div>
        <Sheet open={mobile} onOpenChange={setMobile}>
          <SheetContent side="left" className="w-64 p-0 [&>button]:hidden">
            <SheetTitle className="sr-only">Navigation</SheetTitle>
            <AppSidebar counts={counts} onNavigate={() => setMobile(false)} />
          </SheetContent>
        </Sheet>
        <div className="flex min-w-0 flex-1 flex-col">
          <Topbar crumbs={crumbs} onToggleNav={toggle} />
          <main className="mx-auto grid w-full max-w-[1440px] min-w-0 content-start gap-6 p-4 sm:p-6">
            <Outlet />
          </main>
        </div>
      </div>
    </CrumbCtx.Provider>
  )
}
