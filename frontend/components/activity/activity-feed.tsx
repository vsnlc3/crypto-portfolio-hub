'use client'

import { useInfiniteQuery } from '@tanstack/react-query'
import {
  Activity as ActivityIcon,
  AlertTriangle,
  ArrowDownLeft,
  ArrowLeftRight,
  ArrowUpRight,
  Banknote,
  Coins,
  RefreshCw,
  TrendingUp,
  type LucideIcon,
} from 'lucide-react'
import Link from 'next/link'
import { PageHeader } from '@/components/page-header'
import { ServiceBadge } from '@/components/service-badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import {
  activitiesQueryKey,
  getActivities,
  type ActivityDataStatus,
  type ActivityDirection,
  type ActivityEventType,
  type ActivityItem,
  type ActivityLeg,
  type ActivityProvider,
} from '@/lib/activities-api'
import { formatAmount, formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'

const jpyFormatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'JPY', maximumFractionDigits: 0 })
const signedJpyFormatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'JPY', maximumFractionDigits: 0, signDisplay: 'always' })

const eventMeta: Record<ActivityEventType, { icon: LucideIcon; label: string }> = {
  BUY: { icon: ArrowDownLeft, label: 'Buy' },
  SELL: { icon: ArrowUpRight, label: 'Sell' },
  DEPOSIT: { icon: Banknote, label: 'Deposit' },
  WITHDRAW: { icon: ArrowUpRight, label: 'Withdraw' },
  TRANSFER: { icon: ArrowLeftRight, label: 'Transfer' },
  SWAP: { icon: ArrowLeftRight, label: 'Swap' },
  PERP: { icon: TrendingUp, label: 'Perpetual fill' },
  FUNDING: { icon: Coins, label: 'Funding' },
  OTHER: { icon: ActivityIcon, label: 'Other activity' },
}

const providerBadge: Record<ActivityProvider, 'bitbank' | 'phantom' | 'hyperliquid'> = {
  BITBANK: 'bitbank',
  SOLANA: 'phantom',
  HYPERLIQUID: 'hyperliquid',
}

const providerName: Record<ActivityProvider, string> = {
  BITBANK: 'bitbank',
  SOLANA: 'Phantom',
  HYPERLIQUID: 'Hyperliquid',
}

function dayLabel(occurredAt: string) {
  return new Date(occurredAt).toLocaleDateString('en-US', {
    weekday: 'long', month: 'short', day: 'numeric', year: 'numeric',
  })
}

function groupByDay(activities: ActivityItem[]) {
  const groups = new Map<string, ActivityItem[]>()
  for (const activity of activities) {
    const label = dayLabel(activity.occurredAt)
    const existing = groups.get(label)
    if (existing) existing.push(activity)
    else groups.set(label, [activity])
  }
  return Array.from(groups.entries())
}

function statusLabel(status: ActivityDataStatus) {
  return status === 'COMPLETE' ? 'Fresh' : status[0] + status.slice(1).toLowerCase()
}

function StatusBadge({ status }: { status: ActivityDataStatus }) {
  const styles: Record<ActivityDataStatus, string> = {
    COMPLETE: 'bg-positive/12 text-positive',
    STALE: 'bg-amber-500/12 text-amber-400',
    PARTIAL: 'bg-amber-500/12 text-amber-400',
    UNAVAILABLE: 'bg-muted text-muted-foreground',
  }
  return <span className={cn('rounded-full px-2 py-0.5 text-[10px] font-medium', styles[status])}>{statusLabel(status)}</span>
}

function formatJpy(value: number | null, direction?: ActivityDirection) {
  if (value === null) return 'Unavailable'
  if (!direction) return jpyFormatter.format(value)
  return signedJpyFormatter.format(direction === 'IN' ? value : -value)
}

function formatCurrency(value: number, currency: string | null) {
  if (!currency) return `${formatAmount(value)} (currency unavailable)`
  if (/^[A-Z]{3}$/.test(currency)) {
    try {
      return new Intl.NumberFormat('en-US', {
        style: 'currency', currency, maximumFractionDigits: Math.abs(value) >= 1000 ? 2 : 6,
      }).format(value)
    } catch {
      // Display provider-defined currency codes as text below.
    }
  }
  return `${formatAmount(value)} ${currency}`
}

function formatFxSource(source: string | null) {
  if (source === 'EXCHANGERATE_API') return 'ExchangeRate API'
  if (source === 'COINGECKO_AND_EXCHANGERATE_API') return 'CoinGecko + ExchangeRate API'
  if (source === 'COINGECKO') return 'CoinGecko'
  return source
}

