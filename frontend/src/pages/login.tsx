import { useState } from 'react'
import { FolderKanban, HandCoins, HardHat, LoaderCircle, ShieldCheck } from 'lucide-react'
import logo from '@/assets/sonduru-kedella-logo.png'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Field } from '@/components/app/form'
import { PasswordInput } from '@/components/app/password-input'
import { ApiError } from '@/lib/api'
import { useAuth } from '@/store/auth'

// what the system does, in the owner's words - shown beside the form on wide screens
const POINTS = [
  { icon: FolderKanban, title: 'Profit on every project', text: 'Billed installments, costs and commission side by side, so each site shows what it really earns.' },
  { icon: HandCoins, title: 'Every rupee counted once', text: 'Payments, stock issued to sites and wages are each recorded once, never twice.' },
  { icon: HardHat, title: 'Payroll from attendance', text: 'Daily attendance and approved overtime become the weekly pay, with advances deducted.' },
]

export default function LoginPage() {
  const login = useAuth((s) => s.login)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!username.trim() || !password) {
      setError('Enter your username or email, and your password.')
      return
    }
    setBusy(true)
    setError(undefined)
    try {
      await login(username.trim(), password)
    } catch (err) {
      // 401 and 429 already come with a message meant for people
      setError(err instanceof ApiError ? err.message : "Can't connect right now. Check your connection and try again.")
      setPassword('')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="bg-background grid min-h-svh lg:grid-cols-2">
      <aside className="bg-sidebar text-sidebar-foreground border-sidebar-border hidden flex-col justify-between border-r p-10 lg:flex">
        <img src={logo} alt="Sonduru Kedella — Home Design & Construction" className="h-24 w-auto self-center" />
        <div className="grid max-w-md gap-8">
          <h1 className="text-2xl leading-snug font-semibold tracking-tight">Every project&apos;s money,<br />in one place.</h1>
          <ul className="grid gap-5">
            {POINTS.map((p) => (
              <li key={p.title} className="flex gap-3">
                <p.icon className="text-primary-text mt-0.5 size-4 shrink-0" />
                <span className="grid gap-0.5">
                  <span className="text-sm font-medium">{p.title}</span>
                  <span className="text-sidebar-foreground/70 text-sm leading-relaxed">{p.text}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
        <p className="text-sidebar-foreground/60 text-xs">© {new Date().getFullYear()} Sonduru Kedella · Anuradhapura, Sri Lanka</p>
      </aside>

      <main className="grid place-items-center p-6">
        <div className="grid w-full max-w-sm gap-6">
          {/* no side panel on small screens, so the logo goes above the form */}
          <img src={logo} alt="Sonduru Kedella — Home Design & Construction" className="mx-auto h-20 w-auto lg:hidden" />
          <div className="grid gap-1.5">
            <h2 className="text-xl font-semibold tracking-tight">Sign in</h2>
            <p className="text-muted-foreground text-sm">Use the username or email your administrator gave you.</p>
          </div>
          <form onSubmit={submit} className="grid gap-4" noValidate>
            <Field label="Username or email" htmlFor="login-username">
              <Input id="login-username" autoComplete="username" autoFocus value={username} onChange={(e) => setUsername(e.target.value)} disabled={busy} />
            </Field>
            <Field label="Password" htmlFor="login-password" error={error}>
              <PasswordInput id="login-password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} disabled={busy} aria-invalid={!!error} />
            </Field>
            <Button type="submit" disabled={busy}>{busy && <LoaderCircle className="animate-spin" />}Sign in</Button>
          </form>
          <p className="text-muted-foreground flex items-start gap-2 border-t pt-4 text-xs leading-relaxed">
            <ShieldCheck className="mt-0.5 size-3.5 shrink-0" />
            You are signed out after a period of inactivity. Your password is never stored in the browser.
          </p>
        </div>
      </main>
    </div>
  )
}
