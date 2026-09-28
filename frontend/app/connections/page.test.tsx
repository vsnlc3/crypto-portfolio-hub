import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { activitiesQueryKey } from '@/lib/activities-api'
import { assetsQueryKey } from '@/lib/assets-api'
import ConnectionsPage from '@/app/connections/page'
import { connectionsQueryKey, syncRunQueryKey, type Connection, type SyncRun } from '@/lib/connections-api'
import { portfolioHistoryQueryKey, portfolioSummaryQueryKey } from '@/lib/portfolio-api'
import { positionsQueryKey } from '@/lib/positions-api'

function jsonResponse(status: number, value: unknown): Response {
  return { ok: status >= 200 && status < 300, status, json: async () => value } as Response
}

function emptyResponse(status: number): Response {
  return { ok: status >= 200 && status < 300, status } as Response
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <ConnectionsPage />
      </QueryClientProvider>,
    ),
  }
}

function solanaConnection(): Connection {
  return {
    id: 'solana-connection-id',
    provider: 'SOLANA',
    displayName: 'Phantom',
    maskedIdentifier: '11111…1111',
    status: 'CONNECTED',
    capabilities: ['BALANCE', 'ACTIVITY'],
    capabilitySync: [
      { capability: 'BALANCE', status: 'NOT_SYNCED', lastAttemptAt: null, lastSuccessAt: null, lastErrorCategory: null },
      { capability: 'ACTIVITY', status: 'NOT_SYNCED', lastAttemptAt: null, lastSuccessAt: null, lastErrorCategory: null },
    ],
    portfolioValue: { amountJpy: null, status: 'UNAVAILABLE' },
    lastAttemptAt: null,
    lastSuccessAt: null,
  }
}

function syncRun(status: SyncRun['status'] = 'SUCCESS'): SyncRun {
  return {
    syncRunId: 'sync-run-id',
    connectionId: 'solana-connection-id',
    triggerType: 'MANUAL',
    status,
    startedAt: '2026-09-28T02:00:00Z',
    finishedAt: status === 'RUNNING' ? null : '2026-09-28T02:00:05Z',
    errorCategory: status === 'PARTIAL' ? 'PARTIAL_FAILURE' : null,
    safeErrorDetail: null,
    capabilities: [
      {
        capability: 'BALANCE', status: 'SUCCESS', recordsFetched: 2, recordsPersisted: 2,
        startedAt: '2026-09-28T02:00:00Z', finishedAt: '2026-09-28T02:00:03Z',
        errorCategory: null, safeErrorDetail: null, continuationAvailable: false,
      },
      {
        capability: 'ACTIVITY', status: status === 'PARTIAL' ? 'FAILED' : 'SUCCESS',
        recordsFetched: null, recordsPersisted: null, startedAt: '2026-09-28T02:00:00Z',
        finishedAt: '2026-09-28T02:00:05Z', errorCategory: status === 'PARTIAL' ? 'TIMEOUT' : null,
        safeErrorDetail: null, continuationAvailable: false,
      },
    ],
  }
}

