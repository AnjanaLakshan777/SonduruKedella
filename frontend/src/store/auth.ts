import { create } from 'zustand'
import { ApiError, apiGet, apiPost, ensureCsrf, setUnauthorizedHandler } from '@/lib/api'

export interface CurrentUser {
  id: number
  username: string
  displayName: string
  email: string
  role: 'SUPER_ADMIN' | 'ADMIN'
}

interface AuthState {
  status: 'checking' | 'signedOut' | 'signedIn'
  user?: CurrentUser
  check: () => Promise<void>
  login: (usernameOrEmail: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

export const useAuth = create<AuthState>()((set) => ({
  status: 'checking',
  check: async () => {
    try {
      set({ status: 'signedIn', user: await apiGet<CurrentUser>('/auth/me') })
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) set({ status: 'signedOut', user: undefined })
      else throw e
    }
  },
  login: async (usernameOrEmail, password) => {
    await ensureCsrf()
    const user = await apiPost<CurrentUser>('/auth/login', { username: usernameOrEmail, password })
    // the session id changed, and so did the CSRF token
    await apiGet('/auth/csrf')
    set({ status: 'signedIn', user })
  },
  logout: async () => {
    try {
      await apiPost('/auth/logout')
    } finally {
      set({ status: 'signedOut', user: undefined })
    }
  },
}))

setUnauthorizedHandler(() => {
  if (useAuth.getState().status === 'signedIn') useAuth.setState({ status: 'signedOut', user: undefined })
})
