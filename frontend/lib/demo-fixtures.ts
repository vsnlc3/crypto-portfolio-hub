import type { ActivitiesResponse, ActivityItem } from './activities-api'
import type { AssetsResponse } from './assets-api'
import type { AuthenticatedUser } from './auth-api'
import type { Connection } from './connections-api'
import type { PortfolioHistoryPeriod, PortfolioHistoryResponse, PortfolioSummaryResponse } from './portfolio-api'
import type { PositionsResponse } from './positions-api'

const now = Date.now()
const isoAgo = (hours: number) => new Date(now - hours * 60 * 60 * 1000).toISOString()

export const demoUser: AuthenticatedUser = {
  id: 'demo-read-only-user',
  email: 'demo@example.invalid',
  displayName: 'Demo User',
  avatarUrl: null,
  isDemo: true,
}

export const demoConnections: Connection[] = [
  {
    id: 'demo-bitbank',
    provider: 'BITBANK',
    displayName: 'bitbank · Demo',
    maskedIdentifier: 'Read-only API credentials',
    status: 'CONNECTED',
    capabilities: ['BALANCE', 'ACTIVITY'],
    capabilitySync: [
      { capability: 'BALANCE', status: 'READY', lastAttemptAt: isoAgo(2), lastSuccessAt: isoAgo(2), lastErrorCategory: null },
      { capability: 'ACTIVITY', status: 'READY', lastAttemptAt: isoAgo(2), lastSuccessAt: isoAgo(2), lastErrorCategory: null },
    ],
    portfolioValue: { amountJpy: '5333333', status: 'COMPLETE' },
    lastAttemptAt: isoAgo(2),
    lastSuccessAt: isoAgo(2),
  },
  {
    id: 'demo-solana',
    provider: 'SOLANA',
    displayName: 'Phantom · Demo',
    maskedIdentifier: 'DemoWallet…So1ana',
    status: 'ERROR',
    capabilities: ['BALANCE', 'ACTIVITY'],
    capabilitySync: [
      { capability: 'BALANCE', status: 'ERROR', lastAttemptAt: isoAgo(1), lastSuccessAt: isoAgo(27), lastErrorCategory: 'TIMEOUT' },
      { capability: 'ACTIVITY', status: 'READY', lastAttemptAt: isoAgo(1), lastSuccessAt: isoAgo(1), lastErrorCategory: null },
    ],
    portfolioValue: { amountJpy: '1560000', status: 'STALE' },
    lastAttemptAt: isoAgo(1),
    lastSuccessAt: isoAgo(1),
  },
  {
    id: 'demo-hyperliquid',
    provider: 'HYPERLIQUID',
    displayName: 'Hyperliquid · Demo',
    maskedIdentifier: '0x0000…dEm0',
    status: 'CONNECTED',
    capabilities: ['BALANCE', 'POSITION', 'ACTIVITY', 'ACCOUNT'],
    capabilitySync: [
      { capability: 'BALANCE', status: 'READY', lastAttemptAt: isoAgo(3), lastSuccessAt: isoAgo(3), lastErrorCategory: null },
      { capability: 'POSITION', status: 'READY', lastAttemptAt: isoAgo(3), lastSuccessAt: isoAgo(3), lastErrorCategory: null },
      { capability: 'ACTIVITY', status: 'READY', lastAttemptAt: isoAgo(3), lastSuccessAt: isoAgo(3), lastErrorCategory: null },
      { capability: 'ACCOUNT', status: 'READY', lastAttemptAt: isoAgo(3), lastSuccessAt: isoAgo(3), lastErrorCategory: null },
    ],
    portfolioValue: { amountJpy: '4436667', status: 'COMPLETE' },
    lastAttemptAt: isoAgo(3),
    lastSuccessAt: isoAgo(3),
  },
]

