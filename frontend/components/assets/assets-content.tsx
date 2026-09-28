'use client'

import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, CircleDollarSign, RefreshCw, WalletCards } from 'lucide-react'
import Link from 'next/link'
import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts'
import { CoinGeckoAttribution } from '@/components/coingecko-attribution'
import { PageHeader } from '@/components/page-header'
import { ServiceBadge } from '@/components/service-badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { assetsQueryKey, getAssets, type Asset, type AssetDataStatus, type ConnectionProvider } from '@/lib/assets-api'
import { fmtAmount, fmtDateTime, fmtPct } from '@/lib/mock-data'
import { cn } from '@/lib/utils'

const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'JPY',
  maximumFractionDigits: 0,
})

const tokenColors: Record<string, string> = {
  BTC: 'oklch(0.78 0.14 60)',
  ETH: 'oklch(0.7 0.09 260)',
  SOL: 'oklch(0.74 0.16 300)',
  XRP: 'oklch(0.72 0.03 255)',
  HYPE: 'oklch(0.8 0.15 164)',
  USDC: 'oklch(0.68 0.1 250)',
  USDT: 'oklch(0.72 0.15 165)',
}

const serviceProvider: Record<ConnectionProvider, 'bitbank' | 'phantom' | 'hyperliquid'> = {
  BITBANK: 'bitbank',
  SOLANA: 'phantom',
  HYPERLIQUID: 'hyperliquid',
}

const providerName: Record<ConnectionProvider, string> = {
  BITBANK: 'bitbank',
  SOLANA: 'Phantom',
  HYPERLIQUID: 'Hyperliquid',
}

function formatJpy(value: number | null) {
  return value === null ? 'Unavailable' : currencyFormatter.format(value)
}

function formatPrice(amount: number | null, currency: string | null) {
  if (amount === null || !currency) return 'Unavailable'
  if (/^[A-Z]{3}$/.test(currency)) {
    try {
      return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency,
        maximumFractionDigits: amount >= 1000 ? 0 : 4,
      }).format(amount)
    } catch {
      // Display provider-defined codes as a value and an explicit currency label.
    }
  }
  return `${fmtAmount(amount)} ${currency}`
}

function formatSource(source: string | null) {
  if (source === 'COINGECKO') return 'CoinGecko'
  if (source === 'EXCHANGERATE_API') return 'ExchangeRate API'
  if (source === 'COINGECKO_AND_EXCHANGERATE_API') return 'CoinGecko + ExchangeRate API'
  if (source === 'IDENTITY') return 'Identity rate'
  return source ?? 'Source unavailable'
}

function statusLabel(status: AssetDataStatus) {
  return status === 'COMPLETE' ? 'Fresh' : status[0] + status.slice(1).toLowerCase()
}

function StatusBadge({ status }: { status: AssetDataStatus }) {
  const styles: Record<AssetDataStatus, string> = {
    COMPLETE: 'bg-positive/12 text-positive',
    STALE: 'bg-amber-500/12 text-amber-400',
    PARTIAL: 'bg-amber-500/12 text-amber-400',
    UNAVAILABLE: 'bg-muted text-muted-foreground',
  }
  return <span className={cn('rounded-full px-2 py-0.5 text-[10px] font-medium', styles[status])}>{statusLabel(status)}</span>
}

function AssetToken({ symbol }: { symbol: string }) {
  const color = tokenColors[symbol] ?? 'oklch(0.7 0.04 255)'
  return (
    <span
      aria-hidden="true"
      className="inline-flex size-[38px] shrink-0 items-center justify-center rounded-full border font-mono text-xs font-semibold tabular"
      style={{
        color,
        backgroundColor: `color-mix(in oklch, ${color} 16%, transparent)`,
        borderColor: `color-mix(in oklch, ${color} 32%, transparent)`,
      }}
    >
      {symbol.slice(0, 4)}
    </span>
  )
}

function Stat({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <Card className="gap-0 p-5">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 font-mono text-2xl font-semibold tabular">{value}</p>
      <p className="mt-1 text-[11px] text-muted-foreground">{hint}</p>
    </Card>
  )
}

