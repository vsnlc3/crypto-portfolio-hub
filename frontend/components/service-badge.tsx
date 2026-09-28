import { cn } from "@/lib/utils"

const providers: Record<string, { mark: string; color: string }> = {
  BITBANK: { mark: "bb", color: "oklch(0.72 0.14 250)" },
  SOLANA: { mark: "Ph", color: "oklch(0.72 0.16 300)" },
  HYPERLIQUID: { mark: "HL", color: "oklch(0.8 0.15 164)" },
  bitbank: { mark: "bb", color: "oklch(0.72 0.14 250)" },
  phantom: { mark: "Ph", color: "oklch(0.72 0.16 300)" },
  hyperliquid: { mark: "HL", color: "oklch(0.8 0.15 164)" },
}

export function ServiceBadge({
  provider,
  id,
  size = 32,
  className,
}: {
  provider?: string
  id?: string
  size?: number
  className?: string
}) {
  const service = provider ?? id ?? ""
  const s = providers[service] ?? { mark: service.slice(0, 2).toUpperCase(), color: "var(--muted-foreground)" }
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-[30%] font-semibold",
        className,
      )}
      style={{
        width: size,
        height: size,
        fontSize: size * 0.4,
        color: s.color,
        backgroundColor: `color-mix(in oklch, ${s.color} 16%, transparent)`,
        border: `1px solid color-mix(in oklch, ${s.color} 32%, transparent)`,
      }}
      aria-hidden
    >
      {s.mark}
    </span>
  )
}
