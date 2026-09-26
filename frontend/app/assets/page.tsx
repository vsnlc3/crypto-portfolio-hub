import { PageHeader } from "@/components/page-header"
import { CurrencyList } from "@/components/dashboard/currency-list"
import { AllocationCard } from "@/components/dashboard/allocation-card"
import { Card } from "@/components/ui/card"
import {
  fmtUsd,
  netWorth,
  stableValue,
  spotDirectionalValue,
  totalSpotValue,
} from "@/lib/mock-data"

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card className="gap-0 p-5">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 font-mono text-2xl font-semibold tabular">{value}</p>
      {hint && <p className="mt-1 text-[11px] text-muted-foreground">{hint}</p>}
    </Card>
  )
}

export default function AssetsPage() {
  return (
    <div className="mx-auto max-w-[1400px]">
      <PageHeader
        title="Assets"
        subtitle="Every currency you hold, aggregated across all connected sources."
      />

      <div className="grid gap-4 sm:grid-cols-3">
        <Stat label="Spot holdings" value={fmtUsd(totalSpotValue, { compact: true })} hint="Across 3 sources" />
        <Stat
          label="Directional"
          value={fmtUsd(spotDirectionalValue, { compact: true })}
          hint={`${((spotDirectionalValue / netWorth) * 100).toFixed(0)}% of net worth`}
        />
        <Stat
          label="Stablecoins"
          value={fmtUsd(stableValue, { compact: true })}
          hint="Dry powder"
        />
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-12">
        <div className="lg:col-span-5">
          <AllocationCard />
        </div>
        <div className="lg:col-span-7">
          <CurrencyList />
        </div>
      </div>
    </div>
  )
}