function StateNotice({ status, connectionCount, syncedConnectionCount }: {
  status: AssetDataStatus
  connectionCount: number
  syncedConnectionCount: number
}) {
  if (status === 'COMPLETE' || connectionCount === 0) return null
  const description = status === 'STALE'
    ? 'Showing the last successful valuation. A balance sync, price, or FX input is stale.'
    : status === 'PARTIAL'
      ? `Some balances or valuation inputs are missing. ${syncedConnectionCount} of ${connectionCount} connections have a successful balance sync.`
      : 'Portfolio values are unavailable until balance sync and valuation data are available.'
  return (
    <div
      role={status === 'PARTIAL' || status === 'UNAVAILABLE' ? 'alert' : 'status'}
      className={cn(
        'mb-4 flex items-start gap-2 rounded-lg border px-4 py-3 text-sm',
        status === 'STALE'
          ? 'border-amber-500/30 bg-amber-500/5 text-amber-300'
          : 'border-destructive/30 bg-destructive/5 text-destructive',
      )}
    >
      <AlertTriangle className="mt-0.5 size-4 shrink-0" />
      <div className="min-w-0 flex-1">
        <p className="font-medium">{statusLabel(status)} asset data</p>
        <p className="mt-0.5 text-xs opacity-90">{description}</p>
      </div>
      <Link className="text-xs font-medium underline underline-offset-4" href="/connections">Connections</Link>
    </div>
  )
}

function AllocationCard({ assets, totalValue }: { assets: Asset[]; totalValue: number | null }) {
  const valuedAssets = assets.filter((asset) => asset.valueJpy !== null && asset.valueJpy > 0)
  const valuedTotal = valuedAssets.reduce((total, asset) => total + (asset.valueJpy ?? 0), 0)
  const data = valuedAssets.map((asset, index) => ({
    id: asset.assetId,
    name: asset.symbol,
    value: asset.valueJpy ?? 0,
    color: tokenColors[asset.symbol] ?? `oklch(0.68 0.08 ${index * 61})`,
  }))

  return (
    <Card className="gap-0 p-6">
      <p className="text-sm text-muted-foreground">Allocation by currency</p>
      {data.length === 0 ? (
        <div className="mt-6 flex min-h-40 items-center justify-center text-sm text-muted-foreground">
          Asset allocation is unavailable.
        </div>
      ) : (
        <div className="mt-4 flex items-center gap-6">
          <div className="relative size-40 shrink-0">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={data} dataKey="value" nameKey="name" innerRadius={52} outerRadius={78} paddingAngle={2} stroke="none">
                  {data.map((item) => <Cell key={item.id} fill={item.color} />)}
                </Pie>
              </PieChart>
            </ResponsiveContainer>
            <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
              <span className="text-[11px] text-muted-foreground">Spot assets</span>
              <span className="font-mono text-base font-semibold tabular">{formatJpy(totalValue)}</span>
            </div>
          </div>
          <ul className="min-w-0 flex-1 space-y-2.5">
            {data.map((item) => (
              <li key={item.id} className="flex items-center gap-2 text-sm">
                <span className="size-2.5 shrink-0 rounded-full" style={{ backgroundColor: item.color }} />
                <span className="font-medium">{item.name}</span>
                <span className="ml-auto font-mono text-xs tabular text-muted-foreground">
                  {valuedTotal === 0 ? '—' : `${((item.value / valuedTotal) * 100).toFixed(1)}%`}
                </span>
                <span className="w-24 text-right font-mono text-xs tabular">{formatJpy(item.value)}</span>
              </li>
            ))}
            {totalValue === null && (
              <li className="pt-1 text-[11px] text-amber-300">Known valued assets only; total is incomplete.</li>
            )}
          </ul>
        </div>
      )}
    </Card>
  )
}

function CurrencyList({ assets }: { assets: Asset[] }) {
  return (
    <Card className="gap-0 p-6">
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-muted-foreground">Holdings by currency</p>
        <p className="text-[11px] text-muted-foreground">{assets.length} assets</p>
      </div>
      {assets.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-foreground">No spot assets found.</p>
      ) : (
        <ul className="divide-y divide-border/70">
          {assets.map((asset) => <AssetRow key={asset.assetId} asset={asset} />)}
        </ul>
      )}
    </Card>
  )
}

