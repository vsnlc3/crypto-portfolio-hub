// -----------------------------------------------------------------------------
// Mock data for the unified crypto portfolio dashboard.
//
// The shape is intentionally source-agnostic: a `services` registry describes
// every connected venue (exchange / wallet / defi), and everything else
// (holdings, positions, activity) references a service by id. Adding a new
// exchange or wallet later means adding one entry here — no UI is hard-coded to
// bitbank / Phantom / Hyperliquid specifically.
// -----------------------------------------------------------------------------

export type ServiceId = "bitbank" | "phantom" | "hyperliquid"
export type ServiceKind = "exchange" | "wallet" | "defi"
export type ConnectionStatus = "connected" | "syncing" | "error" | "disconnected"

export type Service = {
  id: ServiceId
  name: string
  kind: ServiceKind
  /** short label for the monogram token, e.g. "bb" */
  mark: string
  /** css color used for the venue accent */
  color: string
  status: ConnectionStatus
  lastSync: string // ISO
  accountRef: string
  capabilities: ("spot" | "perp")[]
}

export type AssetSymbol = "BTC" | "ETH" | "SOL" | "XRP" | "HYPE" | "USDC"

export type AssetMeta = {
  symbol: AssetSymbol
  name: string
  priceUsd: number
  change24hPct: number
  color: string
  stable?: boolean
}

export type Holding = {
  service: ServiceId
  symbol: AssetSymbol
  amount: number
}

export type PerpPosition = {
  service: ServiceId
  symbol: string
  side: "long" | "short"
  leverage: number
  size: number // in base asset
  entryPrice: number
  markPrice: number
  liquidationPrice: number
}

export type Activity = {
  id: string
  service: ServiceId
  type: "buy" | "sell" | "deposit" | "withdraw" | "transfer" | "perp" | "funding"
  title: string
  symbol: AssetSymbol | string
  amount: number
  valueUsd: number
  timestamp: string // ISO
  status: "completed" | "pending"
}

// -----------------------------------------------------------------------------

export const services: Record<ServiceId, Service> = {
  bitbank: {
    id: "bitbank",
    name: "bitbank",
    kind: "exchange",
    mark: "bb",
    color: "oklch(0.72 0.14 250)",
    status: "connected",
    lastSync: "2026-09-24T08:41:00Z",
    accountRef: "API key ••••4c2a",
    capabilities: ["spot"],
  },
  phantom: {
    id: "phantom",
    name: "Phantom Wallet",
    kind: "wallet",
    mark: "Ph",
    color: "oklch(0.72 0.16 300)",
    status: "connected",
    lastSync: "2026-09-24T08:39:00Z",
    accountRef: "7xKX…9fA2",
    capabilities: ["spot"],
  },
  hyperliquid: {
    id: "hyperliquid",
    name: "Hyperliquid",
    kind: "defi",
    mark: "HL",
    color: "oklch(0.8 0.15 164)",
    status: "syncing",
    lastSync: "2026-09-24T08:44:00Z",
    accountRef: "0x3a…B71c",
    capabilities: ["spot", "perp"],
  },
}

export const serviceList: Service[] = Object.values(services)

export const assets: Record<AssetSymbol, AssetMeta> = {
  BTC: { symbol: "BTC", name: "Bitcoin", priceUsd: 64000, change24hPct: 1.4, color: "oklch(0.78 0.14 60)" },
  ETH: { symbol: "ETH", name: "Ethereum", priceUsd: 3100, change24hPct: -0.8, color: "oklch(0.7 0.09 260)" },
  SOL: { symbol: "SOL", name: "Solana", priceUsd: 145, change24hPct: 5.2, color: "oklch(0.74 0.16 300)" },
  XRP: { symbol: "XRP", name: "XRP", priceUsd: 0.62, change24hPct: 0.6, color: "oklch(0.72 0.03 255)" },
  HYPE: { symbol: "HYPE", name: "Hyperliquid", priceUsd: 28, change24hPct: 3.9, color: "oklch(0.8 0.15 164)" },
  USDC: { symbol: "USDC", name: "USD Coin", priceUsd: 1, change24hPct: 0.0, color: "oklch(0.68 0.1 250)", stable: true },
}

export const holdings: Holding[] = [
  { service: "bitbank", symbol: "XRP", amount: 12000 },
  { service: "bitbank", symbol: "BTC", amount: 0.15 },
  { service: "bitbank", symbol: "ETH", amount: 1.8 },
  { service: "bitbank", symbol: "USDC", amount: 1200 },
  { service: "phantom", symbol: "SOL", amount: 85 },
  { service: "phantom", symbol: "HYPE", amount: 40 },
  { service: "phantom", symbol: "USDC", amount: 800 },
  { service: "hyperliquid", symbol: "HYPE", amount: 120 },
  { service: "hyperliquid", symbol: "USDC", amount: 8500 },
]

