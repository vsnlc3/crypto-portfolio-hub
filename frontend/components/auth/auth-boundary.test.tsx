import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthBoundary } from '@/components/auth/auth-boundary'

const navigation = vi.hoisted(() => ({
  pathname: '/assets',
  replace: vi.fn(),
}))

vi.mock('next/navigation', () => ({
  usePathname: () => navigation.pathname,
  useRouter: () => ({ replace: navigation.replace }),
}))

function jsonResponse(status: number, value: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => value,
  } as Response
}

function emptyResponse(status: number): Response {
  return { ok: status >= 200 && status < 300, status } as Response
}

function renderBoundary() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <AuthBoundary>
          <div>Private portfolio page</div>
        </AuthBoundary>
      </QueryClientProvider>,
    ),
  }
}

describe('AuthBoundary', () => {
  beforeEach(() => {
    navigation.pathname = '/assets'
    navigation.replace.mockReset()
    window.sessionStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('redirects an unauthenticated visitor to sign in without rendering protected content', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(401, {})))

    renderBoundary()

    expect(screen.getByRole('status')).toHaveTextContent('Checking your session')
    await waitFor(() => {
      expect(navigation.replace).toHaveBeenCalledWith('/signin?next=%2Fassets')
    })
    expect(screen.queryByText('Private portfolio page')).not.toBeInTheDocument()
  })

  it('shows a safe retry state when session verification fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(503, {})))

    renderBoundary()

    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t verify your session')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
    expect(screen.queryByText('Private portfolio page')).not.toBeInTheDocument()
  })

  it('keeps the authenticated shell visible and reports a failed logout safely', async () => {
    const user = {
      id: 'user-id',
      email: 'portfolio@example.test',
      displayName: 'Portfolio User',
      avatarUrl: null,
    }
    const fetchMock = vi.fn((input: string | URL | Request, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/auth/me')) return Promise.resolve(jsonResponse(200, user))
      if (url.endsWith('/auth/csrf')) {
        return Promise.resolve(jsonResponse(200, { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' }))
      }
      return Promise.resolve(jsonResponse(403, {}))
    })
    vi.stubGlobal('fetch', fetchMock)

    renderBoundary()
    expect(await screen.findByText('Portfolio User')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByText('Sign out failed. Please try again.')).toBeInTheDocument()
    expect(
      fetchMock.mock.calls.map(([input, init]) => `${new URL(String(input), 'http://localhost').pathname}:${init?.method ?? 'GET'}`),
    ).toEqual(['/api/v1/auth/me:GET', '/api/v1/auth/csrf:GET', '/api/v1/auth/logout:POST'])
    expect(screen.getByText('Private portfolio page')).toBeInTheDocument()
  })

  it('shows the authenticated account and sends a CSRF-protected logout', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse(200, {
          id: 'user-id',
          email: 'portfolio@example.test',
          displayName: 'Portfolio User',
          avatarUrl: null,
        }),
      )
      .mockResolvedValueOnce(jsonResponse(200, { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' }))
      .mockResolvedValueOnce(emptyResponse(204))
      .mockResolvedValue(jsonResponse(401, {}))
    vi.stubGlobal('fetch', fetchMock)

    renderBoundary()

    expect(await screen.findByText('Portfolio User')).toBeInTheDocument()
    expect(screen.getByText('Private portfolio page')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith('/signin?reason=logged-out'))
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/auth/me', expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/auth/csrf', expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      '/api/v1/auth/logout',
      expect.objectContaining({
        method: 'POST',
        headers: { 'X-CSRF-TOKEN': 'test-csrf' },
      }),
    )
  })
})
