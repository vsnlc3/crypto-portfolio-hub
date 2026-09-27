export type AuthenticatedUser = {
  id: string
  email: string
  displayName: string | null
  avatarUrl: string | null
}

type CsrfTokenResponse = {
  headerName: string
  token: string
}

export class AuthApiError extends Error {
  constructor(readonly status: number) {
    super(`Authentication request failed with status ${status}`)
    this.name = 'AuthApiError'
  }
}

export const authQueryKey = ['auth', 'me'] as const

export async function getCurrentUser(): Promise<AuthenticatedUser | null> {
  const response = await fetch('/api/v1/auth/me', {
    cache: 'no-store',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })

  if (response.status === 401) return null
  if (!response.ok) throw new AuthApiError(response.status)

  return (await response.json()) as AuthenticatedUser
}

export async function logout(): Promise<void> {
  const csrfResponse = await fetch('/api/v1/auth/csrf', {
    cache: 'no-store',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })

  if (!csrfResponse.ok) throw new AuthApiError(csrfResponse.status)

  const csrf = (await csrfResponse.json()) as CsrfTokenResponse
  if (!csrf.headerName || !csrf.token) throw new AuthApiError(500)

  const response = await fetch('/api/v1/auth/logout', {
    method: 'POST',
    cache: 'no-store',
    credentials: 'same-origin',
    headers: { [csrf.headerName]: csrf.token },
  })

  if (response.status !== 204) throw new AuthApiError(response.status)
}

export function getSafeReturnPath(value: string | null | undefined): string | null {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) {
    return null
  }

  try {
    const target = new URL(value, 'http://localhost')
    if (target.origin !== 'http://localhost' || target.pathname === '/signin') return null
    return `${target.pathname}${target.search}${target.hash}`
  } catch {
    return null
  }
}

export const authReturnPathStorageKey = 'crypto-portfolio-hub:return-path'
