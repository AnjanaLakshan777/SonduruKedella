import { Link } from 'react-router-dom'
import { MapPinOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/app/empty-state'
import { useCrumbs } from '@/components/app/app-layout'

export default function NotFound() {
  useCrumbs([{ label: 'Not found' }])
  return <EmptyState icon={MapPinOff} title="This page doesn't exist" description="The link may be out of date. Go back to the dashboard to continue." action={<Button asChild><Link to="/">Go to Dashboard</Link></Button>} />
}
