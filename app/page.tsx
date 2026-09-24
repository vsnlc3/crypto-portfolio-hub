import { PageHeader } from "@/components/page-header"
import { NetWorthCard } from "@/components/dashboard/net-worth-card"
import { ExposureCard } from "@/components/dashboard/exposure-card"
import { ServiceCards } from "@/components/dashboard/service-cards"
import { AllocationCard } from "@/components/dashboard/allocation-card"
import { CurrencyList } from "@/components/dashboard/currency-list"
import { PositionsTable } from "@/components/dashboard/positions-table"
import { Button } from "@/components/ui/button"
import { RefreshCw } from "lucide-react"

export default function DashboardPage() {
  return (
    <div className="mx-auto max-w-[1400px]">
      <PageHeader
        title="Dashboard"
        subtitle="Your assets across bitbank, Phantom, and Hyperliquid in one view."
        actions={
          <Button variant="outline" size="sm" className="gap-2 bg-card">
            <RefreshCw className="size-3.5" />
            Synced 2m ago
          </Button>
        }
      />

      <div className="grid gap-4 lg:grid-cols-12">
        <div className="lg:col-span-8">
          <NetWorthCard />
        </div>
        <div className="lg:col-span-4">
          <ExposureCard />
        </div>
      </div>

      <div className="mt-4">
        <ServiceCards />
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-12">
        <div className="lg:col-span-5">
          <AllocationCard />
        </div>
        <div className="lg:col-span-7">
          <CurrencyList />
        </div>
      </div>

      <div className="mt-4">
        <PositionsTable />
      </div>
    </div>
  )
}
