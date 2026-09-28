export type PortfolioDataStatus = 'COMPLETE' | 'STALE' | 'PARTIAL' | 'UNAVAILABLE'
export type ConnectionProvider = 'BITBANK' | 'SOLANA' | 'HYPERLIQUID' | string
export type SyncCapability = 'BALANCE' | 'POSITION' | 'ACTIVITY' | 'ACCOUNT' | string

export type CapabilitySync = {
  capability: SyncCapability
  status: 'NOT_SYNCED' | 'SYNCING' | 'READY' | 'ERROR' | string
  lastAttemptAt: string | null
  lastSuccessAt: string | null
  lastErrorCategory: string | null
}

export type PortfolioConnection = {
  id: string
  provider: ConnectionProvider
  displayName: string | null
  netWorthJpy: number | null
  dataStatus: PortfolioDataStatus
  lastAttemptAt: string | null
  lastSuccessfulSyncAt: string | null
  capabilitySync: CapabilitySync[]
}

export type PortfolioSummary = {
  netWorthJpy: number | null
  change24h: {
    amountJpy: number | null
    percentage: number | null
    status: PortfolioDataStatus
    baselineSnapshotAt: string | null
    currentSnapshotAt: string | null
  }
  holdingsValueJpy: number | null
  directionalValueJpy: number | null
  stablecoinValueJpy: number | null
  marketExposureJpy: number | null
  exposureRatio: number | null
  unrealizedPnlJpy: number | null
  status: PortfolioDataStatus
  dataAsOfAt: string | null
  lastSuccessfulSyncAt: string | null
}

export type PortfolioSummaryResponse = {
  summary: PortfolioSummary
  connections: PortfolioConnection[]
}

export type PortfolioHistoryPeriod = '7D' | '30D' | '90D' | '1Y'

export type PortfolioHistoryResponse = {
  period: PortfolioHistoryPeriod
  status: 'AVAILABLE' | 'EMPTY'
  rangeStartAt: string
  rangeEndAt: string
  points: {
    snapshotAt: string
    dataAsOfAt: string
    netWorthJpy: number
    status: 'COMPLETE' | 'STALE'
  }[]
}

type ProblemDetails = { code?: string }

export class PortfolioApiError extends Error {
  constructor(readonly status: number, readonly code: string | undefined) {
    super('Portfolio request failed')
    this.name = 'PortfolioApiError'
  }
}

export const portfolioSummaryQueryKey = ['portfolio', 'summary'] as const
export const portfolioHistoryQueryKey = (period: PortfolioHistoryPeriod) => ['portfolio', 'history', period] as const

export async function getPortfolioSummary(): Promise<PortfolioSummaryResponse> {
  const response = await fetch('/api/v1/portfolio/summary', {
    cache: 'no-store',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await toPortfolioApiError(response)
  return (await response.json()) as PortfolioSummaryResponse
}

export async function getPortfolioHistory(period: PortfolioHistoryPeriod): Promise<PortfolioHistoryResponse> {
  const response = await fetch(`/api/v1/portfolio/history?period=${encodeURIComponent(period)}`, {
    cache: 'no-store',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await toPortfolioApiError(response)
  return (await response.json()) as PortfolioHistoryResponse
}

async function toPortfolioApiError(response: Response): Promise<PortfolioApiError> {
  let problem: ProblemDetails = {}
  try {
    problem = (await response.json()) as ProblemDetails
  } catch {
    // Keep transport details out of the UI.
  }
  return new PortfolioApiError(response.status, problem.code)
}
