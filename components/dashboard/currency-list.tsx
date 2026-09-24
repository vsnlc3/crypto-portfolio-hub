import { Card } from "@/components/ui/card"
import { TokenBadge } from "@/components/token-badge"
import { Delta } from "@/components/delta"
import { ServiceBadge } from "@/components/service-badge"
import { assetAggregates, fmtAmount, fmtPct, fmtUsd, totalSpotValue } from "@/lib/mock-data"

export function CurrencyList() {
  const aggregates = assetAggregates()

  return (
    <Card className="gap-0 p-6">
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-muted-foreground">Holdings by currency</p>
        <p className="text-[11px] text-muted-foreground">{aggregates.length} assets</p>
      </div>

      <ul className="divide-y divide-border/70">
        {aggregates.map((a) => {
          const share = (a.value / totalSpotValue) * 100
          return (
            <li key={a.symbol} className="flex items-center gap-4 py-3 first:pt-0 last:pb-0">
              <TokenBadge symbol={a.symbol} size={38} />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{a.symbol}</span>
                  <span className="truncate text-xs text-muted-foreground">{a.meta.name}</span>
                </div>
                <div className="mt-0.5 flex items-center gap-1.5">
                  {a.breakdown.map((b) => (
                    <ServiceBadge key={b.service} id={b.service} size={16} />
                  ))}
                  <span className="text-[11px] text-muted-foreground">
                    {a.breakdown.length > 1 ? `${a.breakdown.length} sources` : "1 source"}
                  </span>
                </div>
              </div>
              <div className="text-right">
                <p className="font-mono text-sm tabular">{fmtAmount(a.amount, a.symbol)}</p>
                <p className="text-[11px] text-muted-foreground">
                  @ {fmtUsd(a.meta.priceUsd)}
                </p>
              </div>
              <div className="w-28 text-right">
                <p className="font-mono text-sm font-semibold tabular">{fmtUsd(a.value, { compact: true })}</p>
                <div className="mt-0.5 flex items-center justify-end gap-2">
                  <Delta value={a.meta.change24hPct} showIcon={false} className="text-[11px]">
                    {fmtPct(a.meta.change24hPct)}
                  </Delta>
                  <span className="text-[11px] text-muted-foreground">{share.toFixed(1)}%</span>
                </div>
              </div>
            </li>
          )
        })}
      </ul>
    </Card>
  )
}
