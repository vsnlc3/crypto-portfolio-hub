import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ActivityFeed } from '@/components/activity/activity-feed'
import type { ActivitiesResponse, ActivityItem } from '@/lib/activities-api'

function makeActivity(overrides: Partial<ActivityItem> = {}): ActivityItem {
  return {
    id: 'activity-1',
    connectionId: 'connection-1',
    provider: 'SOLANA',
    connectionDisplayName: 'Main wallet',
    providerEventId: 'signature-1',
    eventType: 'SWAP',
    originalEventType: 'SWAP',
    status: 'CONFIRMED',
    occurredAt: '2026-09-28T12:00:00Z',
    importedAt: '2026-09-28T12:01:00Z',
    dataStatus: 'COMPLETE',
    lastSuccessAt: '2026-09-28T12:01:00Z',
    legs: [
      {
        legIndex: 0,
        direction: 'OUT',
        assetKey: 'SOL',
        symbol: 'SOL',
        quantity: '10',
        originalAmount: '10',
        originalCurrency: 'SOL',
        jpyValue: '1500000',
        valuationStatus: 'VALUED',
        valuationBasis: 'EVENT_TIME_MARKET',
        priceUsed: '1000',
        priceCurrency: 'USD',
        priceSource: 'COINGECKO',
        priceEvaluatedAt: '2026-09-28T12:00:00Z',
        fxRateToJpy: '150',
        fxSource: 'EXCHANGERATE_API',
        fxEvaluatedAt: '2026-09-28T12:00:00Z',
      },
      {
        legIndex: 1,
        direction: 'IN',
        assetKey: 'USDC',
        symbol: 'USDC',
        quantity: '1500',
        originalAmount: '1500',
        originalCurrency: 'USDC',
        jpyValue: '225000',
        valuationStatus: 'VALUED',
        valuationBasis: 'EVENT_TIME_MARKET',
        priceUsed: '1',
        priceCurrency: 'USD',
        priceSource: 'COINGECKO',
        priceEvaluatedAt: '2026-09-28T12:00:00Z',
        fxRateToJpy: '150',
        fxSource: 'EXCHANGERATE_API',
        fxEvaluatedAt: '2026-09-28T12:00:00Z',
      },
      {
        legIndex: 2,
        direction: 'FEE',
        assetKey: 'SOL',
        symbol: 'SOL',
        quantity: '0.01',
        originalAmount: '0.01',
        originalCurrency: 'SOL',
        jpyValue: '1500',
        valuationStatus: 'VALUED',
        valuationBasis: 'EVENT_TIME_MARKET',
        priceUsed: '1000',
        priceCurrency: 'USD',
        priceSource: 'COINGECKO',
        priceEvaluatedAt: '2026-09-28T12:00:00Z',
        fxRateToJpy: '150',
        fxSource: 'EXCHANGERATE_API',
        fxEvaluatedAt: '2026-09-28T12:00:00Z',
      },
    ],
    perpetualFill: null,
    ...overrides,
  }
}

function makeResponse(overrides: Partial<ActivitiesResponse> = {}): ActivitiesResponse {
  return {
    summary: { status: 'COMPLETE', connectionCount: 1, syncedConnectionCount: 1, lastSuccessAt: '2026-09-28T12:01:00Z' },
    activities: [makeActivity()],
    nextCursor: null,
    hasMore: false,
    ...overrides,
  }
}

function renderFeed() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return render(<QueryClientProvider client={queryClient}><ActivityFeed /></QueryClientProvider>)
}

