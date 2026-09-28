export type PositionDataStatus = 'COMPLETE' | 'STALE' | 'PARTIAL' | 'UNAVAILABLE'

export type PositionFx = {
  currency: string | null
  rateToJpy: number | null
  source: string | null
  evaluatedAt: string | null
  status: PositionDataStatus
}

export type Position = {
  positionKey: string
  instrumentCode: string
  side: 'LONG' | 'SHORT'
  leverage: number | null
  quantity: number
  entryPrice: number | null
  markPrice: number | null
  liquidationPrice: number | null
  priceCurrency: string | null
  positionValueJpy: number | null
  marginAmount: number | null
  marginCurrency: string | null
  marginJpy: number | null
  unrealizedPnl: number | null
  pnlCurrency: string | null
  unrealizedPnlJpy: number | null
  priceFx: PositionFx
  marginFx: PositionFx
  pnlFx: PositionFx
  status: PositionDataStatus
  connectionId: string
  provider: 'BITBANK' | 'SOLANA' | 'HYPERLIQUID' | string
  connectionDisplayName: string | null
  fetchedAt: string | null
  lastSuccessAt: string | null
}

export type PositionsResponse = {
  summary: {
    positionValueJpy: number | null
    marginJpy: number | null
    unrealizedPnlJpy: number | null
    status: PositionDataStatus
    connectionCount: number
    syncedConnectionCount: number
  }
  positions: Position[]
}

type ProblemDetails = { code?: string }

export class PositionsApiError extends Error {
  constructor(readonly status: number, readonly code: string | undefined) {
    super('Positions request failed')
    this.name = 'PositionsApiError'
  }
}

export const positionsQueryKey = ['positions'] as const

export async function getPositions(): Promise<PositionsResponse> {
  const response = await fetch('/api/v1/positions', {
    cache: 'no-store',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await toPositionsApiError(response)
  return (await response.json()) as PositionsResponse
}

async function toPositionsApiError(response: Response): Promise<PositionsApiError> {
  let problem: ProblemDetails = {}
  try {
    problem = (await response.json()) as ProblemDetails
  } catch {
    // Keep transport and parser details out of the UI.
  }
  return new PositionsApiError(response.status, problem.code)
}
