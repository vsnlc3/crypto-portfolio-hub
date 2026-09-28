'use client'

import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, RefreshCw } from 'lucide-react'
import Link from 'next/link'
import { ServiceBadge } from '@/components/service-badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { decimalCompare, type DecimalString } from '@/lib/decimal'
import { formatAmount, formatDateTime, formatJpy, formatMoney } from '@/lib/format'
import { getPositions, positionsQueryKey, type Position, type PositionDataStatus } from '@/lib/positions-api'
import { cn } from '@/lib/utils'

function sourceName(source: string | null) {
  if (source === 'EXCHANGERATE_API') return 'ExchangeRate API'
  if (source === 'COINGECKO_AND_EXCHANGERATE_API') return 'CoinGecko + ExchangeRate API'
  if (source === 'IDENTITY') return 'Identity rate'
  return source ?? 'FX unavailable'
}

function FxHint({ source, evaluatedAt, status }: Position['priceFx']) {
  return (
    <span className="mt-0.5 block text-[10px] text-muted-foreground">
      {sourceName(source)}{evaluatedAt ? ` · ${formatDateTime(evaluatedAt)}` : ''}{status === 'STALE' ? ' · stale' : ''}
    </span>
  )
}

function StatusBadge({ status }: { status: PositionDataStatus }) {
  const styles: Record<PositionDataStatus, string> = {
    COMPLETE: 'bg-positive/12 text-positive',
    STALE: 'bg-amber-500/12 text-amber-400',
    PARTIAL: 'bg-amber-500/12 text-amber-400',
    UNAVAILABLE: 'bg-muted text-muted-foreground',
  }
  const label = status === 'COMPLETE' ? 'Fresh' : status[0] + status.slice(1).toLowerCase()
  return <span className={cn('rounded-full px-2 py-0.5 text-[10px] font-medium', styles[status])}>{label}</span>
}

function SummaryValue({ label, value, pnl = false }: { label: string; value: DecimalString | null; pnl?: boolean }) {
  return (
    <div className="text-right">
      <p className="text-[11px] text-muted-foreground">{label}</p>
      <p className={cn('font-mono font-semibold tabular', pnl && value !== null && (decimalCompare(value, '0') >= 0 ? 'text-positive' : 'text-negative'))}>
        {formatJpy(value)}
      </p>
    </div>
  )
}

function StateNotice({ status, connectionCount, syncedConnectionCount }: {
  status: PositionDataStatus
  connectionCount: number
  syncedConnectionCount: number
}) {
  if (status === 'COMPLETE' || connectionCount === 0) return null
  const description = status === 'STALE'
    ? 'Showing the last successful position data and valuation inputs.'
    : status === 'PARTIAL'
      ? `Some position or JPY valuation inputs are unavailable. ${syncedConnectionCount} of ${connectionCount} position connections have a successful sync.`
      : 'Position totals are unavailable until a successful position sync and required valuation inputs are available.'
  return (
    <div role={status === 'STALE' ? 'status' : 'alert'} className={cn(
      'mb-4 flex items-start gap-2 rounded-lg border px-4 py-3 text-sm',
      status === 'STALE' ? 'border-amber-500/30 bg-amber-500/5 text-amber-300' : 'border-destructive/30 bg-destructive/5 text-destructive',
    )}>
      <AlertTriangle className="mt-0.5 size-4 shrink-0" />
      <div className="min-w-0 flex-1">
        <p className="font-medium">{status[0] + status.slice(1).toLowerCase()} position data</p>
        <p className="mt-0.5 text-xs opacity-90">{description}</p>
      </div>
      <Link className="text-xs font-medium underline underline-offset-4" href="/connections">Connections</Link>
    </div>
  )
}

function PositionRow({ position }: { position: Position }) {
  const isLong = position.side === 'LONG'
  const rawPnl = formatMoney(position.unrealizedPnl, position.pnlCurrency, true)
  return (
    <tr className="border-t border-border/70 hover:bg-accent/30">
      <td className="px-6 py-3.5">
        <div className="flex items-center gap-2">
          <ServiceBadge provider={position.provider} size={22} />
          <div>
            <span className="font-medium">{position.instrumentCode}</span>
            <span className="ml-2"><StatusBadge status={position.status} /></span>
            {position.connectionDisplayName && <p className="mt-0.5 text-[10px] text-muted-foreground">{position.connectionDisplayName}</p>}
          </div>
        </div>
      </td>
      <td className="px-3 py-3.5">
        <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs font-medium', isLong ? 'bg-positive/12 text-positive' : 'bg-negative/12 text-negative')}>
          {isLong ? 'Long' : 'Short'}
          {position.leverage !== null && <span className="font-mono tabular opacity-80">{formatAmount(position.leverage)}×</span>}
        </span>
        <span className="mt-1 block text-[10px] text-muted-foreground">{formatAmount(position.quantity)} contracts</span>
      </td>
      <td className="px-3 py-3.5 text-right">
        <span className="font-mono tabular">{formatJpy(position.positionValueJpy)}</span>
        <FxHint {...position.priceFx} />
      </td>
      <td className="px-3 py-3.5 text-right">
        <span className="font-mono tabular">{formatMoney(position.marginAmount, position.marginCurrency)}</span>
        <span className="mt-0.5 block font-mono text-[11px] tabular text-muted-foreground">{formatJpy(position.marginJpy)}</span>
        <FxHint {...position.marginFx} />
      </td>
      <td className="px-3 py-3.5 text-right">
        <span className={cn('font-mono tabular', position.unrealizedPnl !== null && (decimalCompare(position.unrealizedPnl, '0') >= 0 ? 'text-positive' : 'text-negative'))}>{rawPnl}</span>
        <span className={cn('mt-0.5 block font-mono text-[11px] tabular', position.unrealizedPnlJpy !== null && (decimalCompare(position.unrealizedPnlJpy, '0') >= 0 ? 'text-positive' : 'text-negative'))}>{formatJpy(position.unrealizedPnlJpy)}</span>
        <FxHint {...position.pnlFx} />
      </td>
      <td className="px-3 py-3.5 text-right font-mono tabular">{formatMoney(position.entryPrice, position.priceCurrency)}</td>
      <td className="px-3 py-3.5 text-right font-mono tabular">{formatMoney(position.markPrice, position.priceCurrency)}</td>
      <td className="px-6 py-3.5 text-right font-mono tabular text-negative/90">{formatMoney(position.liquidationPrice, position.priceCurrency)}</td>
    </tr>
  )
}

