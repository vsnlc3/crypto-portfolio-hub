import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SignInView } from '@/components/auth/sign-in-view'
import { authQueryKey, getSafeReturnPath } from '@/lib/auth-api'
import { demoUser } from '@/lib/demo-fixtures'
import { disableDemoMode, isDemoMode } from '@/lib/demo-mode'

const navigation = vi.hoisted(() => ({ replace: vi.fn() }))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: navigation.replace }),
}))

function renderSignIn(oauthError = false) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({}) }),
  )

  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <SignInView oauthError={oauthError} returnPath="/assets" />
      </QueryClientProvider>,
    ),
  }
}

afterEach(() => {
  cleanup()
  disableDemoMode()
  navigation.replace.mockReset()
  vi.unstubAllGlobals()
})

describe('SignInView', () => {
  it('offers Google as the only sign-in method and reports OAuth failure safely', async () => {
    renderSignIn(true)

    expect(await screen.findByRole('heading', { name: 'Sign in to continue' })).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Continue with Google' })).toBeEnabled())
    expect(screen.getByRole('alert')).toHaveTextContent('Google sign-in did not complete')
    expect(screen.queryByRole('button', { name: /email|password/i })).not.toBeInTheDocument()
  })

  it('accepts only same-origin return paths', () => {
    expect(getSafeReturnPath('/activity?type=swap')).toBe('/activity?type=swap')
    expect(getSafeReturnPath('//malicious.example/path')).toBeNull()
    expect(getSafeReturnPath('/signin')).toBeNull()
  })

  it('enters the fixture-only demo without requesting an authenticated session', async () => {
    const { queryClient } = renderSignIn()
    await waitFor(() => expect(screen.getByRole('button', { name: 'View read-only demo' })).toBeEnabled())
    const fetchMock = vi.mocked(fetch)

    fireEvent.click(screen.getByRole('button', { name: 'View read-only demo' }))

    expect(isDemoMode()).toBe(true)
    expect(queryClient.getQueryData(authQueryKey)).toEqual(demoUser)
    expect(navigation.replace).toHaveBeenCalledWith('/assets')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
