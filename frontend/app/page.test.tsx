import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DashboardPage from '@/app/page'
import type { AssetsResponse } from '@/lib/assets-api'
import type { PortfolioHistoryResponse, PortfolioSummaryResponse } from '@/lib/portfolio-api'
import type { PositionsResponse } from '@/lib/positions-api'
import { disableDemoMode, enableDemoMode } from '@/lib/demo-mode'

const evaluatedAt = '2026-09-28T02:00:00Z'

function jsonResponse(status: number, value: unknown): Response {
  return { ok: status >= 200 && status < 300, status, json: async () => value } as Response
}

function summaryResponse(status: PortfolioSummaryResponse['summary']['status'] = 'COMPLETE'): PortfolioSummaryResponse {
  return {
    summary: {
      netWorthJpy: status === 'UNAVAILABLE' ? null : '46800',
      change24h: {
        amountJpy: '5000',
        percentage: '12',
        status: status === 'STALE' ? 'STALE' : 'COMPLETE',
        baselineSnapshotAt: '2026-09-27T02:00:00Z',
        currentSnapshotAt: evaluatedAt,
      },
      holdingsValueJpy: '45300',
      directionalValueJpy: '30000',
      stablecoinValueJpy: '15300',
      marketExposureJpy: '55300',
      exposureRatio: '1.1845',
      unrealizedPnlJpy: '-150',
      status,
      dataAsOfAt: evaluatedAt,
      lastSuccessfulSyncAt: evaluatedAt,
    },
    connections: [{
      id: 'connection-solana',
      provider: 'SOLANA',
      displayName: 'Phantom Wallet',
      netWorthJpy: '30000',
      dataStatus: status === 'STALE' ? 'STALE' : 'COMPLETE',
      lastAttemptAt: evaluatedAt,
      lastSuccessfulSyncAt: evaluatedAt,
      capabilitySync: [
        { capability: 'BALANCE', status: 'READY', lastAttemptAt: evaluatedAt, lastSuccessAt: evaluatedAt, lastErrorCategory: null },
        { capability: 'ACTIVITY', status: 'NOT_SYNCED', lastAttemptAt: null, lastSuccessAt: null, lastErrorCategory: null },
      ],
    }],
  }
}

function historyResponse(period: PortfolioHistoryResponse['period'] = '30D', stale = false): PortfolioHistoryResponse {
  return {
    period,
    status: 'AVAILABLE',
    rangeStartAt: '2026-08-29T02:00:00Z',
    rangeEndAt: evaluatedAt,
    points: [
      { snapshotAt: '2026-09-27T02:00:00Z', dataAsOfAt: '2026-09-27T02:00:00Z', netWorthJpy: '41800', status: stale ? 'STALE' : 'COMPLETE' },
      { snapshotAt: evaluatedAt, dataAsOfAt: evaluatedAt, netWorthJpy: '46800', status: stale ? 'STALE' : 'COMPLETE' },
    ],
  }
}

function assetsResponse(): AssetsResponse {
  return {
    summary: {
      spotHoldingsValueJpy: '45300',
      directionalAssetsValueJpy: '30000',
      stablecoinsValueJpy: '15300',
      status: 'COMPLETE',
      connectionCount: 1,
      syncedConnectionCount: 1,
      dataAsOfAt: evaluatedAt,
    },
    assets: [{
      assetId: 'MARKET:SOL',
      assetKey: 'SOL',
      symbol: 'SOL',
      name: 'Solana',
      category: 'CRYPTO',
      network: 'SOLANA',
      totalQuantity: '2',
      valueJpy: '30000',
      status: 'COMPLETE',
      price: { amount: '100', currency: 'USD', source: 'COINGECKO', evaluatedAt, status: 'COMPLETE', failureCategory: null },
      change24h: { value: '2.75', unit: 'PERCENTAGE', comparisonPeriod: 'H24', source: 'COINGECKO', evaluatedAt, status: 'COMPLETE', failureCategory: null },
      valuation: { currency: 'JPY', fxSource: 'EXCHANGERATE_API', fxEvaluatedAt: evaluatedAt },
      connections: [{
        connectionId: 'connection-solana', provider: 'SOLANA', displayName: 'Phantom Wallet', quantity: '2',
        valueJpy: '30000', status: 'COMPLETE', balanceFetchedAt: evaluatedAt, lastSuccessAt: evaluatedAt,
      }],
    }],
  }
}

