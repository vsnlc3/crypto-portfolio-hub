import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AssetsPage from '@/app/assets/page'
import type { AssetsResponse } from '@/lib/assets-api'

const evaluatedAt = '2026-09-28T02:00:00Z'

function jsonResponse(status: number, value: unknown): Response {
  return { ok: status >= 200 && status < 300, status, json: async () => value } as Response
}

function assetsResponse(overrides: Partial<AssetsResponse> = {}): AssetsResponse {
  return {
    summary: {
      spotHoldingsValueJpy: '30000',
      directionalAssetsValueJpy: '30000',
      stablecoinsValueJpy: '0',
      status: 'COMPLETE',
      connectionCount: 1,
      syncedConnectionCount: 1,
      dataAsOfAt: evaluatedAt,
    },
    assets: [
      {
        assetId: 'MARKET:SOL',
        assetKey: 'SOL',
        symbol: 'SOL',
        name: 'Solana',
        category: 'CRYPTO',
        network: 'SOLANA',
        totalQuantity: '2',
        valueJpy: '30000',
        status: 'COMPLETE',
        price: {
          amount: '100',
          currency: 'USD',
          source: 'COINGECKO',
          evaluatedAt,
          status: 'COMPLETE',
          failureCategory: null,
        },
        change24h: {
          value: '2.75',
          unit: 'PERCENTAGE',
          comparisonPeriod: 'H24',
          source: 'COINGECKO',
          evaluatedAt,
          status: 'COMPLETE',
          failureCategory: null,
        },
        valuation: {
          currency: 'JPY',
          fxSource: 'EXCHANGERATE_API',
          fxEvaluatedAt: evaluatedAt,
        },
        connections: [
          {
            connectionId: 'wallet-connection',
            provider: 'SOLANA',
            displayName: 'Phantom',
            quantity: '2',
            valueJpy: '30000',
            status: 'COMPLETE',
            balanceFetchedAt: evaluatedAt,
            lastSuccessAt: evaluatedAt,
          },
        ],
      },
    ],
    ...overrides,
  }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AssetsPage />
    </QueryClientProvider>,
  )
}

describe('AssetsPage', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('loads authenticated API data and formats JPY, quote currency, source, and 24h change', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, assetsResponse()))
    vi.stubGlobal('fetch', fetchMock)
    renderPage()

    expect((await screen.findAllByText('¥30,000')).length).toBeGreaterThan(0)
    expect(screen.getByText('2 SOL')).toBeInTheDocument()
    expect(screen.getByText('@ $100.00')).toBeInTheDocument()
    expect(screen.getByText('+2.75%')).toBeInTheDocument()
    expect(screen.getAllByText(/CoinGecko/).length).toBeGreaterThan(0)
    expect(screen.getByText(/FX ExchangeRate API/)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/assets', expect.objectContaining({
      cache: 'no-store',
      credentials: 'same-origin',
    }))
  })

  it('shows unavailable 24h change as text instead of rendering zero', async () => {
    const data = assetsResponse()
    data.assets[0].change24h = {
      value: null,
      unit: 'PERCENTAGE',
      comparisonPeriod: 'H24',
      source: 'COINGECKO',
      evaluatedAt,
      status: 'UNAVAILABLE',
      failureCategory: null,
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, data)))
    renderPage()

    expect(await screen.findByText('24h unavailable')).toBeInTheDocument()
    expect(screen.queryByText('0.00%')).not.toBeInTheDocument()
    expect(screen.getByText('CoinGecko')).toBeInTheDocument()
  })

  it('shows stale status and a partial-data warning without hiding available holdings', async () => {
    const data = assetsResponse()
    data.summary = {
      ...data.summary,
      spotHoldingsValueJpy: null,
      status: 'PARTIAL',
      connectionCount: 2,
      syncedConnectionCount: 1,
    }
    data.assets[0].status = 'STALE'
    data.assets[0].totalQuantity = null
    data.assets[0].valueJpy = null
    data.assets[0].price.status = 'STALE'
    data.assets[0].change24h.value = null
    data.assets[0].change24h.status = 'STALE'
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, data)))
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('1 of 2 connections')
    expect(screen.getByText('Known: 2 SOL')).toBeInTheDocument()
    expect(screen.getByText('Stale')).toBeInTheDocument()
    expect(screen.getByText('24h stale')).toBeInTheDocument()
    expect(screen.getAllByText('¥30,000').length).toBeGreaterThan(0)
    expect(screen.getByRole('alert')).toHaveTextContent('Partial asset data')
  })

  it('shows a loading skeleton while the API request is pending', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => undefined)))
    renderPage()

    expect(screen.getByLabelText('Loading assets')).toHaveAttribute('aria-busy', 'true')
  })

  it('shows an error state and retries the API request', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(503, { code: 'INTERNAL_ERROR' }))
      .mockResolvedValueOnce(jsonResponse(200, assetsResponse({
        summary: {
          spotHoldingsValueJpy: null,
          directionalAssetsValueJpy: null,
          stablecoinsValueJpy: null,
          status: 'UNAVAILABLE',
          connectionCount: 0,
          syncedConnectionCount: 0,
          dataAsOfAt: null,
        },
        assets: [],
      })))
    vi.stubGlobal('fetch', fetchMock)
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Assets couldn’t be loaded')
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('No connections yet')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('shows an empty no-connection state and links to Connections', async () => {
    const data = assetsResponse({
      summary: {
        spotHoldingsValueJpy: null,
        directionalAssetsValueJpy: null,
        stablecoinsValueJpy: null,
        status: 'UNAVAILABLE',
        connectionCount: 0,
        syncedConnectionCount: 0,
        dataAsOfAt: null,
      },
      assets: [],
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, data)))
    renderPage()

    expect(await screen.findByText('No connections yet')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Connect a source' })).toHaveAttribute('href', '/connections')
    expect(screen.queryByText('Portfolio values are unavailable until balance sync and valuation data are available.'))
      .not.toBeInTheDocument()
  })
})
