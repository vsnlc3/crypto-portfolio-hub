'use client'

import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import { DataStatusBadge, EmptyCard, InlineError, LoadingCard } from '@/components/dashboard/data-state'
import { Delta } from '@/components/delta'
import { ServiceBadge } from '@/components/service-badge'
import { TokenBadge } from '@/components/token-badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { assetsQueryKey, getAssets, type Asset } from '@/lib/assets-api'
import { decimalRatioPercent } from '@/lib/decimal'
import { formatAmount, formatDateTime, formatJpy, formatMoney, formatPercent } from '@/lib/format'
import { cn } from '@/lib/utils'

function AssetRow({ asset, totalValueJpy }: { asset: Asset; totalValueJpy: string | null }) {
  const share = asset.valueJpy !== null && totalValueJpy !== null
    ? decimalRatioPercent(asset.valueJpy, totalValueJpy)
    : null
  const change = asset.change24h.value

  return (
    <li className="flex flex-wrap items-center gap-3 py-3 first:pt-0 last:pb-0 sm:gap-4">
      <TokenBadge symbol={asset.symbol} size={38} />
      <div className="min-w-0 flex-1 basis-28">
        <div className="flex items-center gap-2">
          <span className="font-medium">{asset.symbol}</span>
          <span className="truncate text-xs text-muted-foreground">{asset.name}</span>
          <DataStatusBadge status={asset.status} />
        </div>
        <div className="mt-1 flex items-center gap-1.5">
          {asset.connections.map((connection) => (
            <ServiceBadge key={connection.connectionId} provider={connection.provider} size={18} />
          ))}
          <span className="text-[11px] text-muted-foreground">
            {asset.connections.length === 0 ? 'Source unavailable' : `${asset.connections.length} ${asset.connections.length === 1 ? 'source' : 'sources'}`}
          </span>
        </div>
      </div>
      <div className="min-w-24 text-right">
        <p className="font-mono text-sm tabular">
          {asset.totalQuantity === null ? 'Unavailable' : formatAmount(asset.totalQuantity, asset.symbol)}
        </p>
        <p className="text-[11px] text-muted-foreground">@ {formatMoney(asset.price.amount, asset.price.currency)}</p>
        {asset.price.evaluatedAt && <p className="text-[10px] text-muted-foreground">{formatDateTime(asset.price.evaluatedAt)}</p>}
      </div>
      <div className="min-w-28 text-right">
        <p className="font-mono text-sm font-semibold tabular">{formatJpy(asset.valueJpy, true)}</p>
        <div className="mt-0.5 flex items-center justify-end gap-2">
          {change !== null ? (
            <Delta value={change} showIcon={false} className="text-[11px]">{formatPercent(change)}</Delta>
          ) : (
            <span className={cn('text-[11px]', asset.change24h.status === 'STALE' ? 'text-amber-400' : 'text-muted-foreground')}>
              {asset.change24h.status === 'STALE' ? '24h stale' : '24h unavailable'}
            </span>
          )}
          <span className="text-[11px] text-muted-foreground">{share === null ? 'share unavailable' : formatPercent(share, 1)}</span>
        </div>
      </div>
    </li>
  )
}

export function CurrencyList() {
  const query = useQuery({ queryKey: assetsQueryKey, queryFn: getAssets })
  if (query.isPending && !query.data) return <LoadingCard label="assets" />
  if (query.isError && !query.data) {
    return <Card className="p-6"><InlineError message="Assets couldn’t be loaded." onRetry={() => void query.refetch()} /></Card>
  }

  const response = query.data!
  if (response.summary.connectionCount === 0) {
    return (
      <EmptyCard
        title="No connected services"
        detail="Connect a service to see your current asset balances."
        action={<Button variant="outline" nativeButton={false} render={<Link href="/connections" />}>View connections</Button>}
      />
    )
  }
  if (response.assets.length === 0) {
    return <EmptyCard title="No asset balances" detail="A successful balance sync has not returned any holdings." />
  }

  return (
    <Card className="gap-0 p-6">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2"><p className="text-sm text-muted-foreground">Holdings by currency</p><DataStatusBadge status={response.summary.status} /></div>
        <p className="text-[11px] text-muted-foreground">{response.assets.length} assets · {formatJpy(response.summary.spotHoldingsValueJpy, true)} spot</p>
      </div>
      {query.isRefetchError && <div className="mb-3"><InlineError message="Assets couldn’t be refreshed. Showing the last loaded data." onRetry={() => void query.refetch()} /></div>}
      {response.summary.status === 'PARTIAL' && <p role="status" className="mb-3 text-xs text-amber-400">Some quantities or valuations are unavailable; totals and shares remain blank where incomplete.</p>}
      {response.summary.status === 'STALE' && <p role="status" className="mb-3 text-xs text-amber-400">Showing the last successful balance or valuation data.</p>}

      <ul className="divide-y divide-border/70">
        {response.assets.map((asset) => (
          <AssetRow key={asset.assetId} asset={asset} totalValueJpy={response.summary.spotHoldingsValueJpy} />
        ))}
      </ul>
    </Card>
  )
}
