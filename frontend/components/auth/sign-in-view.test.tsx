import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SignInView } from '@/components/auth/sign-in-view'
import { getSafeReturnPath } from '@/lib/auth-api'

function renderSignIn(oauthError = false) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({}) }),
  )

  return render(
    <QueryClientProvider client={queryClient}>
      <SignInView oauthError={oauthError} returnPath="/assets" />
    </QueryClientProvider>,
  )
}

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
})