export const positions: PerpPosition[] = [
  {
    service: "hyperliquid",
    symbol: "BTC-PERP",
    side: "long",
    leverage: 5,
    size: 0.3,
    entryPrice: 61000,
    markPrice: 64000,
    liquidationPrice: 49850,
  },
  {
    service: "hyperliquid",
    symbol: "SOL-PERP",
    side: "short",
    leverage: 3,
    size: 60,
    entryPrice: 152,
    markPrice: 145,
    liquidationPrice: 198.4,
  },
  {
    service: "hyperliquid",
    symbol: "ETH-PERP",
    side: "long",
    leverage: 10,
    size: 3,
    entryPrice: 3250,
    markPrice: 3100,
    liquidationPrice: 2960,
  },
]

export const activity: Activity[] = [
  { id: "a1", service: "hyperliquid", type: "perp", title: "Opened BTC-PERP long 5x", symbol: "BTC-PERP", amount: 0.3, valueUsd: 18300, timestamp: "2026-09-24T07:52:00Z", status: "completed" },
  { id: "a2", service: "phantom", type: "buy", title: "Swapped USDC → SOL", symbol: "SOL", amount: 12, valueUsd: 1740, timestamp: "2026-09-24T06:18:00Z", status: "completed" },
  { id: "a3", service: "bitbank", type: "buy", title: "Bought XRP", symbol: "XRP", amount: 2500, valueUsd: 1550, timestamp: "2026-09-23T22:05:00Z", status: "completed" },
  { id: "a4", service: "hyperliquid", type: "funding", title: "Funding payment SOL-PERP", symbol: "USDC", amount: -3.42, valueUsd: -3.42, timestamp: "2026-09-23T20:00:00Z", status: "completed" },
  { id: "a5", service: "phantom", type: "transfer", title: "Received HYPE", symbol: "HYPE", amount: 40, valueUsd: 1108, timestamp: "2026-09-23T15:41:00Z", status: "completed" },
  { id: "a6", service: "bitbank", type: "deposit", title: "JPY deposit → USDC", symbol: "USDC", amount: 1200, valueUsd: 1200, timestamp: "2026-09-23T11:12:00Z", status: "completed" },
  { id: "a7", service: "hyperliquid", type: "perp", title: "Reduced ETH-PERP long", symbol: "ETH-PERP", amount: 1, valueUsd: 3100, timestamp: "2026-09-22T19:27:00Z", status: "completed" },
  { id: "a8", service: "bitbank", type: "sell", title: "Sold ETH", symbol: "ETH", amount: 0.5, valueUsd: 1560, timestamp: "2026-09-22T14:03:00Z", status: "completed" },
  { id: "a9", service: "phantom", type: "withdraw", title: "Sent SOL to cold wallet", symbol: "SOL", amount: 10, valueUsd: 1450, timestamp: "2026-09-22T09:48:00Z", status: "pending" },
  { id: "a10", service: "hyperliquid", type: "deposit", title: "Bridged USDC to Hyperliquid", symbol: "USDC", amount: 5000, valueUsd: 5000, timestamp: "2026-09-21T18:30:00Z", status: "completed" },
  { id: "a11", service: "bitbank", type: "buy", title: "Bought BTC", symbol: "BTC", amount: 0.05, valueUsd: 3175, timestamp: "2026-09-21T10:11:00Z", status: "completed" },
  { id: "a12", service: "phantom", type: "buy", title: "Swapped SOL → HYPE", symbol: "HYPE", amount: 15, valueUsd: 420, timestamp: "2026-09-20T16:22:00Z", status: "completed" },
]

// 30-day net worth history (USD), ending at the current total.
export const netWorthHistory: { date: string; value: number }[] = [
  { date: "Aug 26", value: 44120 },
  { date: "Aug 29", value: 43980 },
  { date: "Sep 01", value: 45360 },
  { date: "Sep 04", value: 46010 },
  { date: "Sep 07", value: 45120 },
  { date: "Sep 10", value: 47230 },
  { date: "Sep 13", value: 46880 },
  { date: "Sep 16", value: 48540 },
  { date: "Sep 19", value: 47990 },
  { date: "Sep 22", value: 49610 },
  { date: "Sep 23", value: 49605 },
  { date: "Sep 24", value: 50795 },
]

// -----------------------------------------------------------------------------
// Derived selectors
// -----------------------------------------------------------------------------

export function holdingValue(h: Holding): number {
  return h.amount * assets[h.symbol].priceUsd
}

