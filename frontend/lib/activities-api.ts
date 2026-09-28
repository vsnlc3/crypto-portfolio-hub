import type { DecimalString } from './decimal'

export type ActivityDataStatus = 'COMPLETE' | 'STALE' | 'PARTIAL' | 'UNAVAILABLE'
export type ActivityProvider = 'BITBANK' | 'SOLANA' | 'HYPERLIQUID'
export type ActivityEventType = 'BUY' | 'SELL' | 'DEPOSIT' | 'WITHDRAW' | 'TRANSFER' | 'SWAP' | 'PERP' | 'FUNDING' | 'OTHER'
export type ActivityDirection = 'IN' | 'OUT' | 'FEE'

export type ActivityLeg = {
  legIndex: number
  direction: ActivityDirection
  assetKey: string
  symbol: string | null
  quantity: DecimalString | null
  originalAmount: DecimalString | null
  originalCurrency: string | null
  jpyValue: DecimalString | null
  valuationStatus: 'VALUED' | 'UNAVAILABLE'
  valuationBasis: 'PROVIDER_REPORTED' | 'EVENT_TIME_MARKET' | 'IMPORT_TIME_MARKET' | 'UNAVAILABLE' | null
  priceUsed: DecimalString | null
  priceCurrency: string | null
  priceSource: string | null
  priceEvaluatedAt: string | null
  fxRateToJpy: DecimalString | null
  fxSource: string | null
  fxEvaluatedAt: string | null
}

export type PerpetualFill = {
  instrumentCode: string
  side: 'BUY' | 'SELL'
  direction: 'OPEN_LONG' | 'CLOSE_LONG' | 'OPEN_SHORT' | 'CLOSE_SHORT' | 'UNKNOWN'
  providerDirection: string | null
  quantity: DecimalString
  price: DecimalString
  priceCurrency: string | null
  startPosition: DecimalString | null
  closedPnl: DecimalString | null
  closedPnlCurrency: string | null
}

export type ActivityItem = {
  id: string
  connectionId: string
  provider: ActivityProvider
  connectionDisplayName: string | null
  providerEventId: string | null
  eventType: ActivityEventType
  originalEventType: string | null
  status: string | null
  occurredAt: string
  importedAt: string
  dataStatus: ActivityDataStatus
  lastSuccessAt: string | null
  legs: ActivityLeg[]
  perpetualFill: PerpetualFill | null
}

export type ActivitiesResponse = {
  summary: {
    status: ActivityDataStatus
    connectionCount: number
    syncedConnectionCount: number
    lastSuccessAt: string | null
  }
  activities: ActivityItem[]
  nextCursor: string | null
  hasMore: boolean
}

type ProblemDetails = { code?: string }

export class ActivitiesApiError extends Error {
  constructor(readonly status: number, readonly code: string | undefined) {
    super('Activity request failed')
    this.name = 'ActivitiesApiError'
  }
}

export const activitiesQueryKey = ['activities'] as const

export async function getActivities({ cursor, limit = 20 }: { cursor?: string | null; limit?: number } = {}): Promise<ActivitiesResponse> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (cursor) params.set('cursor', cursor)
  const response = await fetch(`/api/v1/activities?${params.toString()}`, {
    cache: 'no-store',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await toActivitiesApiError(response)
  return (await response.json()) as ActivitiesResponse
}

async function toActivitiesApiError(response: Response): Promise<ActivitiesApiError> {
  let problem: ProblemDetails = {}
  try {
    problem = (await response.json()) as ProblemDetails
  } catch {
    // Keep transport and parser details out of the UI.
  }
  return new ActivitiesApiError(response.status, problem.code)
}
