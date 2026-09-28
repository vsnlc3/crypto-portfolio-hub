'use client'

import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import { DataStatusBadge, EmptyCard, InlineError, LoadingCard } from '@/components/dashboard/data-state'
import { ServiceBadge } from '@/components/service-badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { decimalRatioPercent, decimalSum, decimalToNumber } from '@/lib/decimal'
import { formatJpy, formatPercent, formatRelative, formatSignedJpy } from '@/lib/format'
import {
  getPortfolioSummary,
  portfolioSummaryQueryKey,
  type PortfolioConnection,
} from '@/lib/portfolio-api'
import { getPositions, positionsQueryKey, type PositionsResponse } from '@/lib/positions-api'

function connectionPnl(connection: PortfolioConnection, positions: PositionsResponse | undefined) {
  if (connection.provider !== 'HYPERLIQUID') return null
  if (!positions) return null
  const connectionPositions = positions.positions.filter((position) => position.connectionId === connection.id)
  if (connectionPositions.length > 0) {
    if (connectionPositions.some((position) => position.unrealizedPnlJpy === null)) return null
    return decimalSum(connectionPositions.map((position) => position.unrealizedPnlJpy!))
  }
  const positionCapability = connection.capabilitySync.find((capability) => capability.capability === 'POSITION')
  return positionCapability?.lastSuccessAt ? 0 : null
}

function capabilityLabel(value: string) {
  return value === 'BALANCE' ? 'Spot' : value === 'POSITION' ? 'Perpetual' : value.toLowerCase()
}

export function ServiceCards() {
  const summaryQuery = useQuery({ queryKey: portfolioSummaryQueryKey, queryFn: getPortfolioSummary })
  const positionsQuery = useQuery({ queryKey: positionsQueryKey, queryFn: getPositions })

  if (summaryQuery.isPending && !summaryQuery.data) return <LoadingCard label="connections" />
  if (summaryQuery.isError && !summaryQuery.data) {
    return <Card className="p-5"><InlineError message="Connection portfolio data couldn’t be loaded." onRetry={() => void summaryQuery.refetch()} /></Card>
  }
  const connections = summaryQuery.data?.connections ?? []
  if (connections.length === 0) {
    return (
      <EmptyCard
        title="No connected services"
        detail="Connect bitbank, a Solana wallet, or Hyperliquid to see service values here."
        action={<Button variant="outline" nativeButton={false} render={<Link href="/connections" />}>View connections</Button>}
      />
    )
  }

  const netWorth = summaryQuery.data?.summary.netWorthJpy ?? null
  return (
    <div className="space-y-3">
      {summaryQuery.isRefetchError && <InlineError message="Connection values couldn’t be refreshed. Showing the last loaded data." onRetry={() => void summaryQuery.refetch()} />}
      {positionsQuery.isError && !positionsQuery.data && <InlineError message="Perpetual PnL could not be loaded." onRetry={() => void positionsQuery.refetch()} />}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {connections.map((connection) => {
          const sharePercent = connection.netWorthJpy !== null && netWorth !== null
            ? decimalRatioPercent(connection.netWorthJpy, netWorth)
            : null
          const shareWidth = sharePercent === null ? null : decimalToNumber(sharePercent)
          const pnl = connectionPnl(connection, positionsQuery.data)
          return (
            <Card key={connection.id} className="gap-0 p-5">
              <div className="flex items-start justify-between gap-3">
                <div className="flex min-w-0 items-center gap-3">
                  <ServiceBadge provider={connection.provider} size={38} />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold">{connection.displayName || connection.provider}</p>
                    <p className="text-[11px] uppercase tracking-wide text-muted-foreground">{connection.provider}</p>
                  </div>
                </div>
                <DataStatusBadge status={connection.dataStatus} />
              </div>

              <p className="mt-4 font-mono text-2xl font-semibold tabular">{formatJpy(connection.netWorthJpy, true)}</p>

              <div className="mt-3 space-y-2">
                <div className="h-1.5 w-full overflow-hidden rounded-full bg-accent">
                  {shareWidth !== null && <div className="h-full rounded-full bg-primary" style={{ width: `${Math.min(100, Math.max(0, shareWidth))}%` }} />}
                </div>
                <div className="flex items-center justify-between text-[11px] text-muted-foreground">
                  <span>{sharePercent === null ? 'Share unavailable' : `${formatPercent(sharePercent, 1)} of portfolio`}</span>
                  <span title={connection.lastSuccessfulSyncAt ?? undefined}>{formatRelative(connection.lastSuccessfulSyncAt)}</span>
                </div>
              </div>

              <div className="mt-3 flex flex-wrap gap-1.5">
                {connection.capabilitySync.map((capability) => (
                  <span key={capability.capability} title={`${capability.capability}: ${capability.status}`} className="rounded-md bg-accent/60 px-1.5 py-0.5 text-[10px] text-muted-foreground">
                    {capabilityLabel(capability.capability)} · {capability.status === 'READY' ? 'ready' : capability.status.toLowerCase()}
                  </span>
                ))}
              </div>

              <div className="mt-3 flex items-center justify-between gap-2 border-t border-border pt-3 text-[11px]">
                <span className="text-muted-foreground">24h change unavailable</span>
                {connection.provider === 'HYPERLIQUID' && (
                  <span className="font-mono tabular">
                    PnL {pnl === null ? 'Unavailable' : formatSignedJpy(pnl, true)}
                  </span>
                )}
              </div>
            </Card>
          )
        })}
      </div>
    </div>
  )
}
