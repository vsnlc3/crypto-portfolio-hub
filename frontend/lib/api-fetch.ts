import { demoActivities, demoAssets, demoConnections, demoHistory, demoPortfolioSummary, demoPositions, demoUser } from './demo-fixtures'
import { disableDemoMode, isDemoMode } from './demo-mode'

export async function apiFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  if (!isDemoMode()) return fetch(input, init)

  const url = new URL(typeof input === 'string' || input instanceof URL ? String(input) : input.url, window.location.origin)
  const method = (init?.method ?? (input instanceof Request ? input.method : 'GET')).toUpperCase()
  const path = url.pathname

  if (method === 'GET' && path === '/api/v1/auth/me') return jsonResponse(200, demoUser)
  if (method === 'GET' && path === '/api/v1/auth/csrf') {
    return jsonResponse(200, { headerName: 'X-DEMO-CSRF', token: 'demo-read-only' })
  }
  if (method === 'POST' && path === '/api/v1/auth/logout') {
    disableDemoMode()
    return new Response(null, { status: 204 })
  }
  if (method !== 'GET') return problemResponse(403, 'DEMO_READ_ONLY')
  if (path === '/api/v1/connections') return jsonResponse(200, demoConnections)
  if (path === '/api/v1/portfolio/summary') return jsonResponse(200, demoPortfolioSummary)
  if (path === '/api/v1/portfolio/history') {
    const period = url.searchParams.get('period')
    if (period === '7D' || period === '30D' || period === '90D' || period === '1Y') {
      return jsonResponse(200, demoHistory(period))
    }
    return problemResponse(400, 'VALIDATION_ERROR')
  }
  if (path === '/api/v1/assets') return jsonResponse(200, demoAssets)
  if (path === '/api/v1/positions') return jsonResponse(200, demoPositions)
  if (path === '/api/v1/activities') return jsonResponse(200, demoActivities)
  return problemResponse(404, 'RESOURCE_NOT_FOUND')
}

function jsonResponse(status: number, value: unknown): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function problemResponse(status: number, code: string): Response {
  return jsonResponse(status, { status, code, title: code })
}