function respond(body: ActivitiesResponse, status = 200) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: status >= 200 && status < 300, status, json: async () => body }))
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('ActivityFeed', () => {
  it('shows a Swap as separate OUT / IN / FEE legs with raw units and signed JPY values', async () => {
    respond(makeResponse())
    renderFeed()

    expect(await screen.findByRole('heading', { name: 'Swap' })).toBeInTheDocument()
    expect(screen.getByText('Out')).toBeInTheDocument()
    expect(screen.getByText('In')).toBeInTheDocument()
    expect(screen.getByText('Fee')).toBeInTheDocument()
    expect(screen.getByText((_, element) => element?.tagName === 'P' && element.textContent?.includes('Quantity: 10 SOL') === true)).toBeInTheDocument()
    expect(screen.getAllByText(/Original amount:/)).toHaveLength(3)
    expect(screen.getByText('-¥1,500,000')).toBeInTheDocument()
    expect(screen.getByText('+¥225,000')).toBeInTheDocument()
    expect(screen.getByText('-¥1,500')).toBeInTheDocument()
    expect(screen.getByText('Main wallet')).toBeInTheDocument()
  })

  it('shows unavailable leg valuation as unavailable and never as zero', async () => {
    const activity = makeActivity({ legs: [{
      ...makeActivity().legs[0],
      quantity: null,
      originalAmount: null,
      jpyValue: null,
      valuationStatus: 'UNAVAILABLE',
      valuationBasis: 'UNAVAILABLE',
    }] })
    respond(makeResponse({ activities: [activity] }))
    renderFeed()

    expect(await screen.findByText('JPY valuation unavailable')).toBeInTheDocument()
    expect(screen.getAllByText('Unavailable').length).toBeGreaterThan(0)
    expect(screen.queryByText('¥0')).not.toBeInTheDocument()
  })

  it('shows partial and stale sync states while retaining stored history', async () => {
    const staleActivity = makeActivity({ dataStatus: 'STALE' })
    respond(makeResponse({
      summary: { status: 'PARTIAL', connectionCount: 2, syncedConnectionCount: 1, lastSuccessAt: '2026-09-28T12:01:00Z' },
      activities: [staleActivity],
    }))
    renderFeed()

    expect(await screen.findByRole('alert')).toHaveTextContent('1 of 2 connections have successful Activity sync history')
    expect(screen.getByText('Stale')).toBeInTheDocument()
    expect(screen.getByText(/Last synced/)).toBeInTheDocument()
    expect(screen.getAllByText('SOL').length).toBeGreaterThan(0)
  })

  it('renders Perpetual fills as position changes instead of spot IN / OUT legs', async () => {
    const perp = makeActivity({
      eventType: 'PERP',
      originalEventType: 'Order Filled',
      legs: [],
      perpetualFill: {
        instrumentCode: 'BTC',
        side: 'SELL',
        direction: 'CLOSE_LONG',
        providerDirection: 'Close Long',
        quantity: '0.2',
        price: '64000',
        priceCurrency: 'USD',
        startPosition: '0.5',
        closedPnl: '-125',
        closedPnlCurrency: 'USDC',
      },
    })
    respond(makeResponse({ activities: [perp] }))
    renderFeed()

    expect(await screen.findByRole('heading', { name: 'Perpetual fill' })).toBeInTheDocument()
    expect(screen.getByText('BTC · close long')).toBeInTheDocument()
    expect(screen.getByText('Side: SELL')).toBeInTheDocument()
    expect(screen.getByText((_, element) => element?.tagName === 'SPAN' && element.textContent?.includes('Quantity: 0.2 BTC') === true)).toBeInTheDocument()
    expect(screen.getByText(/Price:.*64,000/)).toBeInTheDocument()
    expect(screen.getByText(/Closed PnL:/)).toBeInTheDocument()
    expect(screen.queryByText('In')).not.toBeInTheDocument()
    expect(screen.queryByText('Out')).not.toBeInTheDocument()
  })

  it('loads older cursor pages and groups events by their event date', async () => {
    const older = makeActivity({
      id: 'activity-older',
      occurredAt: '2026-09-27T12:00:00Z',
      eventType: 'DEPOSIT',
      originalEventType: 'deposit',
      legs: [],
    })
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => makeResponse({ nextCursor: 'cursor-page-2', hasMore: true }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => makeResponse({ activities: [older] }) })
    vi.stubGlobal('fetch', fetchMock)
    renderFeed()

    expect(await screen.findByRole('heading', { name: 'Swap' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Load more Activity' }))
    expect(await screen.findByRole('heading', { name: 'Deposit' })).toBeInTheDocument()
    expect(screen.getAllByRole('region')).toHaveLength(2)
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/activities?limit=20&cursor=cursor-page-2', expect.any(Object))
  })

  it('keeps loaded pages visible and retries a failed older-page request', async () => {
    const older = makeActivity({
      id: 'activity-older',
      occurredAt: '2026-09-27T12:00:00Z',
      eventType: 'DEPOSIT',
      legs: [],
    })
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => makeResponse({ nextCursor: 'cursor-next', hasMore: true }) })
      .mockResolvedValueOnce({ ok: false, status: 503, json: async () => ({ code: 'SERVICE_UNAVAILABLE' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => makeResponse({ activities: [older] }) })
    vi.stubGlobal('fetch', fetchMock)
    renderFeed()

    expect(await screen.findByRole('heading', { name: 'Swap' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Load more Activity' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('More Activity couldn’t be loaded')
    expect(screen.getByRole('heading', { name: 'Swap' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Deposit' })).toBeInTheDocument()
  })

  it('provides an accessible loading state and an empty state for a completed empty sync', async () => {
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => {})))
    const { unmount } = renderFeed()
    expect(screen.getByLabelText('Loading activity')).toHaveAttribute('aria-busy', 'true')
    unmount()

    respond(makeResponse({ activities: [] }))
    renderFeed()
    expect(await screen.findByText('No Activity yet')).toBeInTheDocument()
    expect(screen.getByText('A successful Activity sync found no events.')).toBeInTheDocument()
    unmount()

    respond(makeResponse({
      summary: { status: 'UNAVAILABLE', connectionCount: 0, syncedConnectionCount: 0, lastSuccessAt: null },
      activities: [],
    }))
    renderFeed()
    expect(await screen.findByText('No connections yet')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Connect a source' })).toHaveAttribute('href', '/connections')
  })

  it('offers retry after the initial request fails', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: false, status: 503, json: async () => ({ code: 'SERVICE_UNAVAILABLE' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => makeResponse() })
    vi.stubGlobal('fetch', fetchMock)
    renderFeed()

    await screen.findByRole('alert')
    fireEvent.click(await screen.findByRole('button', { name: 'Try again' }))
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Swap' })).toBeInTheDocument())
  })
})
