import { useState } from 'react'
import { KeyRound, LoaderCircle } from 'lucide-react'
import logo from '@/assets/sonduru-kedella-logo.png'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field } from '@/components/app/form'
import { PasswordInput } from '@/components/app/password-input'
import { passwordChangeErrors } from '@/components/app/change-password-dialog'
import { apiPut, errorMessage } from '@/lib/api'
import { useAuth } from '@/store/auth'

/** First sign-in of a user the Super Admin added: they pick their own password before anything else. */
export default function FirstPasswordPage() {
  const { user, check, logout } = useAuth()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [again, setAgain] = useState('')
  const [tried, setTried] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const err = passwordChangeErrors(current, next, again)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setTried(true)
    if (Object.values(err).some(Boolean)) return
    setBusy(true)
    setError(undefined)
    try {
      await apiPut('/auth/me/password', { currentPassword: current, newPassword: next })
      // the flag is gone now; SessionGate carries on and loads the data
      await check()
    } catch (e) {
      // a wrong starting password counts toward the sign-in limit, the server says when to try again
      setError(errorMessage(e))
      setBusy(false)
    }
  }

  return (
    <div className="bg-background grid min-h-svh place-items-center p-4">
      <div className="grid w-full max-w-sm gap-6">
        <img src={logo} alt="Sonduru Kedella — Home Design & Construction" className="mx-auto h-20 w-auto" />
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><KeyRound className="size-4" />Set your own password</CardTitle>
            <CardDescription>Welcome{user ? `, ${user.displayName}` : ''}. This is your first sign-in, so replace the password you were given with one only you know.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={submit} className="grid gap-4" noValidate>
              <Field label="Password you were given" required error={tried ? err.current : undefined}>
                <PasswordInput autoComplete="current-password" autoFocus value={current} onChange={(e) => setCurrent(e.target.value)} disabled={busy} />
              </Field>
              <Field label="New password" required hint="At least 12 characters, not one you use anywhere else." error={tried ? err.next : undefined}>
                <PasswordInput autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} disabled={busy} />
              </Field>
              <Field label="New password again" required error={tried ? err.again : undefined}>
                <PasswordInput autoComplete="new-password" value={again} onChange={(e) => setAgain(e.target.value)} disabled={busy} />
              </Field>
              {error && <p className="text-destructive text-sm" role="alert">{error}</p>}
              <Button type="submit" disabled={busy}>{busy && <LoaderCircle className="animate-spin" />}Save and continue</Button>
              <Button type="button" variant="ghost" disabled={busy} onClick={() => logout()}>Sign out</Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