export const demoPortfolioSummary: PortfolioSummaryResponse = {
  summary: {
    netWorthJpy: '11330000',
    change24h: {
      amountJpy: '235000',
      percentage: '2.12',
      status: 'COMPLETE',
      baselineSnapshotAt: isoAgo(26),
      currentSnapshotAt: isoAgo(2),
    },
    holdingsValueJpy: '11270000',
    directionalValueJpy: '9860000',
    stablecoinValueJpy: '1410000',
    marketExposureJpy: '12560000',
    exposureRatio: '1.1085',
    unrealizedPnlJpy: '60000',
    status: 'STALE',
    dataAsOfAt: isoAgo(1),
    lastSuccessfulSyncAt: isoAgo(2),
  },
  connections: [
    {
      id: 'demo-bitbank', provider: 'BITBANK', displayName: 'bitbank · Demo', netWorthJpy: '5333333',
      dataStatus: 'COMPLETE', lastAttemptAt: isoAgo(2), lastSuccessfulSyncAt: isoAgo(2),
      capabilitySync: demoConnections[0].capabilitySync,
    },
    {
      id: 'demo-solana', provider: 'SOLANA', displayName: 'Phantom · Demo', netWorthJpy: '1560000',
      dataStatus: 'STALE', lastAttemptAt: isoAgo(1), lastSuccessfulSyncAt: isoAgo(27),
      capabilitySync: demoConnections[1].capabilitySync,
    },
    {
      id: 'demo-hyperliquid', provider: 'HYPERLIQUID', displayName: 'Hyperliquid · Demo', netWorthJpy: '4436667',
      dataStatus: 'COMPLETE', lastAttemptAt: isoAgo(3), lastSuccessfulSyncAt: isoAgo(3),
      capabilitySync: demoConnections[2].capabilitySync,
    },
  ],
}

export const demoAssets: AssetsResponse = {
  summary: {
    spotHoldingsValueJpy: '11270000',
    directionalAssetsValueJpy: '9860000',
    stablecoinsValueJpy: '1410000',
    status: 'STALE',
    connectionCount: 3,
    syncedConnectionCount: 3,
    dataAsOfAt: isoAgo(1),
  },
  assets: [
    {
      assetId: 'MARKET:BTC', assetKey: 'BTC', symbol: 'BTC', name: 'Bitcoin', category: 'CRYPTO', network: null,
      totalQuantity: '0.48', valueJpy: '6200000', status: 'COMPLETE',
      price: { amount: '86111.11', currency: 'USD', source: 'COINGECKO', evaluatedAt: isoAgo(2), status: 'COMPLETE', failureCategory: null },
      change24h: { value: '2.35', unit: 'PERCENTAGE', comparisonPeriod: 'H24', source: 'COINGECKO', evaluatedAt: isoAgo(2), status: 'COMPLETE', failureCategory: null },
      valuation: { currency: 'JPY', fxSource: 'EXCHANGERATE_API', fxEvaluatedAt: isoAgo(2) },
      connections: [
        { connectionId: 'demo-bitbank', provider: 'BITBANK', displayName: 'bitbank · Demo', quantity: '0.32', valueJpy: '4133333', status: 'COMPLETE', balanceFetchedAt: isoAgo(2), lastSuccessAt: isoAgo(2) },
        { connectionId: 'demo-hyperliquid', provider: 'HYPERLIQUID', displayName: 'Hyperliquid · Demo', quantity: '0.16', valueJpy: '2066667', status: 'COMPLETE', balanceFetchedAt: isoAgo(3), lastSuccessAt: isoAgo(3) },
      ],
    },
    {
      assetId: 'MARKET:ETH', assetKey: 'ETH', symbol: 'ETH', name: 'Ethereum', category: 'CRYPTO', network: null,
      totalQuantity: '7', valueJpy: '2100000', status: 'COMPLETE',
      price: { amount: '2000', currency: 'USD', source: 'COINGECKO', evaluatedAt: isoAgo(2), status: 'COMPLETE', failureCategory: null },
      change24h: { value: '-0.85', unit: 'PERCENTAGE', comparisonPeriod: 'H24', source: 'COINGECKO', evaluatedAt: isoAgo(2), status: 'COMPLETE', failureCategory: null },
      valuation: { currency: 'JPY', fxSource: 'EXCHANGERATE_API', fxEvaluatedAt: isoAgo(2) },
      connections: [
        { connectionId: 'demo-bitbank', provider: 'BITBANK', displayName: 'bitbank · Demo', quantity: '4', valueJpy: '1200000', status: 'COMPLETE', balanceFetchedAt: isoAgo(2), lastSuccessAt: isoAgo(2) },
        { connectionId: 'demo-hyperliquid', provider: 'HYPERLIQUID', displayName: 'Hyperliquid · Demo', quantity: '3', valueJpy: '900000', status: 'COMPLETE', balanceFetchedAt: isoAgo(3), lastSuccessAt: isoAgo(3) },
      ],
    },
    {
      assetId: 'MARKET:SOL', assetKey: 'SOL', symbol: 'SOL', name: 'Solana', category: 'CRYPTO', network: 'SOLANA',
      totalQuantity: '67.09677419', valueJpy: '1560000', status: 'STALE',
      price: { amount: '155', currency: 'USD', source: 'COINGECKO', evaluatedAt: isoAgo(27), status: 'STALE', failureCategory: 'TIMEOUT' },
      change24h: { value: null, unit: 'PERCENTAGE', comparisonPeriod: 'H24', source: 'COINGECKO', evaluatedAt: isoAgo(27), status: 'STALE', failureCategory: 'TIMEOUT' },
      valuation: { currency: 'JPY', fxSource: 'EXCHANGERATE_API', fxEvaluatedAt: isoAgo(27) },
      connections: [
        { connectionId: 'demo-solana', provider: 'SOLANA', displayName: 'Phantom · Demo', quantity: '67.09677419', valueJpy: '1560000', status: 'STALE', balanceFetchedAt: isoAgo(27), lastSuccessAt: isoAgo(27) },
      ],
    },
    {
      assetId: 'MARKET:USDC', assetKey: 'USDC', symbol: 'USDC', name: 'USD Coin', category: 'STABLECOIN', network: 'HYPERLIQUID',
      totalQuantity: '9400', valueJpy: '1410000', status: 'COMPLETE',
      price: { amount: '1', currency: 'USD', source: 'COINGECKO', evaluatedAt: isoAgo(3), status: 'COMPLETE', failureCategory: null },
      change24h: { value: '0.02', unit: 'PERCENTAGE', comparisonPeriod: 'H24', source: 'COINGECKO', evaluatedAt: isoAgo(3), status: 'COMPLETE', failureCategory: null },
      valuation: { currency: 'JPY', fxSource: 'EXCHANGERATE_API', fxEvaluatedAt: isoAgo(3) },
      connections: [
        { connectionId: 'demo-hyperliquid', provider: 'HYPERLIQUID', displayName: 'Hyperliquid · Demo', quantity: '9400', valueJpy: '1410000', status: 'COMPLETE', balanceFetchedAt: isoAgo(3), lastSuccessAt: isoAgo(3) },
      ],
    },
  ],
}

