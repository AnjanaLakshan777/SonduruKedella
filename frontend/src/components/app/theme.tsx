import { createContext, useContext, useEffect, useState } from 'react'

type Theme = 'light' | 'dark'
const Ctx = createContext<{ theme: Theme; setTheme: (t: Theme) => void }>({ theme: 'light', setTheme: () => {} })

function initial(): Theme {
  // a host that embeds the app (e.g. the Claude artifact viewer) may set data-theme on <html>
  const host = document.documentElement.getAttribute('data-theme')
  if (host === 'light' || host === 'dark') return host
  try {
    const saved = localStorage.getItem('sk-theme')
    if (saved === 'light' || saved === 'dark') return saved
  } catch { /* storage unavailable */ }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setTheme] = useState<Theme>(initial)
  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
    try { localStorage.setItem('sk-theme', theme) } catch { /* ignore */ }
  }, [theme])
  return <Ctx.Provider value={{ theme, setTheme }}>{children}</Ctx.Provider>
}

export const useTheme = () => useContext(Ctx)
