import {
  ArrowDownLeft,
  ArrowLeftRight,
  ArrowUpRight,
  Banknote,
  Coins,
  TrendingUp,
} from "lucide-react"
import { PageHeader } from "@/components/page-header"
import { Card } from "@/components/ui/card"
import { ServiceBadge } from "@/components/service-badge"
import { cn } from "@/lib/utils"
import {
  activity,
  type Activity,
  fmtAmount,
  fmtDateTime,
  fmtSignedUsd,
  fmtUsd,
  services,
} from "@/lib/mock-data"

const typeMeta: Record<Activity["type"], { icon: typeof Coins; label: string }> = {
  buy: { icon: ArrowDownLeft, label: "Buy" },
  sell: { icon: ArrowUpRight, label: "Sell" },
  deposit: { icon: Banknote, label: "Deposit" },
  withdraw: { icon: ArrowUpRight, label: "Withdraw" },
  transfer: { icon: ArrowLeftRight, label: "Transfer" },
  perp: { icon: TrendingUp, label: "Perp" },
  funding: { icon: Coins, label: "Funding" },
}

function groupByDay(items: Activity[]) {
  const groups = new Map<string, Activity[]>()
  for (const a of items) {
    const day = new Date(a.timestamp).toLocaleDateString("en-US", {
      weekday: "long",
      month: "short",
      day: "numeric",
    })
    if (!groups.has(day)) groups.set(day, [])
    groups.get(day)!.push(a)
  }
  return Array.from(groups.entries())
}

export default function ActivityPage() {
  const groups = groupByDay(activity)

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="Activity"
        subtitle="A unified transaction feed from bitbank, Phantom, and Hyperliquid."
      />

      <div className="space-y-6">
        {groups.map(([day, items]) => (
          <div key={day}>
            <p className="mb-2 px-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
              {day}
            </p>
            <Card className="gap-0 divide-y divide-border/70 p-0">
              {items.map((a) => {
                const { icon: Icon, label } = typeMeta[a.type]
                const negative = a.valueUsd < 0 || a.type === "sell" || a.type === "withdraw"
                return (
                  <div key={a.id} className="flex items-center gap-4 px-5 py-3.5">
                    <div className="flex size-9 items-center justify-center rounded-full bg-accent text-muted-foreground">
                      <Icon className="size-4" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{a.title}</p>
                      <div className="mt-0.5 flex items-center gap-2 text-[11px] text-muted-foreground">
                        <span className="inline-flex items-center gap-1">
                          <ServiceBadge id={a.service} size={14} />
                          {services[a.service].name}
                        </span>
                        <span aria-hidden>·</span>
                        <span>{label}</span>
                        <span aria-hidden>·</span>
                        <span>{fmtDateTime(a.timestamp)}</span>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className="font-mono text-sm tabular">{fmtAmount(a.amount, String(a.symbol))}</p>
                      <p
                        className={cn(
                          "font-mono text-[11px] tabular",
                          negative ? "text-negative" : "text-muted-foreground",
                        )}
                      >
                        {a.valueUsd < 0 ? fmtSignedUsd(a.valueUsd) : fmtUsd(a.valueUsd, { compact: true })}
                      </p>
                    </div>
                    {a.status === "pending" && (
                      <span className="rounded-full bg-amber-500/15 px-2 py-0.5 text-[10px] font-medium text-amber-400">
                        Pending
                      </span>
                    )}
                  </div>
                )
              })}
            </Card>
          </div>
        ))}
      </div>
    </div>
  )
}
