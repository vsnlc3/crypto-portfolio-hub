"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { Activity, LayoutDashboard, Plug, RefreshCw, Search, Wallet } from "lucide-react"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"

const nav = [
  { href: "/", label: "Dashboard", icon: LayoutDashboard },
  { href: "/assets", label: "Assets", icon: Wallet },
  { href: "/activity", label: "Activity", icon: Activity },
  { href: "/connections", label: "Connections", icon: Plug },
]

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()

  return (
    <div className="flex min-h-svh">
      {/* Sidebar */}
      <aside className="sticky top-0 hidden h-svh w-60 shrink-0 flex-col border-r border-border bg-sidebar px-3 py-5 md:flex">
        <div className="flex items-center gap-2.5 px-3 pb-6">
          <div className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <span className="font-mono text-sm font-bold">M</span>
          </div>
          <div className="leading-tight">
            <p className="text-sm font-semibold text-sidebar-foreground">Meridian</p>
            <p className="text-[11px] text-muted-foreground">Portfolio</p>
          </div>
        </div>

        <nav className="flex flex-1 flex-col gap-1">
          {nav.map((item) => {
            const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href)
            const Icon = item.icon
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                  active
                    ? "bg-sidebar-accent text-sidebar-foreground"
                    : "text-muted-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-foreground",
                )}
              >
                <Icon className={cn("size-4", active && "text-primary")} />
                {item.label}
              </Link>
            )
          })}
        </nav>

        <div className="rounded-xl border border-border bg-card/60 p-3">
          <div className="flex items-center gap-2">
            <span className="relative flex size-2">
              <span className="absolute inline-flex size-full animate-ping rounded-full bg-primary opacity-60" />
              <span className="relative inline-flex size-2 rounded-full bg-primary" />
            </span>
            <p className="text-xs font-medium text-sidebar-foreground">3 sources live</p>
          </div>
          <p className="mt-1 text-[11px] leading-relaxed text-muted-foreground">
            Read-only demo. Google sign-in coming soon.
          </p>
        </div>
      </aside>

      {/* Main */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-border bg-background/80 px-5 backdrop-blur md:px-8">
          {/* mobile nav */}
          <nav className="flex items-center gap-1 md:hidden">
            {nav.map((item) => {
              const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href)
              const Icon = item.icon
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={cn(
                    "flex size-9 items-center justify-center rounded-lg",
                    active ? "bg-accent text-primary" : "text-muted-foreground",
                  )}
                  aria-label={item.label}
                >
                  <Icon className="size-4" />
                </Link>
              )
            })}
          </nav>

          <div className="relative hidden w-full max-w-xs items-center lg:flex">
            <Search className="pointer-events-none absolute left-3 size-4 text-muted-foreground" />
            <input
              type="text"
              placeholder="Search assets, activity…"
              className="h-9 w-full rounded-lg border border-border bg-card pl-9 pr-3 text-sm outline-none placeholder:text-muted-foreground focus:ring-2 focus:ring-ring/40"
            />
          </div>

          <div className="ml-auto flex items-center gap-2">
            <Button variant="outline" size="sm" className="gap-2 bg-card">
              <RefreshCw className="size-3.5" />
              <span className="hidden sm:inline">Sync</span>
            </Button>
            <div className="flex items-center gap-2 rounded-full border border-border bg-card py-1 pl-1 pr-3">
              <div className="flex size-7 items-center justify-center rounded-full bg-accent text-xs font-semibold">
                KT
              </div>
              <span className="hidden text-sm sm:inline">Kenta</span>
            </div>
          </div>
        </header>

        <main className="flex-1 px-5 py-6 md:px-8 md:py-8">{children}</main>
      </div>
    </div>
  )
}
