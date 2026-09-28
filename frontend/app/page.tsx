'use client'

import { useQuery } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import { CoinGeckoAttribution } from '@/components/coingecko-attribution'
import { PageHeader } from '@/components/page-header'
import { NetWorthCard } from '@/components/dashboard/net-worth-card'
import { ExposureCard } from '@/components/dashboard/exposure-card'
import { ServiceCards } from '@/components/dashboard/service-cards'
import { AllocationCard } from '@/components/dashboard/allocation-card'
import { CurrencyList } from '@/components/dashboard/currency-list'
import { PositionsTable } from '@/components/dashboard/positions-table'
import { DataStatusBadge } from '@/components/dashboard/data-state'
import { formatDateTime, formatRelative } from '@/lib/format'
import { getPortfolioSummary, portfolioSummaryQueryKey } from '@/lib/portfolio-api'

function LastSync({ timestamp, loading, error }: { timestamp: string | null; loading: boolean; error: boolean }) {
  if (loading) {
    return <div role="status" className="flex items-center gap-2 rounded-lg border border-border bg-card px-3 py-2 text-xs text-muted-foreground"><RefreshCw className="size-3.5 animate-spin" />Loading sync status</div>
  }
  if (error) return <span role="status" className="text-xs text-muted-foreground">Sync status unavailable</span>
  return (
    <div title={timestamp ? `Last successful sync: ${formatDateTime(timestamp)}` : undefined} className="flex items-center gap-2 rounded-lg border border-border bg-card px-3 py-2 text-xs text-muted-foreground">
      <RefreshCw className="size-3.5" />
      {timestamp ? `Updated ${formatRelative(timestamp)}` : 'Not synced yet'}
    </div>
  )
}

export default function DashboardPage() {
  const summaryQuery = useQuery({ queryKey: portfolioSummaryQueryKey, queryFn: getPortfolioSummary })
  const lastSuccessfulSyncAt = summaryQuery.data?.summary.lastSuccessfulSyncAt ?? null

  return (
    <div className="mx-auto max-w-[1400px]">
      <PageHeader
        title="Dashboard"
        subtitle="Your assets across bitbank, Solana wallets, and Hyperliquid in one view."
        actions={<LastSync timestamp={lastSuccessfulSyncAt} loading={summaryQuery.isPending} error={summaryQuery.isError && !summaryQuery.data} />}
      />

      <div className="grid gap-4 lg:grid-cols-12">
        <div className="lg:col-span-8"><NetWorthCard /></div>
        <div className="lg:col-span-4"><ExposureCard /></div>
      </div>

      <div className="mt-4"><ServiceCards /></div>

      <div className="mt-4 grid gap-4 lg:grid-cols-12">
        <div className="lg:col-span-5"><AllocationCard /></div>
        <div className="lg:col-span-7"><CurrencyList /></div>
      </div>

      <div className="mt-4"><PositionsTable /></div>

      {summaryQuery.data && summaryQuery.data.summary.status !== 'COMPLETE' && summaryQuery.data.summary.status !== 'STALE' && (
        <p role="status" className="mt-4 text-center text-xs text-muted-foreground">
          Portfolio totals are incomplete. Check each section or review your Connections.
          <span className="ml-1"><DataStatusBadge status={summaryQuery.data.summary.status} /></span>
        </p>
      )}

      <div className="mt-6 flex justify-end"><CoinGeckoAttribution /></div>
    </div>
  )
}
