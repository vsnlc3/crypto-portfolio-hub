import { Check, Plus, RefreshCw, TriangleAlert } from "lucide-react"
import { PageHeader } from "@/components/page-header"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { ServiceBadge } from "@/components/service-badge"
import { cn } from "@/lib/utils"
import {
  type ConnectionStatus,
  fmtRelative,
  fmtUsd,
  serviceList,
  serviceSpotValue,
} from "@/lib/mock-data"

const statusMeta: Record<
  ConnectionStatus,
  { label: string; className: string; icon: typeof Check }
> = {
  connected: { label: "Connected", className: "bg-positive/12 text-positive", icon: Check },
  syncing: { label: "Syncing", className: "bg-primary/12 text-primary", icon: RefreshCw },
  error: { label: "Error", className: "bg-negative/12 text-negative", icon: TriangleAlert },
  disconnected: { label: "Disconnected", className: "bg-muted text-muted-foreground", icon: TriangleAlert },
}

const kindLabel: Record<string, string> = { exchange: "Exchange", wallet: "Wallet", defi: "DeFi protocol" }

export default function ConnectionsPage() {
  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="Connections"
        subtitle="Manage the exchanges and wallets feeding your portfolio."
        actions={
          <Button size="sm" className="gap-2">
            <Plus className="size-4" />
            Add source
          </Button>
        }
      />

      <div className="space-y-3">
        {serviceList.map((s) => {
          const st = statusMeta[s.status]
          const StatusIcon = st.icon
          return (
            <Card key={s.id} className="gap-0 p-5">
              <div className="flex flex-wrap items-center gap-4">
                <ServiceBadge id={s.id} size={44} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <p className="font-semibold">{s.name}</p>
                    <span className="text-[11px] uppercase tracking-wide text-muted-foreground">
                      {kindLabel[s.kind]}
                    </span>
                  </div>
                  <p className="mt-0.5 font-mono text-xs text-muted-foreground">{s.accountRef}</p>
                </div>

                <span
                  className={cn(
                    "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium",
                    st.className,
                  )}
                >
                  <StatusIcon className={cn("size-3.5", s.status === "syncing" && "animate-spin")} />
                  {st.label}
                </span>
              </div>

              <div className="mt-4 flex flex-wrap items-end justify-between gap-4 border-t border-border pt-4">
                <div className="flex gap-8">
                  <div>
                    <p className="text-[11px] text-muted-foreground">Value tracked</p>
                    <p className="mt-0.5 font-mono text-lg font-semibold tabular">
                      {fmtUsd(serviceSpotValue(s.id), { compact: true })}
                    </p>
                  </div>
                  <div>
                    <p className="text-[11px] text-muted-foreground">Capabilities</p>
                    <div className="mt-1 flex gap-1.5">
                      {s.capabilities.map((c) => (
                        <span
                          key={c}
                          className="rounded-md border border-border bg-accent/50 px-2 py-0.5 text-[11px] font-medium uppercase"
                        >
                          {c}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <p className="text-[11px] text-muted-foreground">Last sync {fmtRelative(s.lastSync)}</p>
                  <Button variant="outline" size="sm" className="gap-2 bg-card">
                    <RefreshCw className="size-3.5" />
                    Sync
                  </Button>
                </div>
              </div>
            </Card>
          )
        })}
      </div>

      <p className="mt-6 text-center text-xs text-muted-foreground">
        Read-only demo. Connecting live accounts and Google sign-in are coming soon.
      </p>
    </div>
  )
}
