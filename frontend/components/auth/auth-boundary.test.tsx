import { QueryClient, QueryClientProvider, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthBoundary } from '@/components/auth/auth-boundary'
import { authQueryKey } from '@/lib/auth-api'

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

function CacheSeeder() {
  const queryClient = useQueryClient()
  useEffect(() => {
    queryClient.setQueryData(['portfolio', 'summary'], { summary: { netWorthJpy: 123 } })
    queryClient.setQueryData(['assets'], { assets: [{ assetId: 'user-a-asset' }] })
  }, [queryClient])
  return null
}

function renderBoundary(children: React.ReactNode = <div>Private portfolio page</div>) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <AuthBoundary>
          {children}
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
    cleanup()
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
    const fetchMock = vi.fn((input: string | URL | Request) => {
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
      fetchMock.mock.calls.map(([input]) => new URL(String(input), 'http://localhost').pathname),
    ).toEqual(['/api/v1/auth/me', '/api/v1/auth/csrf', '/api/v1/auth/logout'])
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

    const { queryClient } = renderBoundary(<><div>Private portfolio page</div><CacheSeeder /></>)

    expect(await screen.findByText('Portfolio User')).toBeInTheDocument()
    expect(screen.getByText('Private portfolio page')).toBeInTheDocument()
    await waitFor(() => expect(queryClient.getQueryData(['portfolio', 'summary'])).toMatchObject({ summary: { netWorthJpy: 123 } }))
    await waitFor(() => expect(queryClient.getQueryData(['assets'])).toMatchObject({ assets: [{ assetId: 'user-a-asset' }] }))
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith('/signin?reason=logged-out'))
    expect(queryClient.getQueryData(['portfolio', 'summary'])).toBeUndefined()
    expect(queryClient.getQueryData(['assets'])).toBeUndefined()
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

  it('clears user-owned query data when the authenticated user changes', async () => {
    const firstUser = {
      id: 'user-a',
      email: 'a@example.test',
      displayName: 'User A',
      avatarUrl: null,
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, firstUser)))
    const { queryClient } = renderBoundary()

    expect(await screen.findByText('User A')).toBeInTheDocument()
    queryClient.setQueryData(['portfolio', 'summary'], { summary: { netWorthJpy: 123 } })
    queryClient.setQueryData(['assets'], { assets: [{ assetId: 'user-a-asset' }] })
    queryClient.setQueryData(authQueryKey, {
      id: 'user-b',
      email: 'b@example.test',
      displayName: 'User B',
      avatarUrl: null,
    })

    await waitFor(() => {
      expect(queryClient.getQueryData(['portfolio', 'summary'])).toBeUndefined()
      expect(queryClient.getQueryData(['assets'])).toBeUndefined()
    })
    expect(queryClient.getQueryData(authQueryKey)).toMatchObject({ id: 'user-b' })
  })
})
