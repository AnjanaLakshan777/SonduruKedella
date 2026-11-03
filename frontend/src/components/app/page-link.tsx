import { useCallback } from 'react'
import { Link, useNavigate, type LinkProps } from 'react-router-dom'
import { useAuth } from '@/store/auth'
import { canOpenPath } from './nav'

/** Whether the signed-in user may open a link target, plus a navigate that skips pages they can't. */
export function usePageAccess() {
  const user = useAuth((s) => s.user)
  const navigate = useNavigate()
  const can = useCallback((to: string) => canOpenPath(user, to), [user])
  const go = useCallback((to: string) => { if (can(to)) navigate(to) }, [can, navigate])
  return { can, go }
}

/** A Link, or plain text when the target page isn't one of the user's. */
export function PageLink({ to, children, className, ...rest }: LinkProps & { to: string }) {
  const { can } = usePageAccess()
  if (!can(to)) return <span className={className?.replace(/\S*(underline|hover:)\S*/g, '').trim() || undefined}>{children}</span>
  return <Link to={to} className={className} {...rest}>{children}</Link>
}
