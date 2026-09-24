"use client"

import { useState } from "react"
import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts"
import { Card } from "@/components/ui/card"
import { Delta } from "@/components/delta"
import {
  change24hPct,
  change24hUsd,
  fmtPct,
  fmtSignedUsd,
  fmtUsd,
  netWorth,
  netWorthHistory,
} from "@/lib/mock-data"

const ranges = ["7D", "30D", "90D", "1Y"] as const

function ChartTooltip({ active, payload }: { active?: boolean; payload?: { value: number; payload: { date: string } }[] }) {
  if (!active || !payload?.length) return null
  const p = payload[0]
  return (
    <div className="rounded-lg border border-border bg-popover px-3 py-2 shadow-lg">
      <p className="text-[11px] text-muted-foreground">{p.payload.date}</p>
      <p className="font-mono text-sm font-semibold tabular">{fmtUsd(p.value)}</p>
    </div>
  )
}

export function NetWorthCard() {
  const [range, setRange] = useState<(typeof ranges)[number]>("30D")

  return (
    <Card className="flex flex-col gap-5 p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-sm text-muted-foreground">Total net worth</p>
          <p className="mt-1 font-mono text-4xl font-semibold tracking-tight tabular">
            {fmtUsd(netWorth)}
          </p>
          <div className="mt-2 flex items-center gap-2 text-sm">
            <Delta value={change24hUsd}>{fmtSignedUsd(change24hUsd)}</Delta>
            <Delta value={change24hPct} showIcon={false}>
              ({fmtPct(change24hPct)})
            </Delta>
            <span className="text-muted-foreground">24h</span>
          </div>
        </div>

        <div className="flex rounded-lg border border-border bg-card p-0.5">
          {ranges.map((r) => (
            <button
              key={r}
              onClick={() => setRange(r)}
              className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
                r === range
                  ? "bg-accent text-foreground"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              {r}
            </button>
          ))}
        </div>
      </div>

      <div className="h-56 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={netWorthHistory} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
            <defs>
              <linearGradient id="netWorthFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--primary)" stopOpacity={0.35} />
                <stop offset="100%" stopColor="var(--primary)" stopOpacity={0} />
              </linearGradient>
            </defs>
            <XAxis
              dataKey="date"
              tickLine={false}
              axisLine={false}
              tick={{ fill: "var(--muted-foreground)", fontSize: 11 }}
              minTickGap={28}
            />
            <YAxis
              hide
              domain={["dataMin - 1500", "dataMax + 1500"]}
            />
            <Tooltip content={<ChartTooltip />} cursor={{ stroke: "var(--border)", strokeWidth: 1 }} />
            <Area
              type="monotone"
              dataKey="value"
              stroke="var(--primary)"
              strokeWidth={2}
              fill="url(#netWorthFill)"
              dot={false}
              activeDot={{ r: 4, fill: "var(--primary)", stroke: "var(--background)", strokeWidth: 2 }}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </Card>
  )
}