export const demoPositions: PositionsResponse = {
  summary: {
    positionValueJpy: '2700000', marginJpy: '270000', unrealizedPnlJpy: '60000',
    status: 'COMPLETE', connectionCount: 1, syncedConnectionCount: 1,
  },
  positions: [{
    positionKey: 'DEFAULT:BTC', instrumentCode: 'BTC', side: 'LONG', leverage: '3', quantity: '0.2',
    entryPrice: '86000', markPrice: '89000', liquidationPrice: '62000', priceCurrency: 'USD',
    positionValueJpy: '2700000', marginAmount: '1800', marginCurrency: 'USDC', marginJpy: '270000',
    unrealizedPnl: '400', pnlCurrency: 'USDC', unrealizedPnlJpy: '60000',
    priceFx: { currency: 'USD', rateToJpy: '150', source: 'EXCHANGERATE_API', evaluatedAt: isoAgo(3), status: 'COMPLETE' },
    marginFx: { currency: 'USDC', rateToJpy: '150', source: 'COINGECKO_AND_EXCHANGERATE_API', evaluatedAt: isoAgo(3), status: 'COMPLETE' },
    pnlFx: { currency: 'USDC', rateToJpy: '150', source: 'COINGECKO_AND_EXCHANGERATE_API', evaluatedAt: isoAgo(3), status: 'COMPLETE' },
    status: 'COMPLETE', connectionId: 'demo-hyperliquid', provider: 'HYPERLIQUID',
    connectionDisplayName: 'Hyperliquid · Demo', fetchedAt: isoAgo(3), lastSuccessAt: isoAgo(3),
  }],
}

