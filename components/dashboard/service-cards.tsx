import { Card } from "@/components/ui/card"
import { ServiceBadge } from "@/components/service-badge"
import { Delta } from "@/components/delta"
import {
  fmtPct,
  fmtUsd,
  netWorth,
  serviceChange24h,
  serviceList,
  servicePnl,
  serviceTotalValue,
} from "@/lib/mock-data"

const kindLabel: Record<string, string> = {
  exchange: "Exchange",
  wallet: "Wallet",
  defi: "DeFi",
}

export function ServiceCards() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {serviceList.map((s) => {
        const total = serviceTotalValue(s.id)
        const pnl = servicePnl(s.id)
        const share = (total / netWorth) * 100
        return (
          <Card key={s.id} className="gap-0 p-5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <ServiceBadge id={s.id} size={38} />
                <div>
                  <p className="text-sm font-semibold">{s.name}</p>
                  <p className="text-[11px] uppercase tracking-wide text-muted-foreground">
                    {kindLabel[s.kind]}
                  </p>
                </div>
              </div>
              <Delta value={serviceChange24h[s.id]} className="text-xs">
                {fmtPct(serviceChange24h[s.id])}
              </Delta>
            </div>

            <p className="mt-4 font-mono text-2xl font-semibold tabular">{fmtUsd(total, { compact: true })}</p>

            <div className="mt-3 space-y-1.5">
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-accent">
                <div className="h-full rounded-full bg-primary" style={{ width: `${share}%` }} />
              </div>
              <div className="flex items-center justify-between text-[11px] text-muted-foreground">
                <span>{share.toFixed(1)}% of portfolio</span>
                {s.capabilities.includes("perp") && (
                  <span className="font-mono tabular">PnL {fmtUsd(pnl, { compact: true })}</span>
                )}
              </div>
            </div>
          </Card>
        )
      })}
    </div>
  )
}
