import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PositionsTable } from '@/components/dashboard/positions-table'
import type { PositionsResponse } from '@/lib/positions-api'

const positionResponse: PositionsResponse = {
  summary: {
    positionValueJpy: 30000,
    marginJpy: 420,
    unrealizedPnlJpy: -520,
    status: 'COMPLETE',
    connectionCount: 1,
    syncedConnectionCount: 1,
  },
  positions: [{
    positionKey: 'DEFAULT:BTC',
    instrumentCode: 'BTC',
    side: 'LONG',
    leverage: 2,
    quantity: 2,
    entryPrice: 90,
    markPrice: 100,
    liquidationPrice: 50,
    priceCurrency: 'USD',
    positionValueJpy: 30000,
    marginAmount: 3,
    marginCurrency: 'USDC',
    marginJpy: 420,
    unrealizedPnl: -4,
    pnlCurrency: 'USDT',
    unrealizedPnlJpy: -520,
    priceFx: { currency: 'USD', rateToJpy: 150, source: 'EXCHANGERATE_API', evaluatedAt: '2026-09-28T03:00:00Z', status: 'COMPLETE' },
    marginFx: { currency: 'USDC', rateToJpy: 140, source: 'COINGECKO_AND_EXCHANGERATE_API', evaluatedAt: '2026-09-28T03:00:00Z', status: 'COMPLETE' },
    pnlFx: { currency: 'USDT', rateToJpy: 130, source: 'COINGECKO_AND_EXCHANGERATE_API', evaluatedAt: '2026-09-28T03:00:00Z', status: 'COMPLETE' },
    status: 'COMPLETE',
    connectionId: 'connection-1',
    provider: 'HYPERLIQUID',
    connectionDisplayName: 'Hyperliquid Main',
    fetchedAt: '2026-09-28T03:00:00Z',
    lastSuccessAt: '2026-09-28T03:00:00Z',
  }],
}

function renderPositions() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(<QueryClientProvider client={queryClient}><PositionsTable /></QueryClientProvider>)
}

function respond(body: PositionsResponse, status = 200) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: status >= 200 && status < 300, status, json: async () => body }))
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('PositionsTable', () => {
  it('shows JPY totals and raw position values with their own currencies and FX metadata', async () => {
    respond(positionResponse)
    renderPositions()

    expect(await screen.findByText('BTC')).toBeInTheDocument()
    expect(screen.getAllByText('¥30,000')).toHaveLength(2)
    expect(screen.getAllByText('¥420')).toHaveLength(2)
    expect(screen.getByText('-4 USDT')).toBeInTheDocument()
    expect(screen.getAllByText('-¥520')).toHaveLength(2)
    expect(screen.getByText('$90.00')).toBeInTheDocument()
    expect(screen.getByText('$100.00')).toBeInTheDocument()
    expect(screen.getByText('$50.00')).toBeInTheDocument()
    expect(screen.getByText('3 USDC')).toBeInTheDocument()
    expect(screen.getAllByText(/CoinGecko \+ ExchangeRate API/)).toHaveLength(2)
    expect(screen.getByText('Hyperliquid Main')).toBeInTheDocument()
  })

  it('renders unavailable JPY conversions as unavailable instead of zero', async () => {
    const unavailable: PositionsResponse = {
      ...positionResponse,
      summary: { ...positionResponse.summary, positionValueJpy: null, status: 'PARTIAL' },
      positions: [{
        ...positionResponse.positions[0],
        positionValueJpy: null,
        priceFx: { ...positionResponse.positions[0].priceFx, rateToJpy: null, source: null, status: 'UNAVAILABLE' },
      }],
    }
    respond(unavailable)
    renderPositions()

    expect(await screen.findByText('BTC')).toBeInTheDocument()
    expect(screen.getAllByText('Unavailable').length).toBeGreaterThan(0)
    expect(screen.queryByText('¥0')).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Some position or JPY valuation inputs are unavailable')
  })

  it('marks stale position data and keeps its known values visible', async () => {
    respond({ ...positionResponse, summary: { ...positionResponse.summary, status: 'STALE' }, positions: [{ ...positionResponse.positions[0], status: 'STALE' }] })
    renderPositions()

    expect(await screen.findByText('Stale position data')).toBeInTheDocument()
    expect(screen.getAllByText('¥30,000')).toHaveLength(2)
    expect(screen.getAllByText('Stale').length).toBeGreaterThan(0)
  })

  it('shows an accessible loading state', () => {
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => {})))
    renderPositions()
    expect(screen.getByLabelText('Loading positions')).toHaveAttribute('aria-busy', 'true')
  })

  it('distinguishes no connections from a successful empty position sync', async () => {
    const empty: PositionsResponse = { summary: { ...positionResponse.summary, positionValueJpy: 0, marginJpy: 0, unrealizedPnlJpy: 0 }, positions: [] }
    respond(empty)
    const { unmount } = renderPositions()
    expect(await screen.findByText('No open perpetual positions')).toBeInTheDocument()
    unmount()

    const noConnections: PositionsResponse = { summary: { ...empty.summary, positionValueJpy: null, marginJpy: null, unrealizedPnlJpy: null, status: 'UNAVAILABLE', connectionCount: 0, syncedConnectionCount: 0 }, positions: [] }
    respond(noConnections)
    renderPositions()
    expect(await screen.findByText('No position connections')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'View connections' })).toHaveAttribute('href', '/connections')
  })

  it('offers a retry after an initial request error', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: false, status: 503, json: async () => ({ code: 'SERVICE_UNAVAILABLE' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => positionResponse })
    vi.stubGlobal('fetch', fetchMock)
    renderPositions()

    await screen.findByRole('alert')
    fireEvent.click(await screen.findByRole('button', { name: 'Try again' }))
    await waitFor(() => expect(screen.getByText('BTC')).toBeInTheDocument())
  })
})
