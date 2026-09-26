import { TrendingDown, TrendingUp } from "lucide-react"
import { cn } from "@/lib/utils"

export function Delta({
  value,
  children,
  showIcon = true,
  className,
}: {
  /** sign of this number decides color/arrow */
  value: number
  children: React.ReactNode
  showIcon?: boolean
  className?: string
}) {
  const positive = value >= 0
  const Icon = positive ? TrendingUp : TrendingDown
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 font-mono tabular",
        positive ? "text-positive" : "text-negative",
        className,
      )}
    >
      {showIcon && <Icon className="size-3.5" />}
      {children}
    </span>
  )
}