describe('ConnectionsPage', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('shows an empty state, validates a Solana address, then creates and reloads the Connection', async () => {
    let connections: Connection[] = []
    const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/v1/connections' && !init?.method) return jsonResponse(200, connections)
      if (url === '/api/v1/auth/csrf') {
        return jsonResponse(200, { headerName: 'X-CSRF-TOKEN', token: 'csrf-test' })
      }
      if (url === '/api/v1/connections' && init?.method === 'POST') {
        const request = JSON.parse(String(init.body))
        connections = [solanaConnection()]
        expect(init.headers).toMatchObject({ 'X-CSRF-TOKEN': 'csrf-test' })
        expect(request).toEqual({ provider: 'SOLANA', walletAddress: '11111111111111111111111111111111' })
        return jsonResponse(201, solanaConnection())
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    renderPage()

    expect(await screen.findByText('No connections yet')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Add your first source' }))
    fireEvent.change(screen.getByLabelText('Service'), { target: { value: 'SOLANA' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add connection' }))

    expect(await screen.findByText('Enter a valid 32-byte Solana address.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    fireEvent.change(screen.getByLabelText('Solana Wallet Address'), {
      target: { value: '11111111111111111111111111111111' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Add connection' }))

    expect(await screen.findByText('Connection added.')).toBeInTheDocument()
    expect(await screen.findByText('11111…1111')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(4)
  })

  it('validates and sends bitbank credentials without displaying them in the Connection card', async () => {
    let connections: Connection[] = []
    const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/v1/connections' && !init?.method) return jsonResponse(200, connections)
      if (url === '/api/v1/auth/csrf') {
        return jsonResponse(200, { headerName: 'X-CSRF-TOKEN', token: 'csrf-bitbank' })
      }
      if (url === '/api/v1/connections' && init?.method === 'POST') {
        const request = JSON.parse(String(init.body))
        expect(request).toEqual({
          provider: 'BITBANK',
          apiKey: 'fixture-api-key',
          apiSecret: 'fixture-api-secret',
        })
        connections = [{
          id: 'bitbank-connection-id',
          provider: 'BITBANK',
          displayName: 'bitbank',
          status: 'CONNECTED',
          capabilities: ['BALANCE', 'ACTIVITY'],
          capabilitySync: [
            { capability: 'BALANCE', status: 'NOT_SYNCED', lastAttemptAt: null, lastSuccessAt: null, lastErrorCategory: null },
            { capability: 'ACTIVITY', status: 'NOT_SYNCED', lastAttemptAt: null, lastSuccessAt: null, lastErrorCategory: null },
          ],
          portfolioValue: { amountJpy: null, status: 'UNAVAILABLE' },
          lastAttemptAt: null,
          lastSuccessAt: null,
        }]
        return jsonResponse(201, connections[0])
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    renderPage()

    await screen.findByText('No connections yet')
    fireEvent.click(screen.getByRole('button', { name: 'Add your first source' }))
    fireEvent.click(screen.getByRole('button', { name: 'Add connection' }))
    expect(await screen.findByText('API Key is required.')).toBeInTheDocument()
    expect(screen.getByLabelText('Read-only API Key')).toHaveAttribute('type', 'password')
    expect(screen.getByLabelText('API Secret')).toHaveAttribute('type', 'password')
    expect(fetchMock).toHaveBeenCalledTimes(1)

    fireEvent.change(screen.getByLabelText('Read-only API Key'), { target: { value: 'fixture-api-key' } })
    fireEvent.change(screen.getByLabelText('API Secret'), { target: { value: 'fixture-api-secret' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add connection' }))

    expect(await screen.findByText('Read-only API credentials')).toBeInTheDocument()
    expect(screen.queryByDisplayValue('fixture-api-secret')).not.toBeInTheDocument()
    expect(screen.queryByDisplayValue('fixture-api-key')).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(4)
  })

  it('validates a Hyperliquid address and shows a safe server-side validation error', async () => {
    const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/v1/connections' && !init?.method) return jsonResponse(200, [])
      if (url === '/api/v1/auth/csrf') {
        return jsonResponse(200, { headerName: 'X-CSRF-TOKEN', token: 'csrf-hl' })
      }
      if (url === '/api/v1/connections' && init?.method === 'POST') {
        return jsonResponse(400, {
          code: 'VALIDATION_ERROR',
          errors: [{ field: 'accountAddress', message: 'Invalid value.' }],
        })
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    renderPage()

    await screen.findByText('No connections yet')
    fireEvent.click(screen.getByRole('button', { name: 'Add your first source' }))
    fireEvent.change(screen.getByLabelText('Service'), { target: { value: 'HYPERLIQUID' } })
    fireEvent.change(screen.getByLabelText('Hyperliquid account address'), { target: { value: '0x123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add connection' }))
    expect(await screen.findByText('Enter a 0x address with 40 hexadecimal characters.')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Hyperliquid account address'), {
      target: { value: '0x0000000000000000000000000000000000000001' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Add connection' }))
    expect(await screen.findByText('Review the highlighted fields and try again.')).toBeInTheDocument()
    expect(screen.getByText('Invalid value.')).toBeInTheDocument()
  })

  it('reports load failures and retries the list request', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(503, { code: 'INTERNAL_ERROR' }))
      .mockResolvedValueOnce(jsonResponse(200, []))
    vi.stubGlobal('fetch', fetchMock)
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Connections couldn’t be loaded')
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('No connections yet')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('shows the loading skeleton before the Connection list resolves', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => undefined)))
    renderPage()

    expect(screen.getByLabelText('Loading connections')).toHaveAttribute('aria-busy', 'true')
  })

  it('keeps the last successful list visible when a refresh fails', async () => {
    const connection = solanaConnection()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(200, [connection]))
      .mockResolvedValueOnce(jsonResponse(503, { code: 'INTERNAL_ERROR' }))
    vi.stubGlobal('fetch', fetchMock)
    const { queryClient } = renderPage()

    expect(await screen.findByText('11111…1111')).toBeInTheDocument()
    await queryClient.invalidateQueries({ queryKey: connectionsQueryKey })

    expect(await screen.findByRole('alert')).toHaveTextContent('Showing the last retrieved Connections')
    expect(screen.getByText('11111…1111')).toBeInTheDocument()
  })

  it('shows per-Connection JPY valuation, capability states, and sync timestamps', async () => {
    const connection: Connection = {
      ...solanaConnection(),
      status: 'ERROR',
      portfolioValue: { amountJpy: 234567, status: 'STALE' },
      capabilitySync: [
        { capability: 'BALANCE', status: 'ERROR', lastAttemptAt: '2026-09-28T02:00:00Z', lastSuccessAt: '2026-09-28T01:00:00Z', lastErrorCategory: 'TIMEOUT' },
        { capability: 'ACTIVITY', status: 'READY', lastAttemptAt: '2026-09-28T02:00:00Z', lastSuccessAt: '2026-09-28T02:00:00Z', lastErrorCategory: null },
      ],
      lastAttemptAt: '2026-09-28T02:00:00Z',
      lastSuccessAt: '2026-09-28T02:00:00Z',
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, [connection])))
    renderPage()

    expect(await screen.findByText('¥234,567')).toBeInTheDocument()
    expect(screen.getByText('Stale')).toBeInTheDocument()
    expect(screen.getByText('Spot · error')).toHaveAttribute('title', 'ERROR: TIMEOUT')
    expect(screen.getByText('History · ready')).toBeInTheDocument()
    expect(screen.getByText(/Last successful sync/)).toBeInTheDocument()
    expect(screen.getByText(/Last attempt/)).toBeInTheDocument()
  })

  it('shows an unavailable Connection valuation as unavailable, never as zero', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(200, [solanaConnection()])))
    renderPage()

    expect((await screen.findAllByText('Unavailable')).length).toBeGreaterThan(0)
    expect(screen.queryByText('¥0')).not.toBeInTheDocument()
  })

  it('requests a manual sync, polls its run, and invalidates all dependent portfolio queries', async () => {
    const connection = { ...solanaConnection(), portfolioValue: { amountJpy: 125000, status: 'COMPLETE' as const } }
    const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/v1/connections' && !init?.method) return jsonResponse(200, [connection])
      if (url === '/api/v1/auth/csrf') return jsonResponse(200, { headerName: 'X-CSRF-TOKEN', token: 'csrf-sync' })
      if (url === `/api/v1/connections/${connection.id}/sync` && init?.method === 'POST') {
        expect(init.headers).toMatchObject({ 'X-CSRF-TOKEN': 'csrf-sync' })
        expect(init.body).toBeUndefined()
        expect(init.credentials).toBe('same-origin')
        return jsonResponse(202, {
          syncRunId: 'sync-run-id', connectionId: connection.id, triggerType: 'MANUAL', status: 'RUNNING',
          capabilities: ['BALANCE', 'ACTIVITY'], startedAt: '2026-09-28T02:00:00Z',
        })
      }
      if (url === `/api/v1/connections/${connection.id}/sync-runs/sync-run-id`) return jsonResponse(200, syncRun())
      throw new Error(`Unexpected request: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    const { queryClient } = renderPage()
    await screen.findByText('¥125,000')
    queryClient.setQueryData(assetsQueryKey, { stale: false })
    queryClient.setQueryData(positionsQueryKey, { stale: false })
    queryClient.setQueryData(activitiesQueryKey, { stale: false })
    queryClient.setQueryData(portfolioSummaryQueryKey, { stale: false })
    queryClient.setQueryData(portfolioHistoryQueryKey('30D'), { stale: false })

    fireEvent.click(screen.getByRole('button', { name: 'Sync' }))
    expect(await screen.findByText('Sync completed.')).toBeInTheDocument()
    expect(queryClient.getQueryState(assetsQueryKey)?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(positionsQueryKey)?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(activitiesQueryKey)?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(portfolioSummaryQueryKey)?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(portfolioHistoryQueryKey('30D'))?.isInvalidated).toBe(true)
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/v1/connections/${connection.id}/sync-runs/sync-run-id`,
      expect.objectContaining({ credentials: 'same-origin' }),
    )
    expect(fetchMock.mock.calls.filter(([input, init]) => String(input) === '/api/v1/connections' && !init?.method).length)
      .toBeGreaterThanOrEqual(3)
  })

  it('disables the same Connection while syncing and shows partial capability failure details', async () => {
    const connection = solanaConnection()
    let releaseRun: ((response: Response) => void) | undefined
    const pendingRun = new Promise<Response>((resolve) => { releaseRun = resolve })
    const fetchMock = vi.fn((input: string | URL | Request, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/v1/connections' && !init?.method) return Promise.resolve(jsonResponse(200, [connection]))
      if (url === '/api/v1/auth/csrf') return Promise.resolve(jsonResponse(200, { headerName: 'X-CSRF-TOKEN', token: 'csrf-sync' }))
      if (url === `/api/v1/connections/${connection.id}/sync` && init?.method === 'POST') {
        return Promise.resolve(jsonResponse(202, {
          syncRunId: 'sync-run-id', connectionId: connection.id, triggerType: 'MANUAL', status: 'RUNNING',
          capabilities: ['BALANCE', 'ACTIVITY'], startedAt: '2026-09-28T02:00:00Z',
        }))
      }
      if (url === `/api/v1/connections/${connection.id}/sync-runs/sync-run-id`) return pendingRun
      throw new Error(`Unexpected request: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Sync' }))
    expect(await screen.findByText(/Sync accepted/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Syncing…' })).toBeDisabled()
    await act(async () => releaseRun?.(jsonResponse(200, syncRun('PARTIAL'))))

    expect(await screen.findByRole('alert')).toHaveTextContent('Sync completed with partial failures.')
    expect(screen.getByText('History failed · TIMEOUT')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sync' })).toBeEnabled()
  })

  it('reports an already-running Sync safely and refreshes the Connection state', async () => {
    const connection = solanaConnection()
    const fetchMock = vi.fn((input: string | URL | Request, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/v1/connections' && !init?.method) return Promise.resolve(jsonResponse(200, [connection]))
      if (url === '/api/v1/auth/csrf') return Promise.resolve(jsonResponse(200, { headerName: 'X-CSRF-TOKEN', token: 'csrf-sync' }))
      if (url === `/api/v1/connections/${connection.id}/sync` && init?.method === 'POST') {
        return Promise.resolve(jsonResponse(409, { code: 'SYNC_ALREADY_RUNNING' }))
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Sync' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('This Connection is already syncing.')
    expect(fetchMock.mock.calls.filter(([input, init]) => String(input) === '/api/v1/connections' && !init?.method).length)
      .toBeGreaterThan(1)
    expect(screen.queryByText('csrf-sync')).not.toBeInTheDocument()
  })

  it('confirms disconnection, sends CSRF, and refreshes the list', async () => {
    const connection = solanaConnection()
    let connections: Connection[] = [connection]
    const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/v1/connections' && !init?.method) return jsonResponse(200, connections)
      if (url === '/api/v1/auth/csrf') {
        return jsonResponse(200, { headerName: 'X-CSRF-TOKEN', token: 'csrf-delete' })
      }
      if (url === `/api/v1/connections/${connection.id}` && init?.method === 'DELETE') {
        expect(init.headers).toMatchObject({ 'X-CSRF-TOKEN': 'csrf-delete' })
        connections = []
        return emptyResponse(204)
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderPage()

    expect(await screen.findByText('11111…1111')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Disconnect' }))
    expect(window.confirm).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    vi.mocked(window.confirm).mockReturnValue(true)
    fireEvent.click(screen.getByRole('button', { name: 'Disconnect' }))
    expect(await screen.findByText('Connection removed.')).toBeInTheDocument()
    expect(await screen.findByText('No connections yet')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(4)
  })
})
