import { afterEach, describe, expect, it, vi } from 'vitest'
import { GET } from '@/app/auth/google/route'

describe('GET /auth/google', () => {
  afterEach(() => vi.unstubAllEnvs())

  it('redirects the browser to the configured Backend authorization route', () => {
    vi.stubEnv(
      'GOOGLE_OAUTH_AUTHORIZATION_URL',
      'http://localhost:8080/oauth2/authorization/google',
    )

    const response = GET()

    expect(response.status).toBe(307)
    expect(response.headers.get('location')).toBe(
      'http://localhost:8080/oauth2/authorization/google',
    )
  })
})
