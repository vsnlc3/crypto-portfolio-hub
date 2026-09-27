export type ConnectionProvider = 'BITBANK' | 'SOLANA' | 'HYPERLIQUID'
export type ConnectionStatus = 'CONNECTED' | 'SYNCING' | 'ERROR' | 'DISCONNECTED'
export type SyncCapability = 'BALANCE' | 'POSITION' | 'ACTIVITY' | 'ACCOUNT'

export type Connection = {
  id: string
  provider: ConnectionProvider
  displayName: string
  maskedIdentifier?: string
  status: ConnectionStatus
  capabilities: SyncCapability[]
  lastAttemptAt?: string
  lastSuccessAt?: string
}

export type ConnectionCreateRequest =
  | { provider: 'BITBANK'; displayName?: string; apiKey: string; apiSecret: string }
  | { provider: 'SOLANA'; displayName?: string; walletAddress: string }
  | { provider: 'HYPERLIQUID'; displayName?: string; accountAddress: string }

type CsrfTokenResponse = { headerName: string; token: string }
type ProblemDetails = {
  code?: string
  errors?: { field?: string; message?: string }[]
}

export class ConnectionsApiError extends Error {
  readonly fieldErrors: Record<string, string>

  constructor(
    readonly status: number,
    readonly code: string | undefined,
    errors: ProblemDetails['errors'] = [],
  ) {
    super('Connection request failed')
    this.name = 'ConnectionsApiError'
    this.fieldErrors = Object.fromEntries(
      (errors ?? [])
        .filter((error): error is { field: string; message: string } => Boolean(error.field && error.message))
        .map((error) => [error.field, error.message]),
    )
  }
}

export const connectionsQueryKey = ['connections'] as const

export async function getConnections(): Promise<Connection[]> {
  const response = await fetch('/api/v1/connections', {
    cache: 'no-store',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await toConnectionsApiError(response)
  return (await response.json()) as Connection[]
}

export async function createConnection(request: ConnectionCreateRequest): Promise<Connection> {
  const csrf = await getCsrfToken()
  const response = await fetch('/api/v1/connections', {
    method: 'POST',
    cache: 'no-store',
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(request),
  })
  if (!response.ok) throw await toConnectionsApiError(response)
  return (await response.json()) as Connection
}

export async function deleteConnection(connectionId: string): Promise<void> {
  const csrf = await getCsrfToken()
  const response = await fetch(`/api/v1/connections/${encodeURIComponent(connectionId)}`, {
    method: 'DELETE',
    cache: 'no-store',
    credentials: 'same-origin',
    headers: { Accept: 'application/json', [csrf.headerName]: csrf.token },
  })
  if (response.status !== 204) throw await toConnectionsApiError(response)
}

async function getCsrfToken(): Promise<CsrfTokenResponse> {
  const response = await fetch('/api/v1/auth/csrf', {
    cache: 'no-store',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await toConnectionsApiError(response)

  const csrf = (await response.json()) as CsrfTokenResponse
  if (!csrf.headerName || !csrf.token) throw new ConnectionsApiError(500, undefined)
  return csrf
}

async function toConnectionsApiError(response: Response): Promise<ConnectionsApiError> {
  let problem: ProblemDetails = {}
  try {
    problem = (await response.json()) as ProblemDetails
  } catch {
    // Keep transport/parser details out of the UI.
  }
  return new ConnectionsApiError(response.status, problem.code, problem.errors)
}