function makeSwapActivity(): ActivityItem {
  return {
    id: 'demo-activity-swap', connectionId: 'demo-solana', provider: 'SOLANA', connectionDisplayName: 'Phantom · Demo',
    providerEventId: 'demo-solana-signature', eventType: 'SWAP', originalEventType: 'SWAP', status: 'CONFIRMED',
    occurredAt: isoAgo(5), importedAt: isoAgo(4.9), dataStatus: 'STALE', lastSuccessAt: isoAgo(27),
    legs: [
      { legIndex: 0, direction: 'OUT', assetKey: 'SOL', symbol: 'SOL', quantity: '1.2', originalAmount: '1.2', originalCurrency: 'SOL', jpyValue: '27900', valuationStatus: 'VALUED', valuationBasis: 'EVENT_TIME_MARKET', priceUsed: '155', priceCurrency: 'USD', priceSource: 'COINGECKO', priceEvaluatedAt: isoAgo(5), fxRateToJpy: '150', fxSource: 'EXCHANGERATE_API', fxEvaluatedAt: isoAgo(5) },
      { legIndex: 1, direction: 'IN', assetKey: 'USDC', symbol: 'USDC', quantity: '185.5', originalAmount: '185.5', originalCurrency: 'USDC', jpyValue: '27825', valuationStatus: 'VALUED', valuationBasis: 'EVENT_TIME_MARKET', priceUsed: '1', priceCurrency: 'USD', priceSource: 'COINGECKO', priceEvaluatedAt: isoAgo(5), fxRateToJpy: '150', fxSource: 'EXCHANGERATE_API', fxEvaluatedAt: isoAgo(5) },
      { legIndex: 2, direction: 'FEE', assetKey: 'SOL', symbol: 'SOL', quantity: '0.005', originalAmount: '0.005', originalCurrency: 'SOL', jpyValue: '116.25', valuationStatus: 'VALUED', valuationBasis: 'EVENT_TIME_MARKET', priceUsed: '155', priceCurrency: 'USD', priceSource: 'COINGECKO', priceEvaluatedAt: isoAgo(5), fxRateToJpy: '150', fxSource: 'EXCHANGERATE_API', fxEvaluatedAt: isoAgo(5) },
    ],
    perpetualFill: null,
  }
}

function makePerpetualActivity(): ActivityItem {
  return {
    id: 'demo-activity-perp', connectionId: 'demo-hyperliquid', provider: 'HYPERLIQUID', connectionDisplayName: 'Hyperliquid · Demo',
    providerEventId: 'demo-hyperliquid-fill', eventType: 'PERP', originalEventType: 'Order Filled', status: 'FILLED',
    occurredAt: isoAgo(9), importedAt: isoAgo(8.9), dataStatus: 'COMPLETE', lastSuccessAt: isoAgo(3), legs: [],
    perpetualFill: {
      instrumentCode: 'BTC', side: 'BUY', direction: 'OPEN_LONG', providerDirection: 'Open Long',
      quantity: '0.02', price: '86000', priceCurrency: 'USD', startPosition: '0', closedPnl: '0', closedPnlCurrency: 'USDC',
    },
  }
}

export const demoActivities: ActivitiesResponse = {
  summary: { status: 'STALE', connectionCount: 3, syncedConnectionCount: 3, lastSuccessAt: isoAgo(2) },
  activities: [makeSwapActivity(), makePerpetualActivity()],
  nextCursor: null,
  hasMore: false,
}

export function demoHistory(period: PortfolioHistoryPeriod): PortfolioHistoryResponse {
  const periodDays: Record<PortfolioHistoryPeriod, number> = { '7D': 7, '30D': 30, '90D': 90, '1Y': 365 }
  const duration = periodDays[period] * 24 * 60 * 60 * 1000
  const rangeStart = now - duration
  const pointOffsets = [0.04, 0.36, 0.67, 0.9]
  const values = ['10800000', '11050000', '11180000', '11330000']
  const points = pointOffsets.map((offset, index) => {
    const snapshotAt = new Date(rangeStart + duration * offset).toISOString()
    return {
      snapshotAt,
      dataAsOfAt: snapshotAt,
      netWorthJpy: values[index],
      status: index === 1 ? 'STALE' as const : 'COMPLETE' as const,
    }
  })
  return {
    period,
    status: 'AVAILABLE',
    rangeStartAt: new Date(rangeStart).toISOString(),
    rangeEndAt: new Date(now).toISOString(),
    points,
  }
}