function positionsResponse(): PositionsResponse {
  return {
    summary: { positionValueJpy: '25000', marginJpy: '4000', unrealizedPnlJpy: '-150', status: 'COMPLETE', connectionCount: 1, syncedConnectionCount: 1 },
    positions: [{
      positionKey: 'DEFAULT:BTC', instrumentCode: 'BTC', side: 'LONG', leverage: '2', quantity: '0.01',
      entryPrice: '90000', markPrice: '91000', liquidationPrice: '45000', priceCurrency: 'USD',
      positionValueJpy: '25000', marginAmount: '30', marginCurrency: 'USDC', marginJpy: '4000',
      unrealizedPnl: '-1', pnlCurrency: 'USDC', unrealizedPnlJpy: '-150',
      priceFx: { currency: 'USD', rateToJpy: '150', source: 'EXCHANGERATE_API', evaluatedAt, status: 'COMPLETE' },
      marginFx: { currency: 'USDC', rateToJpy: '133.33', source: 'COINGECKO_AND_EXCHANGERATE_API', evaluatedAt, status: 'COMPLETE' },
      pnlFx: { currency: 'USDC', rateToJpy: '150', source: 'COINGECKO_AND_EXCHANGERATE_API', evaluatedAt, status: 'COMPLETE' },
      status: 'COMPLETE', connectionId: 'connection-hl', provider: 'HYPERLIQUID',
      connectionDisplayName: 'Hyperliquid', fetchedAt: evaluatedAt, lastSuccessAt: evaluatedAt,
    }],
  }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false, gcTime: 0 } },
  })
  return {
    queryClient,
    ...render(<QueryClientProvider client={queryClient}><DashboardPage /></QueryClientProvider>),
  }
}

