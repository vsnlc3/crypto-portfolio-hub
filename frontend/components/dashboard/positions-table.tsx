import { Card } from "@/components/ui/card"
import { ServiceBadge } from "@/components/service-badge"
import { cn } from "@/lib/utils"
import {
  fmtPct,
  fmtSignedUsd,
  fmtUsd,
  positionMargin,
  positionNotional,
  positionPnl,
  positionPnlPct,
  positions,
  services,
} from "@/lib/mock-data"

function Price({ value }: { value: number }) {
  return (
    <span className="font-mono tabular">
      {value >= 1000 ? fmtUsd(value, { decimals: 0 }) : fmtUsd(value)}
    </span>
  )
}

export function PositionsTable() {
  const totalNotional = positions.reduce((s, p) => s + positionNotional(p), 0)
  const totalPnl = positions.reduce((s, p) => s + positionPnl(p), 0)

  return (
    <Card className="gap-0 overflow-hidden py-0">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-6 py-4">
        <div className="flex items-center gap-2.5">
          <ServiceBadge id="hyperliquid" size={28} />
          <div>
            <p className="text-sm font-semibold">Perpetual positions</p>
            <p className="text-[11px] text-muted-foreground">{services.hyperliquid.name}</p>
          </div>
        </div>
        <div className="flex items-center gap-6 text-sm">
          <div className="text-right">
            <p className="text-[11px] text-muted-foreground">Position value</p>
            <p className="font-mono font-semibold tabular">{fmtUsd(totalNotional, { compact: true })}</p>
          </div>
          <div className="text-right">
            <p className="text-[11px] text-muted-foreground">Total PnL</p>
            <p
              className={cn(
                "font-mono font-semibold tabular",
                totalPnl >= 0 ? "text-positive" : "text-negative",
              )}
            >
              {fmtSignedUsd(totalPnl)}
            </p>
          </div>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[880px] border-collapse text-sm">
          <thead>
            <tr className="text-left text-[11px] uppercase tracking-wide text-muted-foreground">
              <th className="px-6 py-2.5 font-medium">Symbol</th>
              <th className="px-3 py-2.5 font-medium">Side</th>
              <th className="px-3 py-2.5 text-right font-medium">Position value</th>
              <th className="px-3 py-2.5 text-right font-medium">Margin</th>
              <th className="px-3 py-2.5 text-right font-medium">Unrealized PnL</th>
              <th className="px-3 py-2.5 text-right font-medium">Entry</th>
              <th className="px-3 py-2.5 text-right font-medium">Mark</th>
              <th className="px-6 py-2.5 text-right font-medium">Liquidation</th>
            </tr>
          </thead>
          <tbody>
            {positions.map((p) => {
              const pnl = positionPnl(p)
              const long = p.side === "long"
              return (
                <tr key={p.symbol} className="border-t border-border/70 hover:bg-accent/30">
                  <td className="px-6 py-3.5">
                    <span className="font-medium">{p.symbol}</span>
                  </td>
                  <td className="px-3 py-3.5">
                    <span
                      className={cn(
                        "inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs font-medium",
                        long
                          ? "bg-positive/12 text-positive"
                          : "bg-negative/12 text-negative",
                      )}
                    >
                      {long ? "Long" : "Short"}
                      <span className="font-mono tabular opacity-80">{p.leverage}×</span>
                    </span>
                  </td>
                  <td className="px-3 py-3.5 text-right">
                    <Price value={positionNotional(p)} />
                  </td>
                  <td className="px-3 py-3.5 text-right font-mono tabular text-muted-foreground">
                    {fmtUsd(positionMargin(p), { compact: true })}
                  </td>
                  <td className="px-3 py-3.5 text-right">
                    <span
                      className={cn(
                        "font-mono tabular",
                        pnl >= 0 ? "text-positive" : "text-negative",
                      )}
                    >
                      {fmtSignedUsd(pnl)}
                      <span className="ml-1 text-[11px] opacity-70">{fmtPct(positionPnlPct(p))}</span>
                    </span>
                  </td>
                  <td className="px-3 py-3.5 text-right">
                    <Price value={p.entryPrice} />
                  </td>
                  <td className="px-3 py-3.5 text-right">
                    <Price value={p.markPrice} />
                  </td>
                  <td className="px-6 py-3.5 text-right font-mono tabular text-negative/90">
                    {p.liquidationPrice >= 1000
                      ? fmtUsd(p.liquidationPrice, { decimals: 0 })
                      : fmtUsd(p.liquidationPrice)}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </Card>
  )
}
