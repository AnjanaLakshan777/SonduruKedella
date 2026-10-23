import { useNavigate, useParams } from 'react-router-dom'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useCrumbs } from '@/components/app/app-layout'
import { PageHeader } from '@/components/app/page-header'
import { Employees } from './employees'
import { AttendanceView } from './attendance'
import { OvertimeView } from './overtime'
import { AdvancesView } from './advances'
import { PayrollView } from './payroll'

const TABS = [
  { id: 'employees', label: 'Employees' },
  { id: 'attendance', label: 'Daily attendance' },
  { id: 'overtime', label: 'Overtime' },
  { id: 'advances', label: 'Salary advances' },
  { id: 'payroll', label: 'Weekly payroll' },
]

export default function LabourPage() {
  const { tab = 'employees' } = useParams()
  const navigate = useNavigate()
  const current = TABS.find((t) => t.id === tab) ?? TABS[0]
  useCrumbs([{ label: 'Labour & Payroll', to: '/labour' }, ...(current.id !== 'employees' ? [{ label: current.label }] : [])])
  return (
    <>
      <PageHeader title="Labour & Payroll" description="Daily-wage workers: attendance, overtime, advances and the weekly pay run. Wages paid each Saturday." />
      <Tabs value={current.id} onValueChange={(t) => navigate(t === 'employees' ? '/labour' : `/labour/${t}`)}>
        <TabsList variant="line" aria-label="Labour sections">
          {TABS.map((t) => <TabsTrigger key={t.id} value={t.id}>{t.label}</TabsTrigger>)}
        </TabsList>
      </Tabs>
      {current.id === 'employees' && <Employees />}
      {current.id === 'attendance' && <AttendanceView />}
      {current.id === 'overtime' && <OvertimeView />}
      {current.id === 'advances' && <AdvancesView />}
      {current.id === 'payroll' && <PayrollView />}
    </>
  )
}
