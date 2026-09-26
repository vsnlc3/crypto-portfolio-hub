import { assets, type AssetSymbol } from "@/lib/mock-data"
import { cn } from "@/lib/utils"

export function TokenBadge({
  symbol,
  size = 36,
  className,
}: {
  symbol: AssetSymbol
  size?: number
  className?: string
}) {
  const meta = assets[symbol]
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-full font-mono font-semibold tabular",
        className,
      )}
      style={{
        width: size,
        height: size,
        fontSize: size * 0.34,
        color: meta.color,
        backgroundColor: `color-mix(in oklch, ${meta.color} 16%, transparent)`,
        border: `1px solid color-mix(in oklch, ${meta.color} 32%, transparent)`,
      }}
      aria-hidden
    >
      {symbol.slice(0, symbol === "USDC" ? 4 : 3)}
    </span>
  )
}
