import { services, type ServiceId } from "@/lib/mock-data"
import { cn } from "@/lib/utils"

export function ServiceBadge({
  id,
  size = 32,
  className,
}: {
  id: ServiceId
  size?: number
  className?: string
}) {
  const s = services[id]
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
