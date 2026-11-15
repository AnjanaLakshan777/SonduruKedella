import { useEffect, useState } from 'react'
import { LoaderCircle, ServerCrash } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/app/empty-state'
import LoginPage from '@/pages/login'
import FirstPasswordPage from '@/pages/first-password'
import { useAuth } from '@/store/auth'
import { useStore } from '@/store/store'

function Splash({ text }: { text: string }) {
  return (
    <div className="text-muted-foreground grid min-h-svh place-items-center">
      <span className="flex items-center gap-2 text-sm"><LoaderCircle className="size-4 animate-spin" />{text}</span>
    </div>
  )
}

// Nothing renders until we know who's signed in and the data is loaded - the pages assume a full store.
export function SessionGate({ children }: { children: React.ReactNode }) {
  const { status, check, user } = useAuth()
  // a new user sets their own password before anything loads (the API refuses the rest anyway)
  const mustChange = status === 'signedIn' && !!user?.mustChangePassword
  const loaded = useStore((s) => s.loaded)
  const load = useStore((s) => s.load)
  const [error, setError] = useState<string>()

  useEffect(() => {
    check().catch(() => setError("Can't connect right now. Check your connection and try again."))
  }, [check])

  useEffect(() => {
    if (status !== 'signedIn' || mustChange || loaded) return
    setError(undefined)
    load().catch((e) => setError(e instanceof Error ? e.message : String(e)))
  }, [status, mustChange, loaded, load])

  if (error) {
    return (
      <div className="grid min-h-svh place-items-center p-4">
        <EmptyState icon={ServerCrash} title="Couldn't load the data" description={error}
          action={<Button onClick={() => window.location.reload()}>Try again</Button>} />
      </div>
    )
  }
  if (status === 'checking') return <Splash text="Checking your session…" />
  if (status === 'signedOut') return <LoginPage />
  if (mustChange) return <FirstPasswordPage />
  if (!loaded) return <Splash text="Loading your data…" />
  return <>{children}</>
}