function ActivityLegRow({ leg }: { leg: ActivityLeg }) {
  const label = leg.direction === 'FEE' ? 'Fee' : leg.direction === 'IN' ? 'In' : 'Out'
  const asset = leg.symbol ?? leg.assetKey
  return (
    <div className="flex flex-wrap items-start gap-x-3 gap-y-2 border-t border-border/60 px-4 py-3 first:border-t-0 sm:items-center">
      <span className={cn(
        'min-w-11 rounded-md px-2 py-1 text-center text-[10px] font-semibold uppercase',
        leg.direction === 'IN' ? 'bg-positive/12 text-positive' : leg.direction === 'FEE' ? 'bg-amber-500/12 text-amber-400' : 'bg-negative/12 text-negative',
      )}>{label}</span>
      <div className="min-w-32 flex-1">
        <p className="font-medium">{asset}</p>
        <p className="text-[11px] text-muted-foreground">
          Quantity: {leg.quantity === null ? 'Unavailable' : formatAmount(leg.quantity, asset)}
          {leg.originalAmount !== null && (
            <> · Original amount: {formatCurrency(leg.originalAmount, leg.originalCurrency)}</>
          )}
        </p>
      </div>
      <div className="ml-auto text-right">
        <p className={cn('font-mono text-sm tabular', leg.direction === 'IN' ? 'text-positive' : 'text-muted-foreground')}>
          {formatJpy(leg.jpyValue, leg.direction)}
        </p>
        <p className="text-[10px] text-muted-foreground">
          {leg.jpyValue === null ? 'JPY valuation unavailable' : `JPY · ${leg.valuationBasis ?? 'basis unavailable'}`}
        </p>
        {leg.jpyValue !== null && leg.fxSource && (
          <p className="text-[10px] text-muted-foreground">
            {formatFxSource(leg.fxSource)}{leg.fxEvaluatedAt ? ` · ${formatDateTime(leg.fxEvaluatedAt)}` : ''}
          </p>
        )}
      </div>
    </div>
  )
}

function PerpetualFillDetails({ activity }: { activity: ActivityItem }) {
  const fill = activity.perpetualFill
  if (!fill) return null
  return (
    <div className="border-t border-border/60 px-4 py-3">
      <p className="text-xs font-medium">{fill.instrumentCode} · {fill.direction.replaceAll('_', ' ').toLowerCase()}</p>
      <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-muted-foreground">
        <span>Side: {fill.side}</span>
        <span>Quantity: {formatAmount(fill.quantity, fill.instrumentCode)}</span>
        <span>Price: {formatCurrency(fill.price, fill.priceCurrency)}</span>
        {fill.startPosition !== null && <span>Start position: {formatAmount(fill.startPosition, fill.instrumentCode)}</span>}
        {fill.closedPnl !== null && <span>Closed PnL: {formatCurrency(fill.closedPnl, fill.closedPnlCurrency)}</span>}
      </div>
    </div>
  )
}

function ActivityCard({ activity }: { activity: ActivityItem }) {
  const meta = eventMeta[activity.eventType]
  const Icon = meta.icon
  const title = activity.eventType === 'OTHER' && activity.originalEventType
    ? activity.originalEventType
    : meta.label
  return (
    <article className="flex gap-3 px-4 py-4 sm:gap-4 sm:px-5">
      <div className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full bg-accent text-muted-foreground">
        <Icon className="size-4" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="text-sm font-medium">{title}</h3>
          <StatusBadge status={activity.dataStatus} />
          {activity.status && <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] text-muted-foreground">{activity.status}</span>}
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px] text-muted-foreground">
          <span className="inline-flex items-center gap-1">
            <ServiceBadge id={providerBadge[activity.provider]} size={14} />
            {activity.connectionDisplayName ?? providerName[activity.provider]}
          </span>
          <span aria-hidden>·</span>
          <span>{meta.label}</span>
          <span aria-hidden>·</span>
          <time dateTime={activity.occurredAt}>{formatDateTime(activity.occurredAt)}</time>
          {activity.dataStatus === 'STALE' && activity.lastSuccessAt && (
            <span>· Last synced {formatDateTime(activity.lastSuccessAt)}</span>
          )}
        </div>
        {activity.originalEventType && activity.eventType !== 'OTHER' && activity.originalEventType !== activity.eventType && (
          <p className="mt-1 text-[10px] text-muted-foreground">Provider event: {activity.originalEventType}</p>
        )}
        {(activity.legs.length > 0 || activity.perpetualFill) && (
          <div className="mt-3 overflow-hidden rounded-lg border border-border/70">
            {activity.legs.map((leg) => <ActivityLegRow key={`${activity.id}:${leg.legIndex}`} leg={leg} />)}
            <PerpetualFillDetails activity={activity} />
          </div>
        )}
      </div>
    </article>
  )
}

