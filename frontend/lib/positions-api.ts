import type { DecimalString } from './decimal'

export type PositionDataStatus = 'COMPLETE' | 'STALE' | 'PARTIAL' | 'UNAVAILABLE'

export type PositionFx = {
  currency: string | null
  rateToJpy: DecimalString | null
  source: string | null
  evaluatedAt: string | null
  status: PositionDataStatus
}

export type Position = {
  positionKey: string
  instrumentCode: string
  side: 'LONG' | 'SHORT'
  leverage: DecimalString | null
  quantity: DecimalString
  entryPrice: DecimalString | null
  markPrice: DecimalString | null
  liquidationPrice: DecimalString | null
  priceCurrency: string | null
  positionValueJpy: DecimalString | null
  marginAmount: DecimalString | null
  marginCurrency: string | null
  marginJpy: DecimalString | null
  unrealizedPnl: DecimalString | null
  pnlCurrency: string | null
  unrealizedPnlJpy: DecimalString | null
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
    positionValueJpy: DecimalString | null
    marginJpy: DecimalString | null
    unrealizedPnlJpy: DecimalString | null
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