function AssetRow({ asset }: { asset: Asset }) {
  const availableConnections = asset.connections
  const change = asset.change24h.value
  const knownQuantity = availableConnections
    .map((connection) => connection.quantity)
    .filter((quantity): quantity is number => quantity !== null)
    .reduce((total, quantity) => total + quantity, 0)
  const knownValueJpy = availableConnections
    .map((connection) => connection.valueJpy)
    .filter((value): value is number => value !== null)
    .reduce((total, value) => total + value, 0)
  const hasKnownValue = availableConnections.some((connection) => connection.valueJpy !== null)
  return (
    <li className="grid gap-3 py-3 first:pt-0 last:pb-0 sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:items-center">
      <div className="flex min-w-0 items-center gap-3">
        <AssetToken symbol={asset.symbol} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{asset.symbol}</span>
            <span className="truncate text-xs text-muted-foreground">{asset.name}</span>
            <StatusBadge status={asset.status} />
          </div>
          <div className="mt-1 flex items-center gap-1.5">
            {availableConnections.map((connection) => (
              <span key={connection.connectionId} title={connection.displayName || providerName[connection.provider]}>
                <ServiceBadge id={serviceProvider[connection.provider]} size={16} />
              </span>
            ))}
            <span className="text-[11px] text-muted-foreground">
              {availableConnections.length} {availableConnections.length === 1 ? 'source' : 'sources'}
            </span>
            {asset.network && <span className="ml-1 text-[11px] text-muted-foreground">· {asset.network}</span>}
          </div>
          <div className="mt-1 text-[10px] text-muted-foreground">
            {asset.valuation.fxSource && <>FX {formatSource(asset.valuation.fxSource)}{asset.valuation.fxEvaluatedAt ? ` · ${fmtDateTime(asset.valuation.fxEvaluatedAt)}` : ''}</>}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4 text-right sm:block sm:min-w-36">
        <div>
          <p className="font-mono text-sm tabular">
            {asset.totalQuantity === null
              ? hasKnownValue ? `Known: ${fmtAmount(knownQuantity, asset.symbol)}` : 'Partial quantity unavailable'
              : fmtAmount(asset.totalQuantity, asset.symbol)}
          </p>
          <p className="text-[11px] text-muted-foreground">
            @ {formatPrice(asset.price.amount, asset.price.currency)}
          </p>
        </div>
        <div className="sm:mt-0.5">
          {change === null ? (
            <span className="text-[11px] text-muted-foreground">
              {asset.change24h.status === 'STALE' ? '24h stale' : '24h unavailable'}
            </span>
          ) : (
            <span className={cn('font-mono text-[11px] tabular', change >= 0 ? 'text-positive' : 'text-negative')}>
              {fmtPct(change)} <span className="text-muted-foreground">24h</span>
            </span>
          )}
          <p className="text-[10px] text-muted-foreground">
            {formatSource(asset.change24h.source)}{asset.change24h.evaluatedAt ? ` · ${fmtDateTime(asset.change24h.evaluatedAt)}` : ''}
          </p>
        </div>
      </div>

      <div className="text-right sm:min-w-32">
        <p className="font-mono text-sm font-semibold tabular">
          {asset.valueJpy !== null
            ? formatJpy(asset.valueJpy)
            : hasKnownValue ? `Known: ${formatJpy(knownValueJpy)}` : 'Unavailable'}
        </p>
        <p className="mt-0.5 text-[10px] text-muted-foreground">Valuation in JPY</p>
      </div>
    </li>
  )
}

function LoadingState() {
  return (
    <div className="mx-auto max-w-[1400px]" aria-label="Loading assets" aria-busy="true">
      <PageHeader title="Assets" subtitle="Every currency you hold, aggregated across all connected sources." />
      <div className="grid gap-4 sm:grid-cols-3">
        {['Spot holdings', 'Directional', 'Stablecoins'].map((label) => (
          <Card key={label} className="gap-3 p-5">
            <p className="text-xs text-muted-foreground">{label}</p>
            <div className="h-8 w-36 animate-pulse rounded bg-muted" />
            <div className="h-3 w-24 animate-pulse rounded bg-muted" />
          </Card>
        ))}
      </div>
      <Card className="mt-4 h-72 animate-pulse p-6" />
    </div>
  )
}