function StateNotice({ status, connectionCount, syncedConnectionCount }: {
  status: ActivityDataStatus
  connectionCount: number
  syncedConnectionCount: number
}) {
  if (status === 'COMPLETE' || connectionCount === 0) return null
  const description = status === 'STALE'
    ? 'Showing the latest successful Activity history. A newer sync attempt needs attention.'
    : status === 'PARTIAL'
      ? `Activity history is incomplete. ${syncedConnectionCount} of ${connectionCount} connections have successful Activity sync history.`
      : 'Activity history is unavailable until a connection completes a successful Activity sync.'
  return (
    <div role={status === 'STALE' ? 'status' : 'alert'} className={cn(
      'mb-5 flex items-start gap-2 rounded-lg border px-4 py-3 text-sm',
      status === 'STALE' ? 'border-amber-500/30 bg-amber-500/5 text-amber-300' : 'border-destructive/30 bg-destructive/5 text-destructive',
    )}>
      <AlertTriangle className="mt-0.5 size-4 shrink-0" />
      <div className="min-w-0 flex-1">
        <p className="font-medium">{statusLabel(status)} Activity data</p>
        <p className="mt-0.5 text-xs opacity-90">{description}</p>
      </div>
      <Link className="text-xs font-medium underline underline-offset-4" href="/connections">Connections</Link>
    </div>
  )
}

function LoadingState() {
  return (
    <div className="mx-auto max-w-4xl" aria-label="Loading activity" aria-busy="true">
      <PageHeader title="Activity" subtitle="A unified transaction feed from your connected accounts." />
      <div className="space-y-5">
        {[0, 1].map((group) => (
          <div key={group}>
            <div className="mb-2 h-4 w-40 animate-pulse rounded bg-muted" />
            <Card className="gap-0 divide-y divide-border/70 p-0">
              {[0, 1, 2].map((row) => <div key={row} className="h-20 animate-pulse bg-muted/20" />)}
            </Card>
          </div>
        ))}
      </div>
    </div>
  )
}

function InitialError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader title="Activity" subtitle="A unified transaction feed from your connected accounts." />
      <Card className="items-center gap-3 p-10 text-center">
        <AlertTriangle className="size-6 text-destructive" />
        <p role="alert" className="text-sm text-muted-foreground">Activity couldn’t be loaded. Check your connections and try again.</p>
        <Button variant="outline" onClick={onRetry}>Try again</Button>
      </Card>
    </div>
  )
}

function EmptyState({ connectionCount, status }: { connectionCount: number; status: ActivityDataStatus }) {
  const noConnections = connectionCount === 0
  const knownEmpty = status === 'COMPLETE' || status === 'STALE'
  return (
    <Card className="items-center gap-3 px-6 py-12 text-center">
      <ActivityIcon className="size-7 text-muted-foreground" />
      <p className="font-medium">{noConnections ? 'No connections yet' : knownEmpty ? 'No Activity yet' : 'Activity is unavailable'}</p>
      <p className="max-w-md text-sm text-muted-foreground">
        {noConnections
          ? 'Connect an exchange or wallet to see your Activity here.'
          : knownEmpty ? 'A successful Activity sync found no events.' : 'A complete Activity history is not available yet.'}
      </p>
      {noConnections && <Button variant="outline" nativeButton={false} render={<Link href="/connections" />}>Connect a source</Button>}
    </Card>
  )
}

export function ActivityFeed() {
  const query = useInfiniteQuery({
    queryKey: activitiesQueryKey,
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) => getActivities({ cursor: pageParam }),
    getNextPageParam: (lastPage) => lastPage.hasMore ? lastPage.nextCursor ?? undefined : undefined,
  })

  if (query.isPending) return <LoadingState />
  if (query.isError && !query.data) return <InitialError onRetry={() => void query.refetch()} />

  const pages = query.data!.pages
  const summary = pages[0].summary
  const activities = pages.flatMap((page) => page.activities)
  const groups = groupByDay(activities)

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader title="Activity" subtitle="A unified transaction feed from your connected accounts." />
      {query.isRefetchError && (
        <div role="alert" className="mb-4 flex items-center gap-3 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertTriangle className="size-4 shrink-0" />
          <p className="flex-1">Activity couldn’t be refreshed. Showing the loaded history.</p>
          <Button variant="outline" size="sm" onClick={() => void query.refetch()}>Try again</Button>
        </div>
      )}
      <StateNotice {...summary} />

      {groups.length === 0 ? (
        <EmptyState connectionCount={summary.connectionCount} status={summary.status} />
      ) : (
        <div className="space-y-6">
          {groups.map(([day, items]) => (
            <section key={day} aria-label={day}>
              <h2 className="mb-2 px-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">{day}</h2>
              <Card className="gap-0 divide-y divide-border/70 p-0">
                {items.map((activity) => <ActivityCard key={activity.id} activity={activity} />)}
              </Card>
            </section>
          ))}
        </div>
      )}

      {query.isFetchNextPageError && (
        <div role="alert" className="mt-4 flex items-center gap-3 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <AlertTriangle className="size-4 shrink-0" />
          <p className="flex-1">More Activity couldn’t be loaded. The history above is still available.</p>
          <Button variant="outline" size="sm" onClick={() => void query.fetchNextPage()}>Try again</Button>
        </div>
      )}
      {query.hasNextPage && !query.isFetchNextPageError && (
        <div className="mt-5 flex justify-center">
          <Button variant="outline" disabled={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>
            {query.isFetchingNextPage ? <><RefreshCw className="animate-spin" /> Loading more</> : 'Load more Activity'}
          </Button>
        </div>
      )}
    </div>
  )
}
