import type { DecimalString } from './decimal'
import { apiFetch } from './api-fetch'

export type AssetDataStatus = 'COMPLETE' | 'STALE' | 'PARTIAL' | 'UNAVAILABLE'
export type AssetCategory = 'CRYPTO' | 'STABLECOIN' | 'FIAT'
export type ConnectionProvider = 'BITBANK' | 'SOLANA' | 'HYPERLIQUID'

export type AssetConnectionHolding = {
  connectionId: string
  provider: ConnectionProvider
  displayName: string | null
  quantity: DecimalString | null
  valueJpy: DecimalString | null
  status: AssetDataStatus
  balanceFetchedAt: string | null
  lastSuccessAt: string | null
}

export type AssetPrice = {
  amount: DecimalString | null
  currency: string | null
  source: string | null
  evaluatedAt: string | null
  status: AssetDataStatus
  failureCategory: string | null
}

export type AssetPriceChange = {
  value: DecimalString | null
  unit: 'PERCENTAGE' | string
  comparisonPeriod: 'H24' | string
  source: string | null
  evaluatedAt: string | null
  status: AssetDataStatus
  failureCategory: string | null
}

export type Asset = {
  assetId: string
  assetKey: string
  symbol: string
  name: string
  category: AssetCategory
  network: string | null
  totalQuantity: DecimalString | null
  valueJpy: DecimalString | null
  status: AssetDataStatus
  price: AssetPrice
  change24h: AssetPriceChange
  valuation: {
    currency: string
    fxSource: string | null
    fxEvaluatedAt: string | null
  }
  connections: AssetConnectionHolding[]
}

export type AssetsResponse = {
  summary: {
    spotHoldingsValueJpy: DecimalString | null
    directionalAssetsValueJpy: DecimalString | null
    stablecoinsValueJpy: DecimalString | null
    status: AssetDataStatus
    connectionCount: number
    syncedConnectionCount: number
    dataAsOfAt: string | null
  }
  assets: Asset[]
}

type ProblemDetails = { code?: string }

export class AssetsApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
  ) {
    super('Assets request failed')
    this.name = 'AssetsApiError'
  }
}

export const assetsQueryKey = ['assets'] as const

export async function getAssets(): Promise<AssetsResponse> {
  const response = await apiFetch('/api/v1/assets', {
    cache: 'no-store',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await toAssetsApiError(response)
  return (await response.json()) as AssetsResponse
}

async function toAssetsApiError(response: Response): Promise<AssetsApiError> {
  let problem: ProblemDetails = {}
  try {
    problem = (await response.json()) as ProblemDetails
  } catch {
    // Keep transport and parser details out of the UI.
  }
  return new AssetsApiError(response.status, problem.code)
}