function LoadError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="mx-auto max-w-[1400px]">
      <PageHeader title="Assets" subtitle="Every currency you hold, aggregated across all connected sources." />
      <Card className="items-center gap-3 p-10 text-center">
        <AlertTriangle className="size-6 text-destructive" />
        <p role="alert" className="text-sm text-muted-foreground">Assets couldn’t be loaded. Check your connections and try again.</p>
        <Button variant="outline" onClick={onRetry}>Try again</Button>
      </Card>
    </div>
  )
}

function EmptyState({ hasConnections }: { hasConnections: boolean }) {
  return (
    <Card className="items-center gap-3 px-6 py-12 text-center">
      {hasConnections ? <CircleDollarSign className="size-7 text-muted-foreground" /> : <WalletCards className="size-7 text-muted-foreground" />}
      <p className="font-medium">{hasConnections ? 'No spot assets found' : 'No connections yet'}</p>
      <p className="max-w-md text-sm text-muted-foreground">
        {hasConnections
          ? 'A successful balance sync found no spot holdings.'
          : 'Connect an exchange or wallet to see your assets here.'}
      </p>
      <Button render={<Link href="/connections" />}>{hasConnections ? 'View connections' : 'Connect a source'}</Button>
    </Card>
  )
}

function RefreshFailure({ onRetry }: { onRetry: () => void }) {
  return (
    <div role="alert" className="mb-4 flex items-center gap-3 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
      <AlertTriangle className="size-4 shrink-0" />
      <p className="flex-1">Assets couldn’t be refreshed. Showing the last successful data.</p>
      <Button variant="outline" size="sm" onClick={onRetry}>Try again</Button>
    </div>
  )
}

export function AssetsContent() {
  const assetsQuery = useQuery({ queryKey: assetsQueryKey, queryFn: getAssets })

  if (assetsQuery.isPending) return <LoadingState />
  if (assetsQuery.isError && !assetsQuery.data) return <LoadError onRetry={() => void assetsQuery.refetch()} />

  const data = assetsQuery.data!
  const { summary, assets } = data

  return (
    <div className="mx-auto max-w-[1400px]">
      <PageHeader
        title="Assets"
        subtitle="Every currency you hold, aggregated across all connected sources."
      />

      {assetsQuery.isRefetchError && <RefreshFailure onRetry={() => void assetsQuery.refetch()} />}
      <StateNotice
        status={summary.status}
        connectionCount={summary.connectionCount}
        syncedConnectionCount={summary.syncedConnectionCount}
      />

      {assets.length === 0 ? (
        <EmptyState hasConnections={summary.connectionCount > 0} />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <Stat
              label="Spot holdings"
              value={formatJpy(summary.spotHoldingsValueJpy)}
              hint={summary.status === 'COMPLETE' || summary.status === 'STALE'
                ? `Across ${summary.connectionCount} sources`
                : 'Complete total unavailable'}
            />
            <Stat
              label="Directional"
              value={formatJpy(summary.directionalAssetsValueJpy)}
              hint="Crypto assets, excluding stablecoins and fiat"
            />
            <Stat label="Stablecoins" value={formatJpy(summary.stablecoinsValueJpy)} hint="Stablecoin spot balances" />
          </div>

          <div className="mt-4 grid gap-4 lg:grid-cols-12">
            <div className="lg:col-span-5">
              <AllocationCard assets={assets} totalValue={summary.spotHoldingsValueJpy} />
            </div>
            <div className="lg:col-span-7">
              <CurrencyList assets={assets} />
            </div>
          </div>
        </>
      )}

      <div className="mt-6 flex justify-end">
        <CoinGeckoAttribution />
      </div>
      {assetsQuery.isFetching && !assetsQuery.isPending && (
        <p className="mt-3 flex items-center justify-end gap-1.5 text-[11px] text-muted-foreground" role="status">
          <RefreshCw className="size-3 animate-spin" /> Refreshing asset data
        </p>
      )}
    </div>
  )
}
