import { cn } from "@/lib/utils"

const tokenColors: Record<string, string> = {
  BTC: "oklch(0.78 0.14 60)",
  ETH: "oklch(0.7 0.09 260)",
  SOL: "oklch(0.74 0.16 300)",
  XRP: "oklch(0.72 0.03 255)",
  HYPE: "oklch(0.8 0.15 164)",
  USDC: "oklch(0.68 0.1 250)",
}

export function TokenBadge({
  symbol,
  size = 36,
  className,
}: {
    symbol: string
  size?: number
  className?: string
}) {
  const color = tokenColors[symbol] ?? "var(--muted-foreground)"
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
        color,
        backgroundColor: `color-mix(in oklch, ${color} 16%, transparent)`,
        border: `1px solid color-mix(in oklch, ${color} 32%, transparent)`,
      }}
      aria-hidden
    >
      {symbol.slice(0, symbol === "USDC" ? 4 : 3)}
    </span>
  )
}
