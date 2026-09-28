'use client'

import { useState } from 'react'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { DataStatusBadge, InlineError, LoadingCard } from '@/components/dashboard/data-state'
import { Delta } from '@/components/delta'
import { Card } from '@/components/ui/card'
import { formatDateTime, formatJpy, formatPercent, formatSignedJpy } from '@/lib/format'
import {
  getPortfolioHistory,
  getPortfolioSummary,
  portfolioHistoryQueryKey,
  portfolioSummaryQueryKey,
  type PortfolioHistoryPeriod,
} from '@/lib/portfolio-api'
import { useQuery } from '@tanstack/react-query'

const ranges: PortfolioHistoryPeriod[] = ['7D', '30D', '90D', '1Y']

function HistoryTooltip({ active, payload }: {
  active?: boolean
  payload?: { value: number; payload: { snapshotAt: string; status: string } }[]
}) {
  if (!active || !payload?.length) return null
  const point = payload[0].payload
  return (
    <div className="rounded-lg border border-border bg-popover px-3 py-2 shadow-lg">
      <p className="text-[11px] text-muted-foreground">{formatDateTime(point.snapshotAt)}</p>
      <p className="font-mono text-sm font-semibold tabular">{formatJpy(payload[0].value)}</p>
      <p className="mt-0.5 text-[10px] text-muted-foreground">{point.status === 'STALE' ? 'Stale snapshot' : 'Complete snapshot'}</p>
    </div>
  )
}

function HistorySkeleton() {
  return <div className="h-56 animate-pulse rounded-lg bg-muted/30" aria-label="Loading history" aria-busy="true" />
}

export function NetWorthCard() {
  const [range, setRange] = useState<PortfolioHistoryPeriod>('30D')
  const summaryQuery = useQuery({ queryKey: portfolioSummaryQueryKey, queryFn: getPortfolioSummary })
  const historyQuery = useQuery({
    queryKey: portfolioHistoryQueryKey(range),
    queryFn: () => getPortfolioHistory(range),
  })

  if (summaryQuery.isPending && !summaryQuery.data && historyQuery.isPending) {
    return <LoadingCard label="portfolio summary" />
  }

  const summary = summaryQuery.data?.summary
  const points = historyQuery.data?.points ?? []
  const chartPoints = points.map((point) => ({ ...point, timestamp: Date.parse(point.snapshotAt) }))
  const singlePointDomain = chartPoints.length === 1
    ? [chartPoints[0].timestamp - 43_200_000, chartPoints[0].timestamp + 43_200_000]
    : ['dataMin', 'dataMax'] as [string, string]
  const change = summary?.change24h
  const stalePoints = points.filter((point) => point.status === 'STALE').length

  return (
    <Card className="flex flex-col gap-5 p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <p className="text-sm text-muted-foreground">Total net worth</p>
            {summary && <DataStatusBadge status={summary.status} />}
          </div>
          <p className="mt-1 font-mono text-4xl font-semibold tracking-tight tabular">
            {summary ? formatJpy(summary.netWorthJpy) : 'Unavailable'}
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-sm">
            {change?.amountJpy !== null && change?.amountJpy !== undefined ? (
              <>
                <Delta value={change.amountJpy}>{formatSignedJpy(change.amountJpy)}</Delta>
                {change.percentage !== null
                  ? <Delta value={change.percentage} showIcon={false}>({formatPercent(change.percentage)})</Delta>
                  : <span className="text-xs text-muted-foreground">percentage unavailable</span>}
                <span className="text-muted-foreground">24h · {change.status === 'STALE' ? 'stale' : 'snapshot comparison'}</span>
              </>
            ) : (
              <span className="text-xs text-muted-foreground">24h change unavailable</span>
            )}
          </div>
          {summaryQuery.isError && !summaryQuery.data && (
            <div className="mt-3"><InlineError message="Portfolio summary couldn’t be loaded." onRetry={() => void summaryQuery.refetch()} /></div>
          )}
          {summaryQuery.isRefetchError && (
            <p role="alert" className="mt-2 text-xs text-destructive">Refresh failed. Showing the last loaded summary.</p>
          )}
        </div>

        <div role="group" aria-label="Portfolio history period" className="flex rounded-lg border border-border bg-card p-0.5">
          {ranges.map((period) => (
            <button
              key={period}
              type="button"
              aria-pressed={period === range}
              onClick={() => setRange(period)}
              className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
                period === range ? 'bg-accent text-foreground' : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              {period}
            </button>
          ))}
        </div>
      </div>

      {historyQuery.isRefetchError && (
        <InlineError message="History refresh failed. Showing the last loaded points." onRetry={() => void historyQuery.refetch()} />
      )}
      {historyQuery.isPending && !historyQuery.data ? (
        <HistorySkeleton />
      ) : historyQuery.isError && !historyQuery.data ? (
        <InlineError message="Portfolio history couldn’t be loaded." onRetry={() => void historyQuery.refetch()} />
      ) : points.length === 0 ? (
        <div role="status" className="grid h-56 place-items-center rounded-lg border border-dashed border-border px-4 text-center">
          <div>
            <p className="text-sm font-medium">No history in this period</p>
            <p className="mt-1 text-xs text-muted-foreground">No saved Portfolio Snapshots are available for {range}.</p>
          </div>
        </div>
      ) : (
        <div>
          {stalePoints > 0 && (
            <p role="status" className="mb-2 text-xs text-amber-400">
              {stalePoints} of {points.length} snapshots use stale data. Gaps have no saved snapshot.
            </p>
          )}
          {stalePoints === 0 && (
            <p className="mb-2 text-xs text-muted-foreground">{points.length} saved snapshots. Gaps have no saved snapshot.</p>
          )}
          {historyQuery.data?.status === 'EMPTY' ? null : (
            <div className="h-56 w-full" role="img" aria-label={`Net worth history for ${range}`}>
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartPoints} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                  <defs>
                    <linearGradient id="netWorthFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="var(--primary)" stopOpacity={0.35} />
                      <stop offset="100%" stopColor="var(--primary)" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid vertical={false} stroke="var(--border)" strokeDasharray="3 3" />
                  <XAxis
                    type="number"
                    dataKey="timestamp"
                    domain={singlePointDomain}
                    scale="time"
                    tickFormatter={(value: number) => new Date(value).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                    tickLine={false}
                    axisLine={false}
                    tick={{ fill: 'var(--muted-foreground)', fontSize: 11 }}
                    minTickGap={28}
                  />
                  <YAxis hide domain={['dataMin - 1500', 'dataMax + 1500']} />
                  <Tooltip content={<HistoryTooltip />} cursor={{ stroke: 'var(--border)', strokeWidth: 1 }} />
                  <Area
                    type="linear"
                    dataKey="netWorthJpy"
                    stroke="var(--primary)"
                    strokeWidth={2}
                    fill="url(#netWorthFill)"
                    dot={{ r: 2.5, fill: 'var(--primary)' }}
                    activeDot={{ r: 4, fill: 'var(--primary)', stroke: 'var(--background)', strokeWidth: 2 }}
                    connectNulls={false}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>
      )}
      {summary?.dataAsOfAt && (
        <p className="text-right text-[10px] text-muted-foreground">Portfolio data as of {formatDateTime(summary.dataAsOfAt)}</p>
      )}
    </Card>
  )
}