function mockDashboardFetch(options: {
  summary?: PortfolioSummaryResponse
  history?: (period: string) => PortfolioHistoryResponse
  assets?: AssetsResponse
  positions?: PositionsResponse
  failing?: string[]
}) {
  const fetchMock = vi.fn((input: string | URL | Request) => {
    const url = String(input)
    const endpoint = url.includes('/portfolio/history')
      ? 'history'
      : url.includes('/portfolio/summary')
        ? 'summary'
        : url.endsWith('/assets')
          ? 'assets'
          : 'positions'
    if (options.failing?.includes(endpoint)) return Promise.resolve(jsonResponse(503, { code: 'SERVICE_UNAVAILABLE' }))
    if (endpoint === 'summary') return Promise.resolve(jsonResponse(200, options.summary ?? summaryResponse()))
    if (endpoint === 'history') {
      const period = new URL(url, 'http://localhost').searchParams.get('period') ?? '30D'
      return Promise.resolve(jsonResponse(200, options.history?.(period) ?? historyResponse(period as PortfolioHistoryResponse['period'])))
    }
    if (endpoint === 'assets') return Promise.resolve(jsonResponse(200, options.assets ?? assetsResponse()))
    return Promise.resolve(jsonResponse(200, options.positions ?? positionsResponse()))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  cleanup()
  disableDemoMode()
  vi.unstubAllGlobals()
})

describe('DashboardPage', () => {
  it('shows API-backed portfolio, exposure, service, allocation, assets, positions, and history', async () => {
    const fetchMock = mockDashboardFetch({})
    renderPage()

    expect(await screen.findByText('Total net worth')).toBeInTheDocument()
    expect((await screen.findAllByText('¥46,800')).length).toBeGreaterThan(0)
    expect(screen.getByText('+¥5,000')).toBeInTheDocument()
    expect(screen.getByText('(+12%)')).toBeInTheDocument()
    expect(screen.getAllByText('Fresh').length).toBeGreaterThan(0)
    expect(screen.getByText('Holdings value')).toBeInTheDocument()
    expect(screen.getByText('Market exposure')).toBeInTheDocument()
    expect(screen.getByText('¥55.3K')).toBeInTheDocument()
    expect(screen.getByText('Phantom Wallet')).toBeInTheDocument()
    expect(screen.getByText('Allocation by currency')).toBeInTheDocument()
    expect(screen.getByText('Holdings by currency')).toBeInTheDocument()
    expect(screen.getAllByText('BTC').length).toBeGreaterThan(0)
    expect(screen.getByText('2 saved snapshots. Gaps have no saved snapshot.')).toBeInTheDocument()
    expect(screen.queryByText('¥0')).not.toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Net worth history for 30D' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/portfolio/summary', expect.any(Object))

    fireEvent.click(screen.getByRole('button', { name: '7D' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/portfolio/history?period=7D', expect.any(Object)))
    expect(await screen.findByRole('img', { name: 'Net worth history for 7D' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '90D' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/portfolio/history?period=90D', expect.any(Object)))
    fireEvent.click(screen.getByRole('button', { name: '1Y' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/portfolio/history?period=1Y', expect.any(Object)))
  })

  it('shows stale summary and snapshots, and leaves an empty history period without a zero point', async () => {
    mockDashboardFetch({
      summary: summaryResponse('STALE'),
      history: (period) => ({ ...historyResponse(period as PortfolioHistoryResponse['period']), status: 'EMPTY', points: [] }),
    })
    renderPage()

    expect(await screen.findByText('Total net worth')).toBeInTheDocument()
    expect(screen.getAllByText('Stale').length).toBeGreaterThan(0)
    expect(await screen.findByText('No history in this period')).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: 'Net worth history for 30D' })).not.toBeInTheDocument()
    expect(screen.queryByText('¥0')).not.toBeInTheDocument()
  })

  it('keeps sparse stale history points without creating zero-valued gaps', async () => {
    mockDashboardFetch({ history: (period) => historyResponse(period as PortfolioHistoryResponse['period'], true) })
    renderPage()

    expect(await screen.findByText('2 of 2 snapshots use stale data. Gaps have no saved snapshot.')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Net worth history for 30D' })).toBeInTheDocument()
    expect(screen.queryByText('¥0')).not.toBeInTheDocument()
  })

  it('shows loading while summary and its dependent dashboard requests are pending', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => undefined)))
    renderPage()

    expect(screen.getByLabelText('Loading portfolio summary')).toHaveAttribute('aria-busy', 'true')
    expect(screen.getByRole('status')).toHaveTextContent('Loading sync status')
  })

  it('shows independent partial errors while keeping successful sections available', async () => {
    mockDashboardFetch({ failing: ['assets'] })
    renderPage()

    expect(await screen.findByText('Assets couldn’t be loaded.')).toBeInTheDocument()
    expect(await screen.findByText('Asset allocation couldn’t be loaded.')).toBeInTheDocument()
    expect(screen.getByText('Stablecoin breakdown couldn’t be loaded.')).toBeInTheDocument()
    expect(screen.getAllByText('BTC').length).toBeGreaterThan(0)
  })

  it('shows retryable errors and unavailable values when the portfolio APIs fail', async () => {
    const fetchMock = mockDashboardFetch({ failing: ['summary', 'history', 'assets', 'positions'] })
    renderPage()

    expect(await screen.findByText('Portfolio summary couldn’t be loaded.')).toBeInTheDocument()
    expect(screen.getByText('Positions couldn’t be loaded. Check your connections and try again.')).toBeInTheDocument()
    expect(screen.getByText('Sync status unavailable')).toBeInTheDocument()
    expect(screen.queryByText('¥0')).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalled()
  })

  it('renders the credential-free sample portfolio through the existing Dashboard UI', async () => {
    enableDemoMode()
    renderPage()

    expect(await screen.findByText('Total net worth')).toBeInTheDocument()
    expect(screen.getByText('¥11,330,000')).toBeInTheDocument()
    expect(screen.getByText('Phantom · Demo')).toBeInTheDocument()
    expect(screen.getAllByText('BTC').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Position value · JPY').length).toBeGreaterThan(0)
    expect(screen.getByRole('img', { name: 'Net worth history for 30D' })).toBeInTheDocument()
    expect(screen.getByText(/Gaps have no saved snapshot/)).toBeInTheDocument()
  })
})
