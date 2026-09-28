'use client'

import { useQuery } from '@tanstack/react-query'
import { Gauge } from 'lucide-react'
import { DataStatusBadge, InlineError, LoadingCard } from '@/components/dashboard/data-state'
import { Delta } from '@/components/delta'
import { Card } from '@/components/ui/card'
import { getAssets, assetsQueryKey } from '@/lib/assets-api'
import { formatJpy, formatPercent, formatSignedJpy } from '@/lib/format'
import { getPortfolioSummary, portfolioSummaryQueryKey } from '@/lib/portfolio-api'
import { getPositions, positionsQueryKey } from '@/lib/positions-api'
import { decimalCompare, decimalRatioToNumber, decimalShift, decimalSum } from '@/lib/decimal'

const colors = {
  directional: 'var(--chart-2)',
  perpetual: 'var(--primary)',
  stablecoin: 'var(--chart-5)',
}

export function ExposureCard() {
  const summaryQuery = useQuery({ queryKey: portfolioSummaryQueryKey, queryFn: getPortfolioSummary })
  const assetsQuery = useQuery({ queryKey: assetsQueryKey, queryFn: getAssets })
  const positionsQuery = useQuery({ queryKey: positionsQueryKey, queryFn: getPositions })
  const summary = summaryQuery.data?.summary
  const segments = [
    { label: 'Directional spot', value: summary?.directionalValueJpy ?? null, color: colors.directional },
    { label: 'Perpetual position value', value: positionsQuery.data?.summary.positionValueJpy ?? null, color: colors.perpetual },
    { label: 'Stablecoins', value: assetsQuery.data?.summary.stablecoinsValueJpy ?? null, color: colors.stablecoin },
  ]
  const total = segments.every((segment) => segment.value !== null)
    ? decimalSum(segments.map((segment) => segment.value!))
    : null
  const canDrawBar = total !== null && decimalCompare(total, '0') > 0
  const exposurePercent = summary?.exposureRatio === null || summary?.exposureRatio === undefined
    ? null
    : decimalShift(summary.exposureRatio, 2)

  if (summaryQuery.isPending && !summaryQuery.data) return <LoadingCard label="exposure" />

  return (
    <Card className="flex flex-col gap-5 p-6">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">Holdings vs. market exposure</p>
        <div className="flex items-center gap-2">
          {summary && <DataStatusBadge status={summary.status} />}
          <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-accent/50 px-2 py-0.5 text-xs font-medium">
            <Gauge className="size-3.5 text-primary" />
            {exposurePercent === null
              ? 'Ratio unavailable'
              : `Exposure ${formatPercent(exposurePercent)}`}
          </span>
        </div>
      </div>

      {summaryQuery.isError && !summaryQuery.data && (
        <InlineError message="Portfolio exposure couldn’t be loaded." onRetry={() => void summaryQuery.refetch()} />
      )}
      {summaryQuery.isRefetchError && (
        <InlineError message="Summary refresh failed. Showing the last loaded values." onRetry={() => void summaryQuery.refetch()} />
      )}

      <div className="grid grid-cols-2 gap-4">
        <div className="rounded-xl border border-border bg-background/40 p-4">
          <p className="text-xs text-muted-foreground">Holdings value</p>
          <p className="mt-1 font-mono text-xl font-semibold tabular">{formatJpy(summary?.holdingsValueJpy ?? null, true)}</p>
          <p className="mt-1 text-[11px] text-muted-foreground">Spot and cash</p>
        </div>
        <div className="rounded-xl border border-primary/25 bg-primary/5 p-4">
          <p className="text-xs text-muted-foreground">Market exposure</p>
          <p className="mt-1 font-mono text-xl font-semibold tabular text-primary">
            {formatJpy(summary?.marketExposureJpy ?? null, true)}
          </p>
          <p className="mt-1 text-[11px] text-muted-foreground">Directional spot + gross positions</p>
        </div>
      </div>

      <div className="space-y-3">
        {canDrawBar ? (
          <div aria-label="Exposure composition" className="flex h-2.5 w-full overflow-hidden rounded-full">
            {segments.map((segment) => (
              <div key={segment.label} style={{ width: `${Math.max(0, (decimalRatioToNumber(segment.value!, total!) ?? 0) * 100)}%`, backgroundColor: segment.color }} />
            ))}
          </div>
        ) : (
          <p className="text-xs text-muted-foreground">Composition is partial while one or more category totals are unavailable.</p>
        )}
        <ul className="space-y-1.5">
          {segments.map((segment) => (
            <li key={segment.label} className="flex items-center justify-between gap-3 text-xs">
              <span className="flex items-center gap-2 text-muted-foreground">
                <span className="size-2 shrink-0 rounded-full" style={{ backgroundColor: segment.color }} />
                {segment.label}
              </span>
              <span className="font-mono tabular text-foreground">{formatJpy(segment.value, true)}</span>
            </li>
          ))}
        </ul>
        {(assetsQuery.isError || positionsQuery.isError) && (
          <div className="space-y-2">
            {assetsQuery.isError && !assetsQuery.data && (
              <InlineError message="Stablecoin breakdown couldn’t be loaded." onRetry={() => void assetsQuery.refetch()} />
            )}
            {positionsQuery.isError && !positionsQuery.data && (
              <InlineError message="Perpetual exposure couldn’t be loaded." onRetry={() => void positionsQuery.refetch()} />
            )}
          </div>
        )}
      </div>

      <div className="flex items-center justify-between border-t border-border pt-4">
        <p className="text-sm text-muted-foreground">Unrealized PnL</p>
        {summary?.unrealizedPnlJpy !== null && summary?.unrealizedPnlJpy !== undefined ? (
          <Delta value={summary.unrealizedPnlJpy} className="text-sm font-semibold">
            {formatSignedJpy(summary.unrealizedPnlJpy)}
          </Delta>
        ) : <span className="text-sm text-muted-foreground">Unavailable</span>}
      </div>
    </Card>
  )
}
