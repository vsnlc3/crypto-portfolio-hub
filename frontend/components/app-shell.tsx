"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { Activity, LayoutDashboard, Plug, RefreshCw, Search, Wallet } from "lucide-react"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import type { AuthenticatedUser } from "@/lib/auth-api"

const nav = [
  { href: "/", label: "Dashboard", icon: LayoutDashboard },
  { href: "/assets", label: "Assets", icon: Wallet },
  { href: "/activity", label: "Activity", icon: Activity },
  { href: "/connections", label: "Connections", icon: Plug },
]

type AppShellProps = {
  children: React.ReactNode
  user: AuthenticatedUser
  onLogout: () => void
  isLoggingOut: boolean
  logoutError: boolean
}

export function AppShell({ children, user, onLogout, isLoggingOut, logoutError }: AppShellProps) {
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
          <p className="text-xs font-medium text-sidebar-foreground">Read-only sources</p>
          <p className="mt-1 text-[11px] leading-relaxed text-muted-foreground">
            Your read-only portfolio view.
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
            {user.isDemo ? (
              <span role="status" className="rounded-full border border-amber-500/30 bg-amber-500/10 px-2.5 py-1 text-xs font-medium text-amber-300">
                Demo · sample data
              </span>
            ) : (
              <Button variant="outline" size="sm" className="gap-2 bg-card">
                <RefreshCw className="size-3.5" />
                <span className="hidden sm:inline">Sync</span>
              </Button>
            )}
            <div className="flex min-w-0 items-center gap-2 rounded-full border border-border bg-card py-1 pl-1 pr-3">
              <div className="flex size-7 shrink-0 items-center justify-center rounded-full bg-accent text-xs font-semibold" aria-hidden="true">
                {getInitials(user.displayName || user.email)}
              </div>
              <span className="hidden max-w-40 truncate text-sm sm:inline" title={user.displayName || user.email}>
                {user.displayName || user.email}
              </span>
            </div>
            <Button variant="outline" size="sm" onClick={onLogout} disabled={isLoggingOut}>
              {isLoggingOut ? (user.isDemo ? "Leaving demo…" : "Signing out…") : (user.isDemo ? "Exit demo" : "Sign out")}
            </Button>
          </div>
        </header>

        {logoutError && (
          <p role="alert" className="border-b border-destructive/20 bg-destructive/5 px-5 py-2 text-right text-xs text-destructive md:px-8">
            Sign out failed. Please try again.
          </p>
        )}

        <main className="flex-1 px-5 py-6 md:px-8 md:py-8">{children}</main>
      </div>
    </div>
  )
}

function getInitials(value: string) {
  const parts = value.trim().split(/[\s@._-]+/).filter(Boolean)
  return parts.slice(0, 2).map((part) => part[0]?.toUpperCase()).join("") || "U"
}
