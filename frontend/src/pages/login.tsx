import { useState } from 'react'
import { LoaderCircle } from 'lucide-react'
import logo from '@/assets/sonduru-kedella-logo.png'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Field } from '@/components/app/form'
import { ApiError } from '@/lib/api'
import { useAuth } from '@/store/auth'

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
      setError(err instanceof ApiError ? err.message : 'Could not reach the server. Check that the backend is running.')
      setPassword('')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="bg-background grid min-h-svh place-items-center p-4">
      <div className="grid w-full max-w-sm gap-6">
        <img src={logo} alt="Sonduru Kedella — Home Design & Construction" className="mx-auto h-20 w-auto" />
        <Card>
          <CardHeader>
            <CardTitle>Sign in</CardTitle>
            <CardDescription>Use your username or email address.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={submit} className="grid gap-4" noValidate>
              <Field label="Username or email" htmlFor="login-username">
                <Input id="login-username" autoComplete="username" autoFocus value={username} onChange={(e) => setUsername(e.target.value)} disabled={busy} />
              </Field>
              <Field label="Password" htmlFor="login-password" error={error}>
                <Input id="login-password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} disabled={busy} aria-invalid={!!error} />
              </Field>
              <Button type="submit" disabled={busy}>{busy && <LoaderCircle className="animate-spin" />}Sign in</Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
