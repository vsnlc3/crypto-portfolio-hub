import { Gauge } from "lucide-react"
import { Card } from "@/components/ui/card"
import { Delta } from "@/components/delta"
import {
  exposureRatio,
  fmtSignedUsd,
  fmtUsd,
  marketExposure,
  perpNotional,
  spotDirectionalValue,
  stableValue,
  totalPnl,
  totalSpotValue,
} from "@/lib/mock-data"

function Bar() {
  const total = spotDirectionalValue + perpNotional + stableValue
  const seg = [
    { label: "Spot", value: spotDirectionalValue, color: "var(--chart-2)" },
    { label: "Perp notional", value: perpNotional, color: "var(--primary)" },
    { label: "Stablecoins", value: stableValue, color: "var(--chart-5)" },
  ]
  return (
    <div className="space-y-3">
      <div className="flex h-2.5 w-full overflow-hidden rounded-full">
        {seg.map((s) => (
          <div
            key={s.label}
            style={{ width: `${(s.value / total) * 100}%`, backgroundColor: s.color }}
          />
        ))}
      </div>
      <ul className="space-y-1.5">
        {seg.map((s) => (
          <li key={s.label} className="flex items-center justify-between text-xs">
            <span className="flex items-center gap-2 text-muted-foreground">
              <span className="size-2 rounded-full" style={{ backgroundColor: s.color }} />
              {s.label}
            </span>
            <span className="font-mono tabular text-foreground">{fmtUsd(s.value, { compact: true })}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

export function ExposureCard() {
  return (
    <Card className="flex flex-col gap-5 p-6">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">Holdings vs. market exposure</p>
        <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-accent/50 px-2 py-0.5 text-xs font-medium">
          <Gauge className="size-3.5 text-primary" />
          {exposureRatio.toFixed(2)}× leverage
        </span>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="rounded-xl border border-border bg-background/40 p-4">
          <p className="text-xs text-muted-foreground">Holdings value</p>
          <p className="mt-1 font-mono text-xl font-semibold tabular">{fmtUsd(totalSpotValue, { compact: true })}</p>
          <p className="mt-1 text-[11px] text-muted-foreground">What you own</p>
        </div>
        <div className="rounded-xl border border-primary/25 bg-primary/5 p-4">
          <p className="text-xs text-muted-foreground">Market exposure</p>
          <p className="mt-1 font-mono text-xl font-semibold tabular text-primary">
            {fmtUsd(marketExposure, { compact: true })}
          </p>
          <p className="mt-1 text-[11px] text-muted-foreground">Directional + leverage</p>
        </div>
      </div>

      <Bar />

      <div className="flex items-center justify-between border-t border-border pt-4">
        <p className="text-sm text-muted-foreground">Unrealized PnL</p>
        <Delta value={totalPnl} className="text-sm font-semibold">
          {fmtSignedUsd(totalPnl)}
        </Delta>
      </div>
    </Card>
  )
}