export function positionNotional(p: PerpPosition): number {
  return p.size * p.markPrice
}

export function positionMargin(p: PerpPosition): number {
  return positionNotional(p) / p.leverage
}

export function positionPnl(p: PerpPosition): number {
  const dir = p.side === "long" ? 1 : -1
  return (p.markPrice - p.entryPrice) * p.size * dir
}

export function positionPnlPct(p: PerpPosition): number {
  return (positionPnl(p) / positionMargin(p)) * 100
}

export function serviceSpotValue(id: ServiceId): number {
  return holdings.filter((h) => h.service === id).reduce((s, h) => s + holdingValue(h), 0)
}

export function servicePnl(id: ServiceId): number {
  return positions.filter((p) => p.service === id).reduce((s, p) => s + positionPnl(p), 0)
}

export function serviceTotalValue(id: ServiceId): number {
  return serviceSpotValue(id) + servicePnl(id)
}

export const totalSpotValue = serviceList.reduce((s, v) => s + serviceSpotValue(v.id), 0)
export const totalPnl = positions.reduce((s, p) => s + positionPnl(p), 0)
export const netWorth = totalSpotValue + totalPnl

export const stableValue = holdings
  .filter((h) => assets[h.symbol].stable)
  .reduce((s, h) => s + holdingValue(h), 0)

export const spotDirectionalValue = totalSpotValue - stableValue

// Gross perp notional across all venues (leverage-inclusive exposure).
export const perpNotional = positions.reduce((s, p) => s + positionNotional(p), 0)

// Net perp direction (long positive, short negative).
export const perpNetDirectional = positions.reduce(
  (s, p) => s + positionNotional(p) * (p.side === "long" ? 1 : -1),
  0,
)

// Market exposure = directional spot + gross perp notional.
export const marketExposure = spotDirectionalValue + perpNotional
export const exposureRatio = marketExposure / netWorth

// 24h change on net worth.
export const prevNetWorth = netWorthHistory[netWorthHistory.length - 2].value
export const change24hUsd = netWorth - prevNetWorth
export const change24hPct = (change24hUsd / prevNetWorth) * 100

export const serviceChange24h: Record<ServiceId, number> = {
  bitbank: 1.2,
  phantom: 4.8,
  hyperliquid: 0.9,
}

// Currency-level aggregation across all services.
export type AssetAggregate = {
  symbol: AssetSymbol
  meta: AssetMeta
  amount: number
  value: number
  breakdown: { service: ServiceId; amount: number; value: number }[]
}

export function assetAggregates(): AssetAggregate[] {
  const map = new Map<AssetSymbol, AssetAggregate>()
  for (const h of holdings) {
    const meta = assets[h.symbol]
    const value = holdingValue(h)
    if (!map.has(h.symbol)) {
      map.set(h.symbol, { symbol: h.symbol, meta, amount: 0, value: 0, breakdown: [] })
    }
    const agg = map.get(h.symbol)!
    agg.amount += h.amount
    agg.value += value
    agg.breakdown.push({ service: h.service, amount: h.amount, value })
  }
  return Array.from(map.values()).sort((a, b) => b.value - a.value)
}

// -----------------------------------------------------------------------------
// Formatters
// -----------------------------------------------------------------------------

export function fmtUsd(n: number, opts?: { compact?: boolean; decimals?: number }): string {
  const { compact, decimals } = opts ?? {}
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    notation: compact ? "compact" : "standard",
    minimumFractionDigits: decimals ?? (compact ? 0 : 2),
    maximumFractionDigits: decimals ?? 2,
  }).format(n)
}

export function fmtSignedUsd(n: number): string {
  const sign = n > 0 ? "+" : n < 0 ? "-" : ""
  return `${sign}${fmtUsd(Math.abs(n))}`
}

export function fmtPct(n: number): string {
  const sign = n > 0 ? "+" : ""
  return `${sign}${n.toFixed(2)}%`
}

export function fmtAmount(n: number, symbol?: string): string {
  const abs = Math.abs(n)
  const decimals = abs >= 1000 ? 2 : abs >= 1 ? 4 : 6
  const s = new Intl.NumberFormat("en-US", { maximumFractionDigits: decimals }).format(n)
  return symbol ? `${s} ${symbol}` : s
}

export function fmtDateTime(iso: string): string {
  return new Date(iso).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  })
}

export function fmtRelative(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  const mins = Math.round(diff / 60000)
  if (mins < 1) return "just now"
  if (mins < 60) return `${mins}m ago`
  const hrs = Math.round(mins / 60)
  if (hrs < 24) return `${hrs}h ago`
  const days = Math.round(hrs / 24)
  return `${days}d ago`
}
