"use client"

import { Cell, Pie, PieChart, ResponsiveContainer } from "recharts"
import { Card } from "@/components/ui/card"
import { assetAggregates, fmtUsd, totalSpotValue } from "@/lib/mock-data"

export function AllocationCard() {
  const aggregates = assetAggregates()
  const data = aggregates.map((a) => ({
    name: a.symbol,
    value: a.value,
    color: a.meta.color,
    pct: (a.value / totalSpotValue) * 100,
  }))

  return (
    <Card className="gap-0 p-6">
      <p className="text-sm text-muted-foreground">Allocation by currency</p>

      <div className="mt-4 flex items-center gap-6">
        <div className="relative size-40 shrink-0">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={data}
                dataKey="value"
                nameKey="name"
                innerRadius={52}
                outerRadius={78}
                paddingAngle={2}
                stroke="none"
              >
                {data.map((d) => (
                  <Cell key={d.name} fill={d.color} />
                ))}
              </Pie>
            </PieChart>
          </ResponsiveContainer>
          <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
            <span className="text-[11px] text-muted-foreground">Assets</span>
            <span className="font-mono text-lg font-semibold tabular">
              {fmtUsd(totalSpotValue, { compact: true })}
            </span>
          </div>
        </div>

        <ul className="flex-1 space-y-2.5">
          {data.map((d) => (
            <li key={d.name} className="flex items-center gap-2 text-sm">
              <span className="size-2.5 rounded-full" style={{ backgroundColor: d.color }} />
              <span className="font-medium">{d.name}</span>
              <span className="ml-auto font-mono tabular text-muted-foreground">
                {d.pct.toFixed(1)}%
              </span>
              <span className="w-20 text-right font-mono tabular">{fmtUsd(d.value, { compact: true })}</span>
            </li>
          ))}
        </ul>
      </div>
    </Card>
  )
}
