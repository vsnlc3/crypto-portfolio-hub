import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ConnectionsPage from '@/app/connections/page'
import { connectionsQueryKey, type Connection } from '@/lib/connections-api'

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
