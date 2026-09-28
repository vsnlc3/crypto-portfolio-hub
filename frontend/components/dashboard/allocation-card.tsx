'use client'

import { useQuery } from '@tanstack/react-query'
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import { DataStatusBadge, EmptyCard, InlineError, LoadingCard } from '@/components/dashboard/data-state'
import { Card } from '@/components/ui/card'
import { getAssets, assetsQueryKey } from '@/lib/assets-api'
import { formatJpy } from '@/lib/format'

const colors = ['var(--chart-2)', 'var(--primary)', 'var(--chart-5)', 'var(--chart-3)', 'var(--chart-4)']

export function AllocationCard() {
  const assetsQuery = useQuery({ queryKey: assetsQueryKey, queryFn: getAssets })
  if (assetsQuery.isPending && !assetsQuery.data) return <LoadingCard label="asset allocation" />
  if (assetsQuery.isError && !assetsQuery.data) {
    return <Card className="p-6"><InlineError message="Asset allocation couldn’t be loaded." onRetry={() => void assetsQuery.refetch()} /></Card>
  }

  const response = assetsQuery.data!
  if (response.summary.connectionCount === 0) {
    return <EmptyCard title="Allocation unavailable" detail="Connect a service to see your asset allocation." />
  }
  const knownAssets = response.assets.filter((asset) => asset.valueJpy !== null && asset.valueJpy > 0)
  const knownTotal = knownAssets.reduce((sum, asset) => sum + asset.valueJpy!, 0)
  const completeTotal = response.summary.spotHoldingsValueJpy
  const isPartial = completeTotal === null
  if (knownAssets.length === 0) {
    return (
      <Card className="gap-3 p-6">
        <div className="flex items-center justify-between"><p className="text-sm text-muted-foreground">Allocation by currency</p><DataStatusBadge status={response.summary.status} /></div>
        <p role="status" className="py-8 text-center text-sm text-muted-foreground">
          {response.summary.status === 'COMPLETE' || response.summary.status === 'STALE'
            ? 'No positive-value holdings are available.'
            : 'Allocation values are unavailable.'}
        </p>
      </Card>
    )
  }

  const data = knownAssets.map((asset, index) => ({
    name: asset.symbol,
    value: asset.valueJpy!,
    color: colors[index % colors.length],
    percent: knownTotal > 0 ? asset.valueJpy! / knownTotal * 100 : null,
  }))

  return (
    <Card className="gap-0 p-6">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">Allocation by currency</p>
        <DataStatusBadge status={response.summary.status} />
      </div>
      {assetsQuery.isRefetchError && (
        <div className="mt-3"><InlineError message="Asset allocation couldn’t be refreshed. Showing the last loaded data." onRetry={() => void assetsQuery.refetch()} /></div>
      )}
      {isPartial && (
        <p role="status" className="mt-3 text-xs text-amber-400">Known valued assets only; one or more balances are unavailable.</p>
      )}

      <div className="mt-4 flex flex-wrap items-center gap-6">
        <div className="relative size-40 shrink-0">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie data={data} dataKey="value" nameKey="name" innerRadius={52} outerRadius={78} paddingAngle={2} stroke="none">
                {data.map((item) => <Cell key={item.name} fill={item.color} />)}
              </Pie>
              <Tooltip formatter={(value) => formatJpy(Number(value))} />
            </PieChart>
          </ResponsiveContainer>
          <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
            <span className="text-[11px] text-muted-foreground">{isPartial ? 'Known value' : 'Spot holdings'}</span>
            <span className="font-mono text-base font-semibold tabular">
              {formatJpy(completeTotal ?? knownTotal, true)}
            </span>
          </div>
        </div>

        <ul className="min-w-52 flex-1 space-y-2.5">
          {data.map((item) => (
            <li key={item.name} className="flex items-center gap-2 text-sm">
              <span className="size-2.5 shrink-0 rounded-full" style={{ backgroundColor: item.color }} />
              <span className="min-w-0 truncate font-medium">{item.name}</span>
              <span className="ml-auto font-mono tabular text-muted-foreground">
                {item.percent === null ? 'Unavailable' : `${item.percent.toFixed(1)}%`}
              </span>
              <span className="w-24 text-right font-mono tabular">{formatJpy(item.value, true)}</span>
            </li>
          ))}
        </ul>
      </div>
    </Card>
  )
}
