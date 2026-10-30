// Thin fetch wrapper for the backend. The session lives in an HttpOnly cookie, so every call sends
// credentials; writes also echo the XSRF-TOKEN cookie back as a header (Spring's CSRF check).
const BASE = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1').replace(/\/$/, '')

export interface FieldError {
  field: string
  message: string
}

export class ApiError extends Error {
  constructor(message: string, readonly status: number, readonly fields: FieldError[] = []) {
    super(message)
  }
}

// set by the auth store, so any 401 (session expired, user deactivated) sends us back to sign in
let onUnauthorized = () => {}
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

function xsrfToken() {
  const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : undefined
}

export async function ensureCsrf() {
  if (!xsrfToken()) await fetch(`${BASE}/auth/csrf`, { credentials: 'include' })
}

export async function api<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  let payload: BodyInit | undefined
  if (body instanceof FormData) payload = body
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  if (method !== 'GET') {
    await ensureCsrf()
    const token = xsrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  const res = await fetch(BASE + path, { method, headers, body: payload, credentials: 'include' })
  if (res.status === 401 && !path.startsWith('/auth/login')) onUnauthorized()
  const text = await res.text()
  const json = text ? JSON.parse(text) : undefined
  if (!res.ok) {
    // RFC 9457 problem details from GlobalExceptionHandler / the security layer
    throw new ApiError(json?.detail ?? json?.title ?? `Request failed (${res.status})`, res.status, json?.errors ?? [])
  }
  return json as T
}

export const apiGet = <T>(path: string) => api<T>('GET', path)
export const apiPost = <T>(path: string, body?: unknown) => api<T>('POST', path, body ?? {})
export const apiPut = <T>(path: string, body: unknown) => api<T>('PUT', path, body)

interface Page<T> {
  content: T[]
  totalPages: number
}

// every row of a paged list endpoint (the backend caps a page at 100)
export async function apiGetAll<T>(path: string): Promise<T[]> {
  const sep = path.includes('?') ? '&' : '?'
  const first = await apiGet<Page<T>>(`${path}${sep}size=100&page=0`)
  const rest = await Promise.all(
    Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, i) => apiGet<Page<T>>(`${path}${sep}size=100&page=${i + 1}`)),
  )
  return [first, ...rest].flatMap((p) => p.content)
}

// null instead of an error when the thing just isn't there (an agreement not uploaded yet)
export async function apiGetOptional<T>(path: string): Promise<T | undefined> {
  try {
    return await apiGet<T>(path)
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return undefined
    throw e
  }
}

export const errorMessage = (e: unknown) => (e instanceof Error ? e.message : String(e))

// for plain links (downloads): the browser sends the session cookie itself
export const apiUrl = (path: string) => BASE + path