function LoadingState() {
  return (
    <Card className="gap-0 overflow-hidden py-0" aria-label="Loading positions" aria-busy="true">
      <div className="border-b border-border px-6 py-4"><div className="h-5 w-48 animate-pulse rounded bg-muted" /></div>
      <div className="h-56 animate-pulse bg-muted/30" />
    </Card>
  )
}

function EmptyState({ connectionCount, status }: { connectionCount: number; status: PositionDataStatus }) {
  const noConnections = connectionCount === 0
  const knownEmpty = status === 'COMPLETE' || status === 'STALE'
  return (
    <div className="px-6 py-12 text-center">
      <p className="font-medium">{noConnections ? 'No position connections' : knownEmpty ? 'No open perpetual positions' : 'Positions unavailable'}</p>
      <p className="mt-1 text-sm text-muted-foreground">
        {noConnections
          ? 'Connect a supported account to see perpetual positions.'
          : knownEmpty ? 'A successful position sync found no open positions.' : 'A complete position list is not available yet.'}
      </p>
      {noConnections && <Button className="mt-4" variant="outline" nativeButton={false} render={<Link href="/connections" />}>View connections</Button>}
    </div>
  )
}

function LoadError({ onRetry }: { onRetry: () => void }) {
  return (
    <Card className="items-center gap-3 p-10 text-center">
      <AlertTriangle className="size-6 text-destructive" />
      <p role="alert" className="text-sm text-muted-foreground">Positions couldn’t be loaded. Check your connections and try again.</p>
      <Button variant="outline" onClick={onRetry}>Try again</Button>
    </Card>
  )
}

export function PositionsTable() {
  const query = useQuery({ queryKey: positionsQueryKey, queryFn: getPositions })
  if (query.isPending) return <LoadingState />
  if (query.isError && !query.data) return <LoadError onRetry={() => void query.refetch()} />

  const { summary, positions } = query.data!
  return (
    <Card className="gap-0 overflow-hidden py-0">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-6 py-4">
        <div className="flex items-center gap-2.5">
          <ServiceBadge id="hyperliquid" size={28} />
          <div>
            <p className="text-sm font-semibold">Perpetual positions</p>
            <p className="text-[11px] text-muted-foreground">Across {summary.connectionCount} position connections</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-6">
          <SummaryValue label="Position value · JPY" value={summary.positionValueJpy} />
          <SummaryValue label="Margin · JPY" value={summary.marginJpy} />
          <SummaryValue label="Unrealized PnL · JPY" value={summary.unrealizedPnlJpy} pnl />
          <StatusBadge status={summary.status} />
        </div>
      </div>

      {query.isRefetchError && (
        <div role="alert" className="flex items-center gap-3 border-b border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertTriangle className="size-4 shrink-0" />
          <p className="flex-1">Positions couldn’t be refreshed. Showing the last successful data.</p>
          <Button variant="outline" size="sm" onClick={() => void query.refetch()}>Try again</Button>
        </div>
      )}
      <div className="px-4 pt-4"><StateNotice {...summary} /></div>
      {positions.length === 0 ? (
        <EmptyState connectionCount={summary.connectionCount} status={summary.status} />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1080px] border-collapse text-sm">
            <thead><tr className="text-left text-[11px] uppercase tracking-wide text-muted-foreground">
              <th className="px-6 py-2.5 font-medium">Symbol</th>
              <th className="px-3 py-2.5 font-medium">Side</th>
              <th className="px-3 py-2.5 text-right font-medium">Position value · JPY</th>
              <th className="px-3 py-2.5 text-right font-medium">Margin</th>
              <th className="px-3 py-2.5 text-right font-medium">Unrealized PnL</th>
              <th className="px-3 py-2.5 text-right font-medium">Entry</th>
              <th className="px-3 py-2.5 text-right font-medium">Mark</th>
              <th className="px-6 py-2.5 text-right font-medium">Liquidation</th>
            </tr></thead>
            <tbody>{positions.map((position) => <PositionRow key={`${position.connectionId}:${position.positionKey}`} position={position} />)}</tbody>
          </table>
        </div>
      )}
      {query.isFetching && !query.isPending && <p className="flex items-center justify-end gap-1.5 px-4 py-2 text-[11px] text-muted-foreground" role="status"><RefreshCw className="size-3 animate-spin" /> Refreshing positions</p>}
    </Card>
  )
}
